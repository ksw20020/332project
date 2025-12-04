package managers

import services.SortService
import services.PartitionService
import scala.concurrent.{Future, ExecutionContext}
import java.io.File
import models._

class SortPartitionManager(
  sortService: SortService,
  partitionService: PartitionService
)(implicit ec: ExecutionContext) {

  private val BLOCK_SIZE_BYTES = 100 * 10000L

  def start_local(
    inputFiles: List[String], 
    ranges: Array[PartitionRange]
  ): Future[Unit] = {
    
    println(s"[SortPartitionManager] Starting Sort & Partition for ${inputFiles.size} files.")

    val futures = inputFiles.map { filePath =>
      processSingleFile(filePath, ranges, 0L)
    }

    Future.sequence(futures).map(_ => ())
  }

  private def processSingleFile(filePath: String, ranges: Array[PartitionRange], currentOffset: Long): Future[Unit] = {
    sortService.sortNextBatch(filePath, currentOffset).flatMap { sortedRecords =>
      if (sortedRecords.nonEmpty) {
        val result = partitionService.partitionRecords(sortedRecords, ranges)
        val nextOFFset = currentOffset + (sortedRecords.size * 100L)
        processSingleFile(filePath, ranges, nextOFFset)
      } else {
        Future.successful(Map.empty[Int, File])
      }
    }.map(_ => ())
  }

  def start_aftersuffle(
    inputPartitionFiles: Seq[String], 
    outputFilePath: String
  ): Future[Unit] = {
    
    println(s"[SortPartitionManager] Starting Merge Sort -> $outputFilePath")
    
    sortService.kWayMerge(inputPartitionFiles, outputFilePath)
  }
}