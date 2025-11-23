package services

import com.google.protobuf.ByteString
import repositories.SamplingRepository
import models.*

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Random
import java.io.{BufferedInputStream, File, FileInputStream, RandomAccessFile}
import scala.collection.mutable.ArrayBuffer

class SamplingService(
                       filePath: String,
                       channel: io.grpc.ManagedChannel,
                       workerId: Int
                     ) {
  private val repository = new SamplingRepository(
    channel = channel,
    workerId = workerId,
    onReceiveResult = onReceiveResult
  )
  val p = Promise[Seq[PartitionRange]]()
  private val RECORD_SIZE = 100

  def executeRound(): Future[Seq[PartitionRange]] = {
    val samples = extractSamples(filePath, 500)
    val records = samples.map(s => RecordKey(s.take(10)))
    repository.sendSamplingRequest(records)
    p.future
  }

  private def onReceiveResult(result: Seq[ByteString]): Unit = {
    // Min Start (0x00 * 10 bytes)
    val minStart = ByteString.copyFrom(new Array[Byte](10))

    // Max End (0xFF * 10 bytes)
    val maxEnd = ByteString.copyFrom(Array.fill[Byte](10)(-1))

    val allBoundaries = (minStart +: result) :+ maxEnd

    val partitionRanges = allBoundaries.sliding(2).zipWithIndex.map {
      case (Seq(start, end), index) =>
        PartitionRange(
          id = index,
          startKey = RecordKey(start.toByteArray),
          endKey = RecordKey(end.toByteArray),
          destWorkerId = index
        )
    }.toSeq

    p.trySuccess(partitionRanges)
  }


  private def extractSamples(filePath: String, sampleCount: Int): Seq[Array[Byte]] = {
    val root = new File(filePath)

    val files = if (root.isDirectory) {
      root.listFiles().filter(_.isFile).sortBy(_.getName)
    } else {
      Array(root)
    }

    if (files == null || files.isEmpty) return Seq.empty

    val samples = new ArrayBuffer[Array[Byte]](sampleCount)
    var remaining = sampleCount

    val iterator = files.iterator
    while (remaining > 0 && iterator.hasNext) {
      val file = iterator.next()
      val bis = new BufferedInputStream(new FileInputStream(file))

      try {
        // 파일 끝에 도달하거나 필요한 만큼 다 모을 때까지 반복
        while (remaining > 0) {
          val buffer = new Array[Byte](RECORD_SIZE)

          // 100바이트를 확실하게 읽기 위한 로직
          // (read 메서드는 100보다 적게 읽을 수도 있으므로 루프 필요)
          var bytesRead = 0
          var eof = false
          while (!eof && bytesRead < RECORD_SIZE) {
            val n = bis.read(buffer, bytesRead, RECORD_SIZE - bytesRead)
            if (n == -1) eof = true
            else bytesRead += n
          }

          if (bytesRead == RECORD_SIZE) {
            // 정상적으로 100바이트 읽음
            samples += buffer
            remaining -= 1
          } else {
            eof = true
          }

        }
      } catch {
        case exception: Exception =>
          println(s"Error reading file: ${exception.getMessage}")
      }
      finally {
        if (bis != null) bis.close()
      }
    }

    samples.toSeq
  }
}
