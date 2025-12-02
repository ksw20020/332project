package finalization.worker

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WorkerFinalizationManager(
    finalizationService: WorkerFinalizationService
) {

  def startFinalization(): Future[Unit] = {
    println("[Worker] Reporting finalize-ready to master...")
    finalizationService.sendFinalizePrepared().flatMap { _ =>
      println("[Worker] Waiting finalize signal from master...")
      finalizationService.waitFinalizeSignal()
    }.map { _ =>
      println("[Worker] Finalize signal received. Worker shutting down.")
    }
  }
}
