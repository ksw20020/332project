package worker

import io.grpc.ManagedChannelBuilder
import repositories.DiskFileStorageRepository

import java.io.File
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import java.nio.file.{Files, Paths}
import java.util.concurrent.Executors
import scala.collection.mutable.ListBuffer

// 필요한 매니저 및 서비스, 리포지토리 임포트
import managers.{RegistrationManager, SamplingManager, ShuffleManager, SortPartitionManager}
import services.{SamplingService, ShuffleMasterService, ShuffleWorkerService, SortService, PartitionService}
import repositories.{SamplingRepository, GrpcShuffleMasterRepository, FileStorageRepository}
// (FileRepository 등은 구현체가 있다고 가정합니다)

object WorkerApp {
  implicit val ec: ExecutionContext = ExecutionContext.global
  private val WORKER_PORT = 5002

  def main(args: Array[String]): Unit = {
    // 1. Argument Parsing
    if (args.length < 4) {
      System.err.println("Usage: worker <masterIP:port> -I <input_dirs...> -O <output_dir>")
      sys.exit(1)
    }

    val masterInfo = args(0).split(":")
    val masterIp = masterInfo(0)
    val masterPort = masterInfo(1).toInt

    val inputDirs = ListBuffer[String]()
    var outputDir = ""

    // 파싱 로직 (-I 뒤의 경로들 수집, -O 뒤의 경로 수집)
    var parsingInput = false
    var parsingOutput = false

    for (i <- 1 until args.length) {
      args(i) match {
        case "-I" =>
          parsingInput = true
          parsingOutput = false
        case "-O" =>
          parsingInput = false
          parsingOutput = true
        case arg if parsingInput => inputDirs += arg
        case arg if parsingOutput => outputDir = arg
        case _ => // 무시
      }
    }

    println(s"=== Worker Starting ===")
    println(s"Master: $masterIp:$masterPort")
    println(s"Input Directories: ${inputDirs.mkString(", ")}")
    println(s"Output Directory: $outputDir")

    // 2. 입력 파일 리스트 확보 (디렉토리 내 파일 스캔)
    val inputFiles = inputDirs.flatMap { dirPath =>
      val dir = new File(dirPath)
      if (dir.exists() && dir.isDirectory) {
        dir.listFiles().filter(_.isFile).map(_.getAbsolutePath)
      } else {
        Array.empty[String]
      }
    }.toList

    if (inputFiles.isEmpty) {
      System.err.println("No input files found!")
      sys.exit(1)
    }

    // 3. gRPC Channel 생성 (Master 연결용)
    val channel = ManagedChannelBuilder
      .forAddress(masterIp, masterPort)
      .usePlaintext()
      .maxInboundMessageSize(100 * 1024 * 1024) // 대용량 전송 허용
      .build()

    try {
      println("\n[Phase 1] Registration...")
      val regManager = new RegistrationManager(channel)

      // start() 내부에서 getPrivateIp 및 5002 포트로 마스터에 등록함
      val (workerId, workerCount) = regManager.start()
      println(s"Registered successfully! WorkerID: $workerId, TotalWorkers: $workerCount")

      val baseDir = new File(".").getCanonicalPath
      val tempDir = s"$baseDir/temp"
      val shufflingDir = s"$baseDir/shuffling"

      recreateDir(tempDir)
      recreateDir(shufflingDir)

      def recreateDir(path: String): Unit = {
        val dir = new File(path)

        if (dir.exists()) {
          def deleteRecursively(f: File): Unit = {
            if (f.isDirectory) f.listFiles().foreach(deleteRecursively)
            f.delete()
          }

          dir.listFiles().foreach(deleteRecursively)
        }

        dir.mkdirs()
      }

      println("\n[Phase 2] Sampling...")
      val samplingManager = new SamplingManager(channel, workerId, inputFiles.head)

      val workflowFuture = samplingManager.startSampling().flatMap { ranges =>
        println(s"Received ${ranges.length} partition ranges from Master.")

        println("\n[Phase 3] Local Sort & Partition...")

        val fileRepo = new DiskFileStorageRepository()
        val sortService = new SortService(fileRepo)
        val partitionService = new PartitionService(fileRepo, tempDir)

        val sortPartitionManager = new SortPartitionManager(sortService, partitionService)

        sortPartitionManager.start_local(inputFiles, ranges.toArray).flatMap { _ =>
          println("Local Sort & Partition Completed.")

          println("\n[Phase 4] Shuffling...")

          val shuffleManager = new ShuffleManager(
            channel = channel,
            workerId = workerId,
            port = WORKER_PORT,
            savePath = baseDir,
            workerCount = workerCount
          )

          // 셔플을 시작하고(마스터에 0라운드 보고), 셔플 서버가 종료될 때까지 대기
          shuffleManager.startShuffle().flatMap { _ =>
            println("Shuffle Phase Completed.")

            println("\n[Phase 5] Final Merge...")

            // 셔플된 결과 파일들이 저장된 위치 (ShuffleWorkerService 로직에 따름)
            val shuffleOutputDir = new File(s"$baseDir/shuffling")
            val shuffledFiles = if (shuffleOutputDir.exists()) {
              shuffleOutputDir.listFiles().filter(_.getName.endsWith(".dat")).map(_.getAbsolutePath).toSeq
            } else {
              Seq.empty[String]
            }

            // 최종 결과 파일 경로
            recreateDir(outputDir)

            val finalOutputPath = s"$outputDir/part-$workerId"

            sortPartitionManager.start_aftersuffle(shuffledFiles, finalOutputPath).map { _ =>
              println(s"JOB FINISHED. Output saved to: $finalOutputPath")
            }
          }
        }
      }

      Await.result(workflowFuture, Duration.Inf)

    } catch {
      case e: Exception =>
        System.err.println(s"Worker failed: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      channel.shutdown()
    }
  }
}