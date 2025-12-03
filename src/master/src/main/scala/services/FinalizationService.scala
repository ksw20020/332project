package finalization

import finalization.grpcFinalization._
import io.grpc.ManagedChannel
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinalizationService(
    repository: FinalizationRepository,
) {

  def waitAllWorkersReady(): Future[Unit] =
    repository.allWorkersReady

}
