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

  def reportFinalizeReady(): Future[Unit] = {
    val req = WorkerFinalizeRequest(workerId = workerId)
    stub.reportFinalize(req).map(_ => ())
  }

  // Master가 보내는 FinalizeSignal RPC의 응답이 도착할 때 호출됨
  def receiveFinalizeSignal(): Future[Unit] = {
    val req = FinalizeSignalRequest(ok = true)
    stub.sendFinalizeSignal(req).map { _ =>
      finalizePromise.trySuccess(())
    }.map(_ => ())
  }

  def waitFinalizeSignal(): Future[Unit] =
    finalizePromise.future
}

