package managers

import services.SortService
import services.partitionService
import scala.concurrent.{Future, ExecutionContext}
import java.io.File

class SortPartitionManager(
  sortService: SortService,
  partitionService: PartitionService
)(implicit ec: ExecutionContext) {

  def start_local(
    inputFiles: List[String], 
    ranges: Array[PartitionRange]
  ): Future[Unit] = {
    
    println(s"[SortPartitionManager] Starting Sort & Partition for ${inputFiles.size} files.")

    val futures = inputFiles.map { filePath =>
      processSingleFile(filePath, ranges)
    }

    Future.sequence(futures).map(_ => ())
  }

  private def processSingleFile(filePath: String, ranges: Array[PartitionRange]): Future[Unit] = {
    sortService.sortNextBatch(filePath).flatMap { sortedRecords =>
      if (sortedRecords.nonEmpty) {
        val result = partitionService.partitionRecords(sortedRecords, ranges)
        Future.successful(result)
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