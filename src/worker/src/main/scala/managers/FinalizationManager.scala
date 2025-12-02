package finalization.worker

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WorkerFinalizationManager(
    service: WorkerFinalizationService
) {

  def start(): Future[Unit] = {
    println("[Worker] Reporting finalize-ready...")
    service.reportFinalizeReady().flatMap { _ =>
      println("[Worker] Waiting finalize signal...")
      service.receiveFinalizeSignal().flatMap(_ => service.waitFinalizeSignal())
    }.map { _ =>
      println("[Worker] Finalize signal received. Shutting down worker.")
    }
  }
}
