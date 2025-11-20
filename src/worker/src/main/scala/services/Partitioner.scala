package services

import java.util.Comparator
import models._

object Partitioner {

  // PartitionRange의 startKey를 기준으로 RecordKey와 비교하는 Comparator를 정의합니다.
  // 이 Comparator는 binarySearch에 전달되어, RecordKey가 어떤 PartitionRange에 속하는지 찾습니다.
  private val PartitionSearchComparator: Comparator[Any] = new Comparator[Any] {
    override def compare(o1: Any, o2: Any): Int = {
      // o1: binarySearch의 keyToFind (RecordKey 타입)
      // o2: binarySearch가 배열에서 비교하는 요소 (PartitionRange 타입)
      
      val keyToFind = o1.asInstanceOf[RecordKey]
      
      // o2가 PartitionRange 타입인지 확인합니다.
      if (o2.isInstanceOf[PartitionRange]) {
        val range = o2.asInstanceOf[PartitionRange]

        // 1. 키가 범위의 시작 키보다 작으면 이 범위의 앞에 위치 (-1)
        if (keyToFind.compareTo(range.startKey) < 0) return -1
        // 2. 키가 범위의 끝 키보다 크거나 같으면 이 범위의 뒤에 위치 (+1)
        if (keyToFind.compareTo(range.endKey) >= 0) return 1
        
        // 3. 키가 범위 내에 있으므로 일치함 (0)
        return 0
      }
      
      // 비교 대상의 타입이 잘못된 경우
      throw new ClassCastException("Binary search comparison target must be PartitionRange.")
    }
  }

  def getDestination(key: RecordKey, ranges: Array[PartitionRange]): (Int, Int) = {
    // Array[PartitionRange]를 검색하되, 찾을 키(key)는 RecordKey 타입임을 명시합니다.
    val index = java.util.Arrays.binarySearch(
      ranges.asInstanceOf[Array[Object]], // 배열은 Object 배열로 캐스팅해야 합니다.
      key,                                // 찾으려는 키 (RecordKey)
      PartitionSearchComparator           // 정의된 Comparator 사용
    )

    if (index >= 0) {
      val range = ranges(index)
      (range.id, range.destWorkerId)
    } else {
      // 경계 처리: 파티션 정의가 완벽하여 모든 키를 포함해야 합니다.
      // 인덱스가 음수라는 것은 키가 어떤 범위에도 속하지 않음을 의미합니다.
      throw new IllegalStateException(s"Key ${key} does not fall into any defined partition range. Index: $index")
    }
  }
}