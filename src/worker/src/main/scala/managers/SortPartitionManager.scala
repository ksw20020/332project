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
  private val MAX_MERGE_FACTOR = 100

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

          recursiveMerge(chunkFiles, finalLocalFile, wid, 0)
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

  private def recursiveMerge(
                              files: Seq[String],
                              outputFile: String,
                              workerId: Int,
                              pass: Int
                            ): Future[Unit] = {

    // 종료 조건: 파일이 병합 인자(MAX_MERGE_FACTOR)보다 적으면 한 번에 병합하여 끝냄
    if (files.size <= MAX_MERGE_FACTOR) {
      println(s"[LocalMerge] Worker $workerId (Final Pass): Merging ${files.size} files -> $outputFile")
      sortService.kWayMerge(files, outputFile).map { _ =>
        // 원본 청크들 삭제
        files.foreach(f => new File(f).delete())
      }
    } else {
      // 진행: 파일을 배치 단위로 잘라서 중간 파일(intermediate) 생성
      println(s"[LocalMerge] Worker $workerId (Pass $pass): Merging ${files.size} files in batches of $MAX_MERGE_FACTOR...")

      // 파일을 MAX_MERGE_FACTOR 개수만큼 그룹으로 나눔
      val batches = files.grouped(MAX_MERGE_FACTOR).toSeq

      // 각 배치를 병렬(혹은 순차)로 병합하여 중간 파일 생성
      val batchFutures = batches.zipWithIndex.map { case (batchFiles, index) =>
        val intermediateFile = s"$temp/intermediate_w${workerId}_p${pass}_$index.dat"

        sortService.kWayMerge(batchFiles, intermediateFile).map { _ =>
          // 병합된 원본 청크 삭제 (디스크 공간 확보)
          batchFiles.foreach(f => new File(f).delete())
          intermediateFile // 생성된 중간 파일 경로 반환
        }
      }

      // 모든 배치가 처리되면, 생성된 중간 파일들로 다시 재귀 호출
      Future.sequence(batchFutures).flatMap { intermediateFiles =>
        recursiveMerge(intermediateFiles, outputFile, workerId, pass + 1)
      }
    }
  }
}