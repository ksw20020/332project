package services

import com.google.protobuf.ByteString
import repositories.SamplingRepository

import java.util.Comparator
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.math.Ordering.Implicits.seqOrdering
import scala.util.Sorting

/** 샘플링 전용 gRPC 서비스 (Master 측).
  *
  *  - Worker → Master : SendSample()
  *  - Worker ← Master : GetPartitionInfo()
  */
class SamplingService(
                       repository: SamplingRepository,
                       workerCount: Int
                     ) {
  private val allSamples = ArrayBuffer[ByteString]()
  private var receivedCount = 0
  private val receivedFlags = new Array[Boolean](workerCount)
  @volatile private var _pivots: Option[Seq[ByteString]] = None

  lazy val p = Promise[Unit]()

  def start(): Future[Unit] = {
    repository.onWorkerRequest = onWorkerRequest
    
    p.future
  }

  private def onWorkerRequest(workerId: Int, samples: Seq[ByteString]): Unit = {
    if (_pivots.isDefined) {
      sendPartitionInfo()
      return
    }

    var shouldCompute = false
    val workerIndex = workerId - 1
    
    this.synchronized {
      if (_pivots.isEmpty && !receivedFlags(workerIndex)) {
        receivedFlags(workerIndex) = true
        receivedCount += 1
        allSamples ++= samples

        if (receivedCount == workerCount) {
          shouldCompute = true
        }
      }
    }

    if (shouldCompute) {
      computePivots()
    } else if (_pivots.isDefined) {
      sendPartitionInfo()
    }
  }

  private def computePivots(): Unit = {
    if (allSamples.isEmpty) return

    implicit val ordering: Ordering[ByteString] = new Ordering[ByteString] {
      val comparator: Comparator[ByteString] = ByteString.unsignedLexicographicalComparator()

      override def compare(x: ByteString, y: ByteString): Int = comparator.compare(x, y)
    }

    val arr = this.synchronized {
      if (allSamples.isEmpty) return
      allSamples.toArray
    }
    Sorting.quickSort(arr)

    val pivotsNeeded = workerCount - 1

    val data = (1 to pivotsNeeded).map { i =>
      val index = (arr.length.toLong * i / workerCount).toInt
      val safeIndex = math.max(0, math.min(index, arr.length - 1))

      arr(safeIndex)
    }
    
    if (_pivots.isEmpty) {
      _pivots = Some(data)
    }
    sendPartitionInfo()
    if (p != null && !p.isCompleted) {
      p.trySuccess(())
    }
  }

  private def sendPartitionInfo(): Unit = {
    repository.sendSamplingResult(_pivots.get)
  }

}
