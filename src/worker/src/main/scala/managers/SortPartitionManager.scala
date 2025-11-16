package managers

import services.SortService

class SortPartitionManager(
  sortService: SortService,
  partitionService: PartitionService, // PartitionService 가정
  ...) {

  def sortAndPartitionAll(inputFiles: List[String]): Unit = {
    // 멀티스레딩 환경을 위한 Future/ThreadPool을 가정
    
    inputFiles.foreach { path =>
      // 1. Local Sort 실행
      val sortedRecords = sortService.execute(path)
      
      // 2. Partitioning 실행 (Sort 결과를 PartitionService로 전달)
      // partitionService.partitionRecords(sortedRecords, partitionRanges, tempDir)
    }
  }
}