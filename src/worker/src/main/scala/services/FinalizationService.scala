package finalization.worker

import finalization.grpcFinalization._
import io.grpc.ManagedChannel
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

class WorkerFinalizationService(
    channel: ManagedChannel,
    workerId: Int
) {

  private val stub = FinalizationServiceGrpc.stub(channel)
  private val finalizePromise = Promise[Unit]()

  def sendFinalizePrepared(): Future[Unit] = {
    val req = WorkerFinalizeRequest(workerId = workerId)
    stub.reportFinalize(req).map(_ => ())
  }

  def waitFinalizeSignal(): Future[Unit] =
    finalizePromise.future

  def onFinalizeSignal(): Unit =
    finalizePromise.trySuccess(())
}
