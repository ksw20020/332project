package managers

import repositories.{GrpcShuffleMasterRepository, SamplingRepository}
import services.{ShuffleMasterService, ShuffleWorkerService}
import scala.concurrent.ExecutionContext.Implicits.global

class ShuffleManager(
                      channel: io.grpc.ManagedChannel,
                      workerId: Int
                    ) {
  private val masterService = ShuffleMasterService(
    channel = channel,
    workerId = workerId,
    onStartRound = executeRound,
  )

  private val samplingRepository = new SamplingRepository(channel)
  private val sampler            = new Sampler()
  private val workerService = new ShuffleWorkerService(workerId, samplingRepository, sampler)

  def startShuffle(): Unit = {
    masterService.reportRoundDoneToMaster(0)
  }

  private def executeRound(roundId: Int): Unit = {
    workerService.executeRound(roundId).foreach {
      masterService.reportRoundDoneToMaster(roundId)
    }
  }
}
