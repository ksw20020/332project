package managers

import repositories.GrpcShuffleMasterRepository
import services.{SamplingService, ShuffleMasterService, ShuffleWorkerService}

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class ShuffleManager(
                      channel: io.grpc.ManagedChannel,
                      workerId: Int,
                      port: Int,
                      savePath: String,
                      workerCount: Int
                    ) {
  private val masterService = new ShuffleMasterService(
    channel = channel,
    workerId = workerId,
    onStartRound = executeRound,
  )
  private val workerService = new ShuffleWorkerService(
    workerId = workerId,
    port = port,
    savePath = savePath,
    workerCount = workerCount,
    checkShuffleComplete = () => shuffleCompleted.get()
  )

  private val p: Promise[Unit] = Promise[Unit]()
  private val shuffleCompleted: AtomicBoolean = new AtomicBoolean(false)

  def startShuffle(): Future[Unit] = {
    workerService.moveSelfDataToShuffling()
    masterService.reportRoundDoneToMaster(0)
    p.future
  }

  private def executeRound(roundId: Int, partnerIp: String, partnerPort: Int): Unit = {
    workerService.executeRound(roundId, partnerIp, partnerPort).foreach { _ =>
      masterService.reportRoundDoneToMaster(roundId)
      if (roundId == workerCount - 1) {
        shuffleCompleted.set(true)
        p.trySuccess(())
      }
    }
  }
}