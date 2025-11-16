package services

import scala.collection.immutable.List

class SortService(fileRepo: FileStorageRepository) {

  /**
    * 입력 파일 경로에서 데이터를 읽어 메모리에서 정렬합니다 (Local Sort).
    * 이 정렬된 결과는 다음 단계인 PartitioningService로 전달됩니다.
    *
    * @param inputFilePath 정렬할 입력 블록 파일 경로
    * @return 키를 기준으로 정렬된 Record 목록
    */
  def localSort(inputFilePath: String): List[Record] = {
    println(s"[SortService] Start reading and sorting input file: $inputFilePath")
    
    // 1. 데이터 로딩
    // FileStorageRepository를 사용하여 입력 블록을 읽고 Record 객체 목록을 생성합니다.
    val records: List[Record] = try {
      fileRepo.readBlock(inputFilePath)
    } catch {
      case e: Exception =>
        println(s"Error reading file $inputFilePath: ${e.getMessage}")
        // 파일 I/O 오류 발생 시 예외를 던져 상위 관리자(SortPartitionManager)에게 보고
        throw new RuntimeException(s"Failed to read input block for sorting: $inputFilePath", e)
    }

    // 2. 인-메모리 정렬 (Local Sort)
    // Scala의 sortBy 메서드는 Record 객체 내부의 key (RecordKey)를 사용하여 정렬합니다.
    // RecordKey가 Comparable을 구현했기 때문에, 이는 효율적인 정렬을 보장합니다.
    val sortedRecords: List[Record] = records.sortBy(_.key)

    println(s"[SortService] Finished local sort of ${records.length} records.")
    
    // 3. 정렬된 레코드 반환
    sortedRecords
  }
  
  /**
    * SortService의 메인 실행 메서드 (상위 매니저가 호출할 수 있는 인터페이스)
    */
  def execute(inputFilePath: String): List[Record] = {
    localSort(inputFilePath)
  }
}
