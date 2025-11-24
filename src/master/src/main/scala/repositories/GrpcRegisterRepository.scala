package repositories

import register.grpcRegister._
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** Master가 Worker 등록을 직접 받는 gRPC 서버 Repository */
class GrpcRegisterRepository(expectedWorkerCount: Int)
  extends RegisterServiceGrpc.RegisterService {

  private val workers = TrieMap.empty[Int, (String, Int)]
  private val ipToId = TrieMap.empty[String, Int]               // ip → workerId
  @volatile private var nextId: Int = 1

  /** 콜백 → Manager 또는 Main이 설정 */
  var onRegistered: (Int, String, Int, Int) => Unit =
    (_, _, _, _) => ()

  // 수정: unary RPC 시그니처는 Future 반환이어야 함
  override def register(req: RegisterRequest): Future[RegisterResponse] = Future {

    val ip   = req.ip
    val port = req.port

    val (assignedId, total) = this.synchronized {

      // 1. 기존 IP인지 확인
      ipToId.get(ip) match {
        case Some(existingId) =>
          // 기존 worker가 다시 들어온 경우 → 동일 ID 반환
          workers.put(existingId, (ip, port))
          (existingId, expectedWorkerCount)

        case None =>
          // 신규 worker
          if (nextId > expectedWorkerCount) {
            // 모든 worker가 이미 등록됨 → 등록 거부
            (0, expectedWorkerCount)
          } else {
            val id = nextId
            nextId += 1
            ipToId.put(ip, id)
            workers.put(id, (ip, port))
            (id, expectedWorkerCount)
          }
      }
    
    }

    onRegistered(assignedId, ip, port, total)

    RegisterResponse(
      workerId = assignedId,
      workerCount = total
    )
  }

  def registeredWorkers: Map[Int,(String,Int)] = workers.toMap
}
