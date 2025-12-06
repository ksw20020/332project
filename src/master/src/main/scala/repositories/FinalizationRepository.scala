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
  override def reportFinalize(
                               req: WorkerFinalizeRequest
                             ): Future[WorkerFinalizeResponse] = {

    val wid = req.workerId - 1

    this.synchronized {
      if (wid >= 0 && wid < workerCount) {
        finalizeFlags(wid) = true // Promise 성공 로직 제거
      }
    }
    // Master는 플래그만 업데이트하고 바로 응답
    Future.successful(WorkerFinalizeResponse(ok = true))
  }

  // Worker → Master : "모두 준비될 때까지 기다렸다가, 준비되면 ok 돌려줘"
  override def sendFinalizeSignal(
                                   req: FinalizeSignalRequest
                                 ): Future[FinalizeSignalResponse] = {

    // [변경점] Promise 성공 조건을 sendFinalizeSignal 내부에서 검사
    val allReady = this.synchronized {
      !finalizeFlags.contains(false)
    }

    if (allReady) {
      // 모든 워커가 reportFinalize를 완료했다면,
      // 이 시점(즉, 마지막 워커가 sendFinalizeSignal을 요청했을 때)에 Promise를 성공시킴.
      allDonePromise.trySuccess(())
    }

    // Promise가 성공할 때까지 대기
    allWorkersReady.map { _ =>
      FinalizeSignalResponse(ok = true)
    }
  }
}