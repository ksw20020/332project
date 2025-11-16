package managers

import com.google.protobuf.ByteString
import scala.util.Random

/** STS: 정렬 후 strata 기반 층화 추출 */
class Sampler(
    k: Int,             // strata 수 = worker 수
    sampleCount: Int    // 전체 샘플 개수
) {

  /** keys는 이미 정렬된 로컬 데이터의 key 배열 */
  def stratifiedSample(sortedKeys: Seq[Array[Byte]]): Seq[Array[Byte]] = {

    val total = sortedKeys.length.toDouble
    if (total == 0) return Seq.empty

    // 1. 전체 키 공간에서 strata 구간 계산
    val maxKey = BigInt(1, sortedKeys.last)  // 마지막 key
    val minKey = BigInt(1, sortedKeys.head)  // 첫 key
    val range = maxKey - minKey
    val stride = range / k

    // strata 구간별로 데이터 모으기
    val strataBuckets = Array.fill(k)(scala.collection.mutable.ArrayBuffer[Array[Byte]]())

    for (key <- sortedKeys) {
      val keyBI = BigInt(1, key)
      val idx = ((keyBI - minKey) / stride).toInt.min(k-1)
      strataBuckets(idx) += key
    }

    // 전체 데이터 개수
    val N = total

    // strata별 비례 샘플링
    val samples = scala.collection.mutable.ArrayBuffer[Array[Byte]]()

    for (i <- 0 until k) {
      val Ni = strataBuckets(i).length.toDouble
      if (Ni > 0) {
        val takeCount = math.max(1, (Ni / N * sampleCount).toInt)

        val bucket = strataBuckets(i)
        val randIdx = Random.shuffle(bucket).take(takeCount)
        samples ++= randIdx
      }
    }

    samples.toSeq
  }

}
