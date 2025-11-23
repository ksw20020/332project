package services

import java.io.File
import scala.collection.immutable.List
import models.*
import repositories.FileStorageRepository

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

    // streamMap 대신, 어떤 파일에 쓰여지고 있는지 파일 객체 정보를 저장합니다.
    val outputFileMap = scala.collection.mutable.Map[Int, File]()

    try {
      sortedRecords.foreach { record =>
        // 1. Partitioner를 사용해 목적지 결정
        val (_, destWorkerId) = Partitioner.getDestination(record.key, partitionRanges)
        
        // 2. 파일 경로 결정 및 파일 객체 등록
        val filePath = getTempFileName(destWorkerId)
        if (!outputFileMap.contains(destWorkerId)) {
          // 해당 목적지 워커 ID의 파일 객체가 Map에 없으면 새로 생성하여 등록합니다.
          outputFileMap(destWorkerId) = new File(filePath)
        }

        // 3. FileStorageRepository를 사용해 레코드의 전체 100바이트를 파일에 이어씁니다.
        // **I/O 책임은 Repository로 위임합니다.**
        fileRepo.saveRecord(
          path = filePath,
          recordBytes = record.bytes,
          append = true // Partitioning 작업은 항상 이어쓰기
        )
      }
      
      println(s"[PartitionService] Finished writing temp files for ${outputFileMap.size} destination workers.")
      outputFileMap.toMap

    } catch {
      case e: Exception =>
        // I/O 오류 또는 파티션 오류 발생 시 예외 전파 (스트림 관리는 Repository 내부로 이동)
        println(s"Partitioning error: ${e.getMessage}")
        throw e
    } finally {
      // Stream 관리는 Repository 내부로 이동했으므로, 여기서는 자원 해제 코드가 불필요하거나,
      // 혹은 Repository에 정리(flush) 요청 메서드를 호출해야 합니다.
      // (현재 디자인에서는 Repository의 saveRecord가 개별적으로 스트림을 열고 닫는다고 가정)
    }
  }
}