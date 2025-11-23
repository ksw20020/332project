package managers

import services.SamplingService

import scala.concurrent._

class SamplingManager(service: SamplingService) {
  def shuffle(): Future[Unit] = {
    service.start()
  }
}
