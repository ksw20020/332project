package finalization

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinalizationManager(
    finalizationService: FinalizationService
) {
  def start(f: => Unit): Unit = {
    finalizationService.waitAllWorkersReady().foreach { _ =>
      f
    }
  }
}
