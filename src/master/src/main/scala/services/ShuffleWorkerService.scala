package services

import repositories.GrpcShuffleRepository

import scala.collection.concurrent.TrieMap
import scala.concurrent.*
import scala.concurrent.ExecutionContext.Implicits.global

class ShuffleWorkerService(
                            repository: GrpcShuffleRepository,
                            registrationService: RegistrationService,
                            workerCount: Int
                          ) {
  private val doneCheckLists = TrieMap.empty[Int, Array[Boolean]]
  private val roundPromises = TrieMap.empty[Int, Promise[Unit]]

  @volatile private var deadWorkerId = -1
  @volatile private var currentGlobalRound: Int = 0

  private var deferredCatchUp: Option[(Int, Int, Int)] = None
  private var catchUpPromise: Option[Promise[Unit]] = None
  private val catchUpDoneSet = scala.collection.mutable.Set[(Int, Int)]()

  private def broadcastNextRound(roundId: Int): Unit = {
    val pairs = getPairsForRound(roundId)
    val registration = registrationService.getRegisteredWorkers

    pairs.foreach { case (idA, idB) =>
      val (ipA, portA) = registration(idA)
      val (ipB, portB) = registration(idB)

      repository.sendNextRound(idA, roundId, ipB, portB)
      repository.sendNextRound(idB, roundId, ipA, portA)
    }
  }

  private def onWorkerRoundDone(workerId: Int, roundId: Int): Unit = {
    // [Case A] 재부팅 감지: 죽었던 놈이 0라운드(Sort) 끝내고 옴 -> Catch-Up 시작
    if (roundId == 0 && workerId == deadWorkerId) {
      println(s"Reboot detected. Worker $workerId starting Catch-Up.")
      startCatchUpProcess(workerId)
      return
    }

    // [Case B] Catch-Up 진행 중: 현재 글로벌 라운드보다 낮은 라운드 보고
    if (roundId < currentGlobalRound) {
      handleCatchUpDone(workerId, roundId)
      return
    }
    this.synchronized {

    // [Case C] 정상 진행 (또는 Catch-Up 합류)
    val doneListOpt = doneCheckLists.get(roundId)
    val promiseOpt = roundPromises.get(roundId)

      (doneListOpt, promiseOpt) match {
        case (Some(doneList), Some(promise)) =>
          val workerIndex = workerId - 1

          if (workerIndex >= 0 && workerIndex < workerCount) {
            doneList(workerIndex) = true
            // 캐치업 트리거 체크
            checkAndTriggerDeferredCatchUp(workerId)
          }

          // 2. 모두 완료되었는지 확인
          if (!doneList.contains(false)) {
            // 3. 완료되었다면 즉시 맵에서 제거하고 Promise 완료
            doneCheckLists.remove(roundId)
            roundPromises.remove(roundId)
            promise.trySuccess(())
          }

        case _ =>
          println(s"Ignored duplicate or late Done(worker=$workerId round=$roundId)")
      }
    }
  }

  private def handleCatchUpDone(workerId: Int, roundId: Int): Unit = {
    this.synchronized {
      catchUpPromise.foreach { p =>
        catchUpDoneSet.add((roundId, workerId))

        // 해당 라운드의 페어를 찾아서
        val pairs = getPairsForRound(roundId)
        val pair = pairs.find(pair => pair._1 == deadWorkerId || pair._2 == deadWorkerId).get

        // 둘 다 완료했으면 다음 단계로 진행 (Promise Success)
        if (catchUpDoneSet.contains((roundId, pair._1)) && catchUpDoneSet.contains((roundId, pair._2))) {
          catchUpDoneSet.clear()
          catchUpPromise = None
          p.trySuccess(())
        }
      }
    }
  }

  // 보류된 캐치업이 있고, 지금 일을 마친 워커가 그 파트너라면 실행
  private def checkAndTriggerDeferredCatchUp(finishedWorkerId: Int): Unit = {
    deferredCatchUp match {
      case Some((rebootId, round, partnerId)) if partnerId == finishedWorkerId =>
        println(s"[CatchUp] Partner $partnerId is now IDLE. Resuming Catch-Up Round $round for $rebootId")
        this.synchronized {
          deferredCatchUp = None
        } // 대기열 해제
        broadcastToPairOnly(rebootId, round) // 이제 전송!
      case _ =>
      // 매칭되는 보류 작업 없음
    }
  }

  private def onWorkerDead(workerId: Int): Unit = {
    println(s"[Master] Worker $workerId DEAD reported.")
    deadWorkerId = workerId
  }

  private def executeRound(roundId: Int): Future[Unit] = {
    val doneList = Array.fill(workerCount)(false)
    val promise = Promise[Unit]()

    this.synchronized {
      currentGlobalRound = roundId
      doneCheckLists(roundId) = doneList
      roundPromises(roundId) = promise
    }

    if(roundId != 0) {
      broadcastNextRound(roundId)
    }

    promise.future
  }

  private def executeRounds(start: Int, end: Int): Future[Unit] = {
    def loop(roundId: Int): Future[Unit] = {
      if (roundId >= end) Future.successful(())
      else {
        executeRound(roundId).flatMap { _ =>
          loop(roundId + 1)
        }
      }
    }

    loop(start)
  }

  private def startCatchUpProcess(recoveringWorkerId: Int): Unit = {
    catchUpLoop(recoveringWorkerId, 1)
  }

  private def catchUpLoop(rebootedWorkerId: Int, roundToRun: Int): Unit = {
    if (roundToRun == currentGlobalRound) {
      println(s"Catch-Up Finished. Joining Global Round $roundToRun")
      broadcastToPairOnly(rebootedWorkerId, roundToRun)
      return
    }

    // [Running] 과거 라운드 수행
    val p = Promise[Unit]()
    this.synchronized {
      catchUpPromise = Some(p)
    }

    // [수정] 무조건 전송하는 게 아니라, 파트너 상태를 보고 결정
    val pairs = getPairsForRound(roundToRun)
    val pair = pairs.find(p => p._1 == rebootedWorkerId || p._2 == rebootedWorkerId).get
    val partnerId = if (pair._1 == rebootedWorkerId) pair._2 else pair._1

    this.synchronized {
      val isPartnerIdle = isWorkerIdleInCurrentRound(partnerId)

      if (isPartnerIdle) {
        println(s"[CatchUp] Partner $partnerId is IDLE. Executing Round $roundToRun immediately.")
        broadcastToPairOnly(rebootedWorkerId, roundToRun)
      } else {
        println(s"[CatchUp] Partner $partnerId is BUSY. Deferring Round $roundToRun.")
        this.synchronized {
          deferredCatchUp = Some((rebootedWorkerId, roundToRun, partnerId))
        }
      }
    }

    p.future.foreach { _ =>
      catchUpLoop(rebootedWorkerId, roundToRun + 1)
    }
  }

  def start(): Future[Unit] = {
    repository.onWorkerRoundDone = onWorkerRoundDone
    repository.onWorkerDead = onWorkerDead

    executeRounds(0, workerCount)
  }

  private def isWorkerIdleInCurrentRound(workerId: Int): Boolean = {
    doneCheckLists.get(currentGlobalRound) match {
      case Some(doneList) =>
        val idx = workerId - 1
        if (idx >= 0 && idx < doneList.length) doneList(idx)
        else false
      case None =>
        true
    }
  }

  private def broadcastToPairOnly(rebootedWorkerId: Int, roundId: Int): Unit = {
    val pairs = getPairsForRound(roundId)
    val pair = pairs.find(p => p._1 == rebootedWorkerId || p._2 == rebootedWorkerId)
    val registry = registrationService.getRegisteredWorkers

    pair.foreach { case (idA, idB) =>
      val (ipA, portA) = registry(idA)
      val (ipB, portB) = registry(idB)

      repository.sendNextRound(idA, roundId, ipB, portB)
      repository.sendNextRound(idB, roundId, ipA, portA)
    }
  }

  private def getPairsForRound(roundId: Int): List[(Int, Int)] = {
    if (roundId < 1 || roundId > roundRobinPairs.length) List.empty
    else roundRobinPairs(roundId - 1)
  }

  private val roundRobinPairs: List[List[(Int, Int)]] = {

    val initialPlayers = (1 to workerCount).toList

    def rotate(players: List[Int]): List[Int] = {
      players.head :: players.last :: players.tail.init
    }

    val playerStates: LazyList[List[Int]] =
      LazyList.iterate(initialPlayers)(rotate)

    playerStates.take(workerCount - 1).map { players =>
      val len = players.length

      (0 until (len / 2)).map { i =>
        (players(i), players(len - 1 - i))
      }.toList
    }.toList
  }
}