package managers

import services.ShuffleMasterService

import scala.concurrent.*
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

  private val workerService = ???

  def startShuffle(): Unit = {
    masterService.reportRoundDoneToMaster(0)
  }

  def startShuffleForDeadWorker(deadWorkerId: Int): Unit = {
    masterService.reportRoundDoneToMaster(0)
  }

  private def executeRound(roundId: Int): Unit = {
    workerService.executeRound(roundId).foreach {
      masterService.reportRoundDoneToMaster(roundId)
    }
  }
}
