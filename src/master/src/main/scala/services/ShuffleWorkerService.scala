package services

import repositories.GrpcShuffleRepository

import scala.collection.concurrent.TrieMap
import scala.concurrent.*
import scala.concurrent.ExecutionContext.Implicits.global

class ShuffleWorkerService(repository: GrpcShuffleRepository) {
  private val doneCheckLists = TrieMap.empty[Int, Array[Boolean]]
  private val roundPromises = TrieMap.empty[Int, Promise[Unit]]
  private var deadWorkerId = -1
  private var currentPair = (-1, -1)

  private def broadcastNextRound(roundId: Int): Unit = {
    currentPair = (-1, -1)
    repository.broadcastNextRound(roundId)
  }

  private def broadcastNextRoundForDeadWorker(roundId: Int): Unit = {
    val pair = roundRobinPairs(roundId).find(p => p._1 == deadWorkerId || p._2 == deadWorkerId)
    if (pair.isEmpty) {}
    else {
      currentPair = pair.get

      pair.foreach { (worker1, worker2) =>
        repository.sendNextRound(worker1, roundId)
        repository.sendNextRound(worker2, roundId)
      }
    }
  }

  private def onWorkerRoundDone(workerId: Int, roundId: Int): Unit = {
    val doneList = doneCheckLists.get(roundId)
    val promise = roundPromises.get(roundId)

    (doneList, promise) match {
      case (Some(doneList), Some(promise)) =>
        var allDone = false
        this.synchronized {
          if(currentPair._1 == workerId || currentPair._2 == workerId) {
            doneList(workerId) = true
            if (doneList(currentPair._1) && doneList(currentPair._2)) allDone = true
          }
          else {
            doneList(workerId) = true
            if (!doneList.contains(false)) allDone = true
          }
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

  private def onWorkerDead(workerId: Int): Unit = {
    deadWorkerId = workerId
  }

  private def executeRound(roundId: Int): Future[Unit] = {
    val doneList = Array.fill(20)(false)
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
        executeRound(roundId).flatMap { _ =>
          loop(roundId + 1)
        }
      }
    }

    loop(start)
  }

  private def executeRoundForDeadWorker(roundId: Int): Future[Unit] = {
    val doneList = Array.fill(20)(false)
    val promise = Promise[Unit]()

    this.synchronized {
      doneCheckLists(roundId) = doneList
      roundPromises(roundId) = promise
    }

    if(roundId != 0) {
      broadcastNextRoundForDeadWorker(roundId)
    }

    promise.future
  }

  def start(): Future[Unit] = {
    repository.onWorkerRoundDone = onWorkerRoundDone
    repository.onWorkerDead = onWorkerDead

    executeRounds(0, 19)
  }

  private val roundRobinPairs: List[List[(Int, Int)]] = {

    val initialPlayers = (1 to 20).toList

    def rotate(players: List[Int]): List[Int] = {
      players.head :: players.last :: players.tail.init
    }

    val playerStates: LazyList[List[Int]] =
      LazyList.iterate(initialPlayers)(rotate)

    playerStates.take(19).map { players =>
      val len = players.length

      (0 until (len / 2)).map { i =>
        (players(i), players(len - 1 - i))
      }.toList
    }.toList
  }
}