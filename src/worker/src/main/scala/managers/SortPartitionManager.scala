package managers

import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._
import java.util.concurrent.Executors

class SortPartitionManager(
  sortService: SortService,
  partitionService: PartitionService,
  inputFiles: List[String], // 워커에게 할당된 모든 입력 파일 목록
  partitionRanges: Array[PartitionRange]
) {
  // 디스크 I/O에 최적화된 고정 스레드 풀을 생성합니다.
  private val NUM_IO_THREADS = 4 // 예시 값, 실제 환경에 따라 최적화 필요
  private implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(
    Executors.newFixedThreadPool(NUM_IO_THREADS)
  )

  /**
    * 하나의 입력 블록에 대한 Sort와 Partitioning을 순차적으로 수행하는 태스크입니다.
    */
  private def processInputBlock(path: String): Map[Int, File] = {
    // 1. Local Sort 실행
    val sortedRecords = sortService.localSort(path)
    
    // 2. Partitioning 실행
    val tempFiles = partitionService.partitionRecords(sortedRecords, partitionRanges)
    
    tempFiles
  }
  
  /**
    * 워커에게 할당된 모든 입력 파일을 병렬로 처리합니다.
    *
    * @return Map[Destination Worker ID, File] 형태의 모든 생성된 임시 파일 목록
    * @throws RuntimeException 작업 중 하나라도 실패하면 예외를 던집니다.
    */
  def sortAndPartitionAll(): Map[Int, File] = {
    println(s"[SortPartitionManager] Starting parallel Sort/Partition for ${inputFiles.size} blocks.")

    // 모든 파일에 대해 processInputBlock 태스크를 Future로 생성하고 병렬 실행
    val allFutures: List[Future[Map[Int, File]]] = inputFiles.map { path =>
      Future {
        // 이 블록에서 발생하는 모든 예외는 Future에 포착됩니다.
        processInputBlock(path)
      }
    }

    try {
      // 모든 Future가 완료될 때까지 대기하고 결과를 취합합니다.
      // Duration.Inf는 무한 대기 (실제 시스템에서는 타임아웃을 설정해야 함)
      val results: List[Map[Int, File]] = Await.result(Future.sequence(allFutures), Duration.Inf)

      // 모든 스레드의 결과를 하나의 Map으로 병합합니다. (주: 키가 중복될 수 있으나, 여기서는 파일 객체이므로 괜찮습니다.)
      results.flatten.toMap

    } catch {
      case e: Exception =>
        println(s"[SortPartitionManager] FATAL ERROR during parallel processing: ${e.getMessage}")
        // 작업 실패 시 시스템 종료 또는 마스터에게 보고
        throw new RuntimeException("Sort/Partition phase failed.", e)
    } finally {
      // 스레드 풀 사용 후 종료
      ec.shutdown()
    }
  }
}