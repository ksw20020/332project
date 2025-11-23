package managers

import io.grpc.ManagedChannel
import services.WorkerRegistrationService
import repositories.RegistrationRepository

class RegistrationManager(
    channel: ManagedChannel,
    ip: String,
    port: Int
) {

  private val repo = new RegistrationRepository(channel)
  private val service = new WorkerRegistrationService(repo)

  def start(): (Int, Int) = {
    service.register(ip, port)
  }
}
