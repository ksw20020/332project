package managers

import services.SamplingService

import scala.concurrent._

class SamplingManager(service: SamplingService) {
  def start(): Future[Unit] = {
    service.start()
  }
}
