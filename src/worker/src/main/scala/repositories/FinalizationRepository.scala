package finalization.worker

import finalization.grpcFinalization._
import io.grpc.stub.StreamObserver

class WorkerFinalizationRepository(workerId: Int) {

  def buildRequest(): WorkerFinalizeRequest =
    WorkerFinalizeRequest(workerId = workerId)
}
