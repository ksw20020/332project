package services

import com.google.protobuf.ByteString
import repositories.GrpcShuffleWorkerRepository
import repositories.WorkerRole
import repositories.DiskFileStorageRepository
import models.Record
import shuffle.control.grpcShuffle.RecordBatch

import java.nio.file.Path
import java.io.ByteArrayOutputStream
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ShuffleWorkerService(workerId: Int, port: Int, savePath: String, workerCount: Int) {
  private val RECORD_SIZE = 100
  private val READ_SIZE = 100
  private val fileRepository = DiskFileStorageRepository()
  private val grpcRepository = GrpcShuffleWorkerRepository(
    onReceiveBatch = onReceiveBatch,
    onReceiveReady = onReceiveReady
  )
  private var opponent: Int = -1
  private var readBlocks: Long = 0

  def executeRound(roundId: Int): Future[Unit] = {
    readBlocks = 0

    val (a, b) = roundRobinPairs(roundId)
      .find { case (x, y) => x == workerId || y == workerId }
      .get

    opponent = if (a == workerId) b else a
    val role = if (workerId > opponent) WorkerRole.Client else WorkerRole.Server
    
    grpcRepository.start(role, port, host)
  }
  
  private def onReceiveBatch(rb: RecordBatch): Future[Unit] = {
    Future {
      val path = savePath + s"/shuffling/fromWorker$opponent.dat"
      fileRepository.saveRecord(path, recordBatchToArr(rb), true)
    }
  }

  private def onReceiveReady(): Future[RecordBatch] = {
    Future {
      val path = savePath + s"/temp/temp_partition_forworker$workerId.dat"
      val records = fileRepository.readBlock(path, readBlocks * RECORD_SIZE * READ_SIZE, RECORD_SIZE * READ_SIZE)
      readBlocks += 1
      listToRecordBatch(records)
    }
  }

  private def recordBatchToArr(rb: RecordBatch): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    rb.records.foreach(bs => out.write(bs.toByteArray))
    out.toByteArray
  }

  private def listToRecordBatch(arr: List[Record]): RecordBatch = {
    val recordsAsByteString = arr.map(r => ByteString.copyFrom(r.bytes))
    RecordBatch(records = recordsAsByteString)
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
