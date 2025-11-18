package services

import java.io.{FileOutputStream, File}

// FileStorageRepository와 Partitioner는 DI를 통해 주입된다고 가정
class PartitionService(
  fileRepo: FileStorageRepository,
  temp: String // 임시 파일 저장 경로
) {

  // 임시 파일명 규칙
  private def getTempFileName(destWorkerId: Int): String =
    s"$temp/temp_partition_for_worker_$destWorkerId.dat"

  /**
    * 로컬 정렬이 완료된 레코드 목록을 전역 파티션 범위에 따라 분류하고 디스크에 씁니다.
    *
    * @param sortedRecords 로컬 정렬된 Record 목록
    * @param partitionRanges 마스터가 제공한 전역 파티션 범위
    * @return Map[Destination Worker ID, File] 형태로 생성된 임시 파일 목록
    */
  def partitionRecords(
    sortedRecords: List[Record],
    partitionRanges: Array[PartitionRange]
  ): Map[Int, File] = {
    
    println(s"[PartitionService] Starting partitioning of ${sortedRecords.length} records.")

    val streamMap = scala.collection.mutable.Map[Int, FileOutputStream]()
    val outputFileMap = scala.collection.mutable.Map[Int, File]()

    try {
      sortedRecords.foreach { record =>
        // 1. Partitioner를 사용해 목적지 결정
        val (_, destWorkerId) = Partitioner.getDestination(record.key, partitionRanges)

        // 2. 해당 워커 ID의 파일 스트림을 가져오거나 새로 생성
        val outputStream = streamMap.getOrElseUpdate(destWorkerId, {
          val file = new File(getTempFileName(destWorkerId))
          outputFileMap(destWorkerId) = file // 생성된 파일 정보 저장
          new FileOutputStream(file, true) // 기존 파일에 이어쓰기 (append)
        })

        // 3. 파일에 레코드의 전체 100바이트를 씁니다.
        outputStream.write(record.bytes)
      }
      
      println(s"[PartitionService] Finished writing temp files for ${streamMap.size} destination workers.")
      outputFileMap.toMap

    } catch {
      case e: Exception =>
        // I/O 오류 또는 파티션 오류 발생 시 스트림 닫고 예외 전파
        println(s"Partitioning error: ${e.getMessage}")
        throw e
    } finally {
      // 모든 파일 스트림을 닫습니다.
      streamMap.values.foreach(_.close())
    }
  }
}