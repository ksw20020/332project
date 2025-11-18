package services

import com.google.protobuf.ByteString

import scala.collection.mutable.ArrayBuffer
import scala.math.Ordering.Implicits.seqOrdering
import scala.util.Sorting

/** STS 기반 pivot 계산 */
class SamplingCoordinator(workerCount: Int) {

  private val allSamples = ArrayBuffer[ByteString]()

  def addSample(workerId: Int, samples: Seq[ByteString]): Unit = {
    allSamples ++= samples
  }

  /** 전체 strata 샘플을 정렬한 뒤 pivot 계산 */
  def computePivots(): Seq[ByteString] = {
    if (allSamples.isEmpty) return Seq.empty

    implicit val ordering: Ordering[ByteString] =
      Ordering.by(_.toByteArray.toIndexedSeq)

    val arr = allSamples.toArray
    Sorting.quickSort(arr)

    // workerCount-1 개의 pivot 필요
    val pivotsNeeded = workerCount - 1
    val stride = arr.length / workerCount

    (1 to pivotsNeeded).map(i => arr(math.min(i * stride, arr.length - 1)))
  }
}
