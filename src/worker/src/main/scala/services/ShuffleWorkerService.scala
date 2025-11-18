package services

import repositories.GrpcShuffleWorkerRepository
import repositories.WorkerRole
import repositories.DiskFileStorageRepository
import shuffle.control.grpcShuffle.RecordBatch

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ShuffleWorkerService(workerId: Int, port: Int) {
  val fileRepository = DiskFileStorageRepository()
  val grpcRepository = GrpcShuffleWorkerRepository(
    onReceiveBatch = ???,
    onReceiveReady = ???
  )

  def executeRound(roundId: Int): Future[Unit] = {
    val (a, b) = roundRobinPairs(roundId)
      .find { case (x, y) => x == workerId || y == workerId }
      .get

    val opponent = if (a == workerId) b else a
    val role = if (workerId > opponent) WorkerRole.Client else WorkerRole.Server
    
    grpcRepository.start(role, port, host)
  }
  
  private def onReceiveBatch(rb: RecordBatch): Future[Unit] = {
    Future {
      
    }
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
