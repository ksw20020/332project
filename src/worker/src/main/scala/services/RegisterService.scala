package services

import repositories.GrpcRegisterRepository

class WorkerRegistrationService(repo: GrpcRegisterRepository) {

  /** Worker → Master 로 등록 요청 */
  def register(ip: String, port: Int): (Int, Int) = {
    val res = repo.register(ip, port)
    (res.workerId, res.workerCount)
  }
}
