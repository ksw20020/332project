package services

import repositories.RegistrationRepository

class WorkerRegistrationService(repo: RegistrationRepository) {

  /** Worker → Master 로 등록 요청 */
  def register(ip: String, port: Int): (Int, Int) = {
    val res = repo.register(ip, port)
    (res.workerId, res.workerCount)
  }
}
