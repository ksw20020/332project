package services

import com.google.protobuf.ByteString
import repositories.GrpcShuffleWorkerRepository
import repositories.WorkerRole
import repositories.DiskFileStorageRepository
import models.Record
import shuffle.control.grpcShuffle.RecordBatch

import java.nio.file.{Files, Path, Paths}
import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Paths}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ShuffleWorkerService(workerId: Int, port: Int, savePath: String, workerCount: Int) {
  private val RECORD_SIZE = 100
  private val READ_SIZE = 100
  private val fileRepository = new DiskFileStorageRepository()
  private val grpcRepository = new GrpcShuffleWorkerRepository(
    onReceiveBatch = onReceiveBatch,
    onReceiveReady = onReceiveReady
  )
  private var opponent: Int = -1
  private var readBlocks: Long = 0

  def moveSelfDataToShuffling(): Future[Unit] = {
    Future {
      // 1. 옮길 원본 경로 (자신의 temp 데이터)
      val sourcePathStr = savePath + s"/temp/temp_partition_for_worker_$workerId.dat"
      // 2. 이동할 목적지 경로 (자신의 shuffling 데이터)
      val destPathStr = savePath + s"/shuffling/fromWorker$workerId.dat"

      val source = Paths.get(sourcePathStr)
      val dest = Paths.get(destPathStr)

      if (Files.exists(source)) {
        try {
          Files.move(source, dest)
          println(s"[Worker $workerId] Successfully moved self-data to $destPathStr")
        } catch {
          case e: Exception =>
            println(s"[Worker $workerId] Failed to move self-data: ${e.getMessage}")
            throw e
        }
      } else {
        println(s"[Worker $workerId] No self-data found at $sourcePathStr. Skipping move.")
      }
    }
  }

  def executeRound(roundId: Int, partnerIp: String, partnerPort: Int): Future[Unit] = {
    this.synchronized {
      readBlocks = 0
    }

    val (a, b) = roundRobinPairs(roundId - 1)
      .find { case (x, y) => x == workerId || y == workerId }
      .get

    opponent = if (a == workerId) b else a
    val role = if (workerId > opponent) WorkerRole.Client else WorkerRole.Server

    val receivingFilePath = savePath + s"/shuffling/fromWorker$opponent.dat"
    fileRepository.deleteFile(receivingFilePath)



    grpcRepository.start(role, partnerPort, partnerIp).recoverWith { case ex: Throwable =>
      println(s"[Worker $workerId] Round $roundId failed. Cleaning up garbage data...")

      fileRepository.deleteFile(receivingFilePath)

      Future.failed(ex)
    }
  }
  
  private def onReceiveBatch(rb: RecordBatch): Future[Unit] = {
    Future {
      val path = savePath + s"/shuffling/fromWorker$opponent.dat"
      fileRepository.saveRecord(path, recordBatchToArr(rb), true)
    }
  }

  private def onReceiveReady(): Future[RecordBatch] = {
    Future {
      val path = savePath + s"/temp/temp_partition_for_worker_$opponent.dat"

      if (readBlocks >= 0) {
        val offset = readBlocks * RECORD_SIZE * READ_SIZE
        val length = RECORD_SIZE * READ_SIZE

        val records = fileRepository.readBlock(path, offset, length)

        if (records.nonEmpty) {
          readBlocks += 1
          listToRecordBatch(records)
        } else {
          readBlocks = -1
          RecordBatch(records = Seq.empty)
        }
      } else {
        RecordBatch(records = Seq.empty)
      }
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
