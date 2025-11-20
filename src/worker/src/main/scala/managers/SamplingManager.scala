package managers

import models.PartitionRange
import services.SamplingService

import scala.concurrent.{Future, Promise}

class SamplingManager (
                        channel: io.grpc.ManagedChannel,
                        workerId: Int,
                        filePath: String,
) {
  private val service = SamplingService(
    filePath = filePath,
    channel = channel,
    workerId = workerId,
  )

  def startSampling(): Future[Seq[PartitionRange]] = {
    service.executeRound()
  }
}