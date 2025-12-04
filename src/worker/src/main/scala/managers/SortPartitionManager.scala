package managers

import services.SortService
import services.PartitionService
import scala.concurrent.{Future, ExecutionContext}
import java.io.File
import models._

class SortPartitionManager(
  sortService: SortService,
  partitionService: PartitionService,
  temp: String
)(implicit ec: ExecutionContext) {

  private val BLOCK_SIZE_BYTES = 100 * 10000L

  def start_local(
    inputFiles: List[String], 
    ranges: Array[PartitionRange]
  ): Future[Unit] = {
    
    println(s"[SortPartitionManager] Starting Phase 1: Sort & Partition (creating chunks)...")

    // 1. 모든 입력 파일을 청크 단위로 나누어 파티셔닝 (중간 파일 생성)
    val futures = inputFiles.map { filePath =>
      processSingleFile(filePath, ranges, 0L)
    }

    // 2. 모든 파티셔닝 작업이 끝나면, 로컬 머지 수행
    Future.sequence(futures).flatMap { _ =>
      println("[SortPartitionManager] Phase 1 Done. Starting Phase 1.5: Local Merge...")
      mergeLocalChunks(ranges)
    }
  }

  private def processSingleFile(filePath: String, ranges: Array[PartitionRange], currentOffset: Long): Future[Unit] = {
    sortService.sortNextBatch(filePath, currentOffset).flatMap { sortedRecords =>
      if (sortedRecords.nonEmpty) {
        val uniqueId = new File(filePath).getName + "_" + currentOffset
        partitionService.partitionRecords(sortedRecords, ranges, uniqueId)
        val nextOFFset = currentOffset + (sortedRecords.size * 100L)
        processSingleFile(filePath, ranges, nextOFFset)
      } else {
        Future.successful(Map.empty[Int, File])
      }
    }.map(_ => ())
  }

  private def mergeLocalChunks(ranges: Array[PartitionRange]): Future[Unit] = {
    // 1. 존재하는 모든 Dest Worker ID 추출 (중복 제거)
    val destWorkerIds = ranges.map(_.destWorkerId).distinct

    // 2. 각 워커 ID별로 병합 작업 병렬 실행
    val mergeFutures = destWorkerIds.map { wid =>
      Future {
        // A. 해당 워커로 가는 모든 중간 파일(chunk) 찾기
        val chunkPrefix = s"chunk_partition_for_worker_${wid}_"
        val chunkFiles = new File(temp).listFiles()
          .filter(f => f.isFile && f.getName.startsWith(chunkPrefix))
          .map(_.getAbsolutePath)
          .toSeq

        if (chunkFiles.nonEmpty) {
          // B. 최종 목적지 파일명 (하나의 파일)
          // 예: temp/partition_for_worker_1.dat
          val finalLocalFile = s"$temp/temp_partition_for_worker_$wid.dat"
          
          println(s"[LocalMerge] Merging ${chunkFiles.size} chunks into $finalLocalFile")
          
          // C. SortService의 kWayMerge 재사용 (이미 정렬된 청크들이므로 효율적)
          // 주의: kWayMerge는 Future를 반환하므로 Await하거나 flatMap 체이닝 필요.
          // 여기서는 Future 안의 Future 구조를 피하기 위해 아래에서 flatten함.
          sortService.kWayMerge(chunkFiles, finalLocalFile).map { _ =>
            // D. (선택사항) 병합 완료 후 중간 청크 파일 삭제
             chunkFiles.foreach(f => new File(f).delete())
          }
        } else {
          Future.successful(())
        }
      }.flatten // Future[Future[Unit]] -> Future[Unit]
    }

    Future.sequence(mergeFutures).map(_ => ())
  }

  def start_aftersuffle(
    inputPartitionFiles: Seq[String], 
    outputFilePath: String
  ): Future[Unit] = {
    
    println(s"[SortPartitionManager] Starting Merge Sort -> $outputFilePath")
    
    sortService.kWayMerge(inputPartitionFiles, outputFilePath)
  }
}