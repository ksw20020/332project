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

    // [Case C] 정상 진행 (또는 Catch-Up 합류)
    val doneListOpt = doneCheckLists.get(roundId)
    val promiseOpt = roundPromises.get(roundId)

    (doneListOpt, promiseOpt) match {
      case (Some(doneList), Some(promise)) =>
        val workerIndex = workerId - 1
        var allDone = false
        this.synchronized {
          if (workerIndex >= 0 && workerIndex < workerCount) {
            doneList(workerIndex) = true
          }
          if (!doneList.contains(false)) allDone = true
        }

        if (allDone) {
          doneCheckLists.remove(roundId)
          roundPromises.remove(roundId)
          promise.trySuccess(())
        }

      case _ =>
        println(s"Received Done(worker=$workerId round=$roundId)")
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

  private def onWorkerDead(workerId: Int): Unit = {
    println(s"[Master] Worker $workerId DEAD reported.")
    deadWorkerId = workerId
  }

  private def executeRound(roundId: Int): Future[Unit] = {
    val doneList = Array.fill(workerCount)(false)
    val promise = Promise[Unit]()

    this.synchronized {
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
        currentGlobalRound = roundId

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

    // 해당 라운드의 파트너와 죽었던 워커에게만 명령 전송
    broadcastToPairOnly(rebootedWorkerId, roundToRun)

    // 이 단계가 끝나면(p 완료) 다음 단계로
    p.future.foreach { _ =>
      catchUpLoop(rebootedWorkerId, roundToRun + 1)
    }
  }

  def start(): Future[Unit] = {
    repository.onWorkerRoundDone = onWorkerRoundDone
    repository.onWorkerDead = onWorkerDead

    executeRounds(0, workerCount)
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