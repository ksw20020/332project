package finalization

import finalization.grpcFinalization._
import io.grpc.stub.StreamObserver

import scala.concurrent.Promise

class FinalizationRepository(workerCount: Int)
    extends FinalizationServiceGrpc.FinalizationServiceImplBase {

  private val finalizeFlags = Array.fill(workerCount)(false)
  private val allDonePromise = Promise[Unit]()

  def allWorkersReady: scala.concurrent.Future[Unit] =
    allDonePromise.future

  override def reportFinalize(
      req: WorkerFinalizeRequest,
      respObs: StreamObserver[WorkerFinalizeResponse]
  ): Unit = {
    val wid = req.workerId
    val idx = wid - 1
    var allReady = false

    this.synchronized {
      if (!finalizeFlags(idx)) {
        finalizeFlags(idx) = true
      }
      if (!finalizeFlags.contains(false)) allReady = true
    }

    respObs.onNext(WorkerFinalizeResponse(ok = true))
    respObs.onCompleted()

    if (allReady) allDonePromise.trySuccess(())
  }
}
