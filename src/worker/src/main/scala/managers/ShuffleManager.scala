package managers

import repositories.GrpcShuffleMasterRepository
import services.{SamplingService, ShuffleMasterService, ShuffleWorkerService}

import scala.concurrent.ExecutionContext.Implicits.global

class ShuffleManager(
                      channel: io.grpc.ManagedChannel,
                      workerId: Int,
                      port: Int,
                      savePath: String
                    ) {
  private val masterService = ShuffleMasterService(
    channel = channel,
    workerId = workerId,
    onStartRound = executeRound,
  )
  private val workerService = ShuffleWorkerService(
    workerId = workerId,
    port = port,
    savePath = savePath
  )

  def startShuffle(): Unit = {
    masterService.reportRoundDoneToMaster(0)
  }

  private def executeRound(roundId: Int): Unit = {
    workerService.executeRound(roundId).foreach { _ =>
      masterService.reportRoundDoneToMaster(roundId)
    }
  }
}
