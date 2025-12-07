package master

import io.grpc.ServerBuilder
import io.grpc.netty.NettyServerBuilder
import register.grpcRegister.RegisterServiceGrpc
import sampling.grpcSampling.SamplingServiceGrpc
import shuffle.control.grpcShuffle.ShuffleControlServiceGrpc
import finalization.grpcFinalization.FinalizationServiceGrpc

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.logging.Logger

// 패키지 경로에 맞춰 import (가정)
import managers.{RegistrationManager, SamplingManager, ShuffleManager}
import services.{RegistrationService, SamplingService, ShuffleWorkerService}
import repositories.{GrpcRegisterRepository, SamplingRepository, GrpcShuffleRepository}
import finalization.{FinalizationManager, FinalizationRepository, FinalizationService}

object MasterApp {
  def main(args: Array[String]): Unit = {
    // 1. 입력값 파싱 (워커 수)
    if (args.length < 1) {
      //println("Usage: MasterApp <worker-count>")
      sys.exit(1)
    }

    val workerCount = args(0).toInt
    val port = 5001

    //println(s"=== Initializing Master Server ===")
    //println(s"Port: $port")
    //println(s"Expected Worker Count: $workerCount")

    // 2. Repository 생성 (gRPC Service 구현체)
    val regRepo = new GrpcRegisterRepository(workerCount)
    val sampRepo = new SamplingRepository()
    val shuffleRepo = new GrpcShuffleRepository()
    val finalizationRepo = new FinalizationRepository(workerCount)

    // 3. Service 생성 (Repository + workerCount 주입)
    val regService = new RegistrationService(regRepo)
    val sampService = new SamplingService(sampRepo, workerCount)
    val shuffleService = new ShuffleWorkerService(shuffleRepo, regService, workerCount)
    val finalizationService = new FinalizationService(finalizationRepo)

    // 4. Manager 생성 (Service 주입)
    val regManager = new RegistrationManager(regService)
    val sampManager = new SamplingManager(sampService)
    val shuffleManager = new ShuffleManager(shuffleService)
    val finalizationManager = new FinalizationManager(finalizationService)

    // 5. Manager 로직 시작 (Non-blocking)
    //println("Starting managers...")
    regManager.start()       // 등록 대기 시작
    sampManager.start()    // 샘플링/파티셔닝 준비
    shuffleManager.shuffle()    // 셔플/소트 준비

    // 6. gRPC 서버 빌드 및 시작
    val server = NettyServerBuilder.forPort(port)
      .addService(RegisterServiceGrpc.bindService(regRepo, global))
      .addService(SamplingServiceGrpc.bindService(sampRepo, global))
      .addService(ShuffleControlServiceGrpc.bindService(shuffleRepo, global))
      .addService(FinalizationServiceGrpc.bindService(finalizationRepo, global))
      .permitKeepAliveTime(10, TimeUnit.SECONDS)
      .permitKeepAliveWithoutCalls(true)
      .build()

    server.start()
    //println(s"Master Server started successfully on port $port")

    finalizationManager.start {
      server.shutdown
      try
        server.awaitTermination(30, TimeUnit.SECONDS)
      catch {
        case ex: InterruptedException =>
      }
      server.shutdownNow()
      return
    }

    // 7. 서버 종료 전까지 메인 스레드 대기
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        //println("*** shutting down gRPC server since JVM is shutting down")
        server.shutdown()
        //println("*** server shut down")
      }
    })

    server.awaitTermination()
  }
}