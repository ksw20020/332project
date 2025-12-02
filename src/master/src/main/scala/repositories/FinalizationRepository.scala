package finalization

import finalization.grpcFinalization._
import io.grpc.stub.StreamObserver
import scala.concurrent.Promise

class FinalizationRepository(workerCount: Int)
    extends FinalizationServiceGrpc.FinalizationServiceImplBase {

  private val finalizeFlags = Array.fill(workerCount)(false)
  private val allDonePromise = Promise[Unit]()

  def allWorkersReady = allDonePromise.future

  override def reportFinalize(
      req: WorkerFinalizeRequest,
      respObs: StreamObserver[WorkerFinalizeResponse]
  ): Unit = {
    val wid = req.workerId - 1
    var allReady = false

    this.synchronized {
      finalizeFlags(wid) = true
      if (!finalizeFlags.contains(false)) allReady = true
    }

    // unary 응답
    respObs.onNext(WorkerFinalizeResponse(ok = true))
    respObs.onCompleted()

    if (allReady)
      allDonePromise.trySuccess(())
  }
}
