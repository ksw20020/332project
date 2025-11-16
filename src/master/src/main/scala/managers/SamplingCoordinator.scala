package managers

import scala.collection.mutable.ArrayBuffer
import scala.util.Sorting

class SamplingCoordinator(workerCount: Int) {

  private val collectedSamples = ArrayBuffer[(Int, Array[Byte])]()

  // Worker로부터 샘플을 수집
  def addSample(workerId: Int, samples: Seq[Array[Byte]]): Unit = {
    samples.foreach(s => collectedSamples.append((workerId, s)))
  }

  // 모든 샘플 수집 후 파티션 결정
  def computePivots(): Seq[Array[Byte]] = {
    val onlyKeys = collectedSamples.map(_._2).toArray

    // 전체 샘플 정렬
    Sorting.quickSort(onlyKeys)(Ordering.by(new String(_)))

    val pivotCount = workerCount - 1
    val step = onlyKeys.length / workerCount

    (1 to pivotCount).map(i => onlyKeys(i * step))
  }
}
