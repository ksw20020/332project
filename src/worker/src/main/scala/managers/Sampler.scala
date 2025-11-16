package managers

import scala.util.Random

class Sampler {

  def sample(sortedData: Seq[Array[Byte]], sampleCount: Int): Seq[Array[Byte]] = {
    if (sortedData.isEmpty) return Seq.empty

    val rand = new Random()
    (0 until sampleCount).map { _ =>
      val idx = rand.nextInt(sortedData.length)
      sortedData(idx)
    }
  }
}
