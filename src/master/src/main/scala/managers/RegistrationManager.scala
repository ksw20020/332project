package managers

import services.RegistrationService

import scala.concurrent.Future

class RegistrationManager(service: RegistrationService) {
  def start(): Unit = {
    service.onWorkerRegistered((_, _, _, _) => ())
  }
  
  def getRegisteredWorkers: Map[Int, (String, Int)] = service.getRegisteredWorkers
}
