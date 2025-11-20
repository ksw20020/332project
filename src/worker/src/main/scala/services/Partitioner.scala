package services

import java.util.Comparator

object Partitioner {

  private val PartitionSearchComparator: Comparator[Any] = new Comparator[Any] {
    override def compare(o1: Any, o2: Any): Int = {
      val keyToFind = o1.asInstanceOf[RecordKey]

      if (o2.isInstanceOf[PartitionRange]) {
        val range = o2.asInstanceOf[PartitionRange]

        if (keyToFind.compareTo(range.startKey) < 0) return -1
        if (keyToFind.compareTo(range.endKey) >= 0) return 1
        return 0
      }

      throw new ClassCastException("Binary search comparison target must be PartitionRange.")
    }
  }

  def getDestination(key: RecordKey, ranges: Array[PartitionRange]): (Int, Int) = {

    val index = java.util.Arrays.binarySearch(
      ranges.asInstanceOf[Array[Object]], 
      key,                                
      PartitionSearchComparator           
    )

    if (index >= 0) {
      val range = ranges(index)
      (range.id, range.destWorkerId)
    } else {
      throw new IllegalStateException(s"Key ${key} does not fall into any defined partition range. Index: $index")
    }
  }
}