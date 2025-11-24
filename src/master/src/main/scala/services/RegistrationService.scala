package services

import repositories.GrpcRegisterRepository

/** Master가 RegistrationRepository를 감싸서 서비스 계층 제공 */
class RegistrationService(repo: GrpcRegisterRepository) {

  /** 새로운 worker가 등록될 때 호출되는 콜백을 설정 */
  def onWorkerRegistered(callback: (Int, String, Int, Int) => Unit): Unit =
    repo.onRegistered = callback

  /** 현재 등록된 worker 목록 조회 */
  def getRegisteredWorkers = repo.registeredWorkers
}

