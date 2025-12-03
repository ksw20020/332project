package finalization

import finalization.grpcFinalization._
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

class FinalizationRepository(workerCount: Int)
    extends FinalizationServiceGrpc.FinalizationService {

  // 각 worker의 "finalize 준비 완료" 플래그
  private val finalizeFlags = Array.fill(workerCount)(false)
  private val allDonePromise = Promise[Unit]()

  // Master 입장에서 "모든 워커 준비 완료"를 기다릴 Future
  def allWorkersReady: Future[Unit] = allDonePromise.future

  // Worker → Master : "나 finalize 준비됨"
  override def reportFinalize(
      req: WorkerFinalizeRequest
  ): Future[WorkerFinalizeResponse] = {

    val wid = req.workerId - 1
    var allReady = false

    this.synchronized {
      if (wid >= 0 && wid < workerCount) {
        finalizeFlags(wid) = true
        if (!finalizeFlags.contains(false)) {
          allReady = true
        }
      }
    }

    if (allReady) {
      // 한 번만 성공하도록 시도
      allDonePromise.trySuccess(())
    }

    Future.successful(WorkerFinalizeResponse(ok = true))
  }

  // Worker → Master : "모두 준비될 때까지 기다렸다가, 준비되면 ok 돌려줘"
  override def sendFinalizeSignal(
      req: FinalizeSignalRequest
  ): Future[FinalizeSignalResponse] = {
    allWorkersReady.map { _ =>
      FinalizeSignalResponse(ok = true)
    }
  }
}
