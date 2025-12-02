package finalization

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinalizationManager(
    finalizationService: FinalizationService
) {

  def startFinalization(): Future[Unit] = {
    println("[Master] Finalization stage started. Waiting for all workers...")

    finalizationService.waitAllWorkersReady().flatMap { _ =>
      println("[Master] All workers reported ready for finalization.")
      println("[Master] Sending finalize signal...")
      finalizationService.sendFinalizeSignalToAll()
    }
  }
}
