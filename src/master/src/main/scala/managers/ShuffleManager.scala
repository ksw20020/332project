package managers

import services.ShuffleWorkerService

import scala.concurrent._

class ShuffleManager(service: ShuffleWorkerService) {
  def shuffle(): Future[Unit] = {
    service.start()
  }

  def shuffleForDeadWorker(deadWorkerId: Int): Future[Unit] = {
    service.start()
  }
}
