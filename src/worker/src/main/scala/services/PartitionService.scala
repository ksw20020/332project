package services


import java.io.File
import scala.collection.immutable.List
import models._
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
    val outputFileMap = scala.collection.mutable.Map[Int, File]()

    // 1. 메모리 상에서 목적지 별로 그룹핑 (I/O 없음, 매우 빠름)
    // 결과 타입: Map[WorkerId, List[Record]]
    val groupedRecords: Map[Int, List[Record]] = sortedRecords.groupBy { record =>
      val (_, destWorkerId) = Partitioner.getDestination(record.key, partitionRanges)
      destWorkerId
    }

    // 2. 목적지(Worker) 별로 모아진 데이터를 한 번에 파일에 쓰기
    try {
      groupedRecords.foreach { case (destWorkerId, records) =>
        val filePath = getTempFileName(destWorkerId)
        outputFileMap(destWorkerId) = new File(filePath)

        this.synchronized {
          fileRepo.saveBatch(filePath, records, append = true)
        }
      }

      println(s"[PartitionService] Finished batch writing for ${outputFileMap.size} partitions.")
      outputFileMap.toMap

    } catch {
      case e: Exception =>
        println(s"Partitioning error: ${e.getMessage}")
        throw e
    }
  }
}