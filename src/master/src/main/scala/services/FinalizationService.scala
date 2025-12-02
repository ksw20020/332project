package finalization

import finalization.grpcFinalization._
import io.grpc.ManagedChannel
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinalizationService(
    repository: FinalizationRepository,
    workerChannels: Map[Int, ManagedChannel]
) {

  private val stubs = workerChannels.map { case (id, ch) =>
    id -> FinalizationServiceGrpc.stub(ch)
  }

  def waitAllWorkersReady(): Future[Unit] =
    repository.allWorkersReady

  def sendFinalizeSignalToAll(): Future[Unit] = {
    val req = FinalizeSignalRequest(ok = true)
    Future.sequence(
      stubs.values.map(_.sendFinalizeSignal(req))
    ).map(_ => ())
  }
}
