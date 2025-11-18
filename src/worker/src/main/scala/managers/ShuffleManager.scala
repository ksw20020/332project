package managers

import repositories.GrpcShuffleMasterRepository
import services.{ShuffleMasterService, SamplingService}
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

  def startShuffle(): Unit = {
    masterService.reportRoundDoneToMaster(0)
  }

  private def executeRound(roundId: Int): Unit = {
    workerService.executeRound(roundId).foreach {
      masterService.reportRoundDoneToMaster(roundId)
    }
  }
}
