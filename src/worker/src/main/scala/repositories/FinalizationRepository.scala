package finalization.worker

import finalization.grpcFinalization._

class WorkerFinalizationRepository(workerId: Int) {
  def buildRequest(): WorkerFinalizeRequest =
    WorkerFinalizeRequest(workerId = workerId)
}
