package repositories

import register.grpcRegister._
import io.grpc.stub.StreamObserver
import scala.collection.concurrent.TrieMap

/** Master가 Worker 등록을 직접 받는 gRPC 서버 Repository */
class GrpcRegisterRepository(expectedWorkerCount: Int)
  extends RegisterServiceGrpc.RegisterService {

  private val workers = TrieMap.empty[Int, (String, Int)]
  @volatile private var nextId: Int = 1

  /** 콜백 → Manager 또는 Main이 설정 */
  var onRegistered: (Int, String, Int, Int) => Unit =
    (_, _, _, _) => ()

  override def register(
      req: RegisterRequest,
      respObs: StreamObserver[RegisterResponse]
  ): Unit = {

    val (assignedId, total) = this.synchronized {
      if (nextId > expectedWorkerCount)
        (0, expectedWorkerCount)
      else {
        val id = nextId
        nextId += 1
        workers.put(id, (req.ip, req.port))
        (id, expectedWorkerCount)
      }
    }

    onRegistered(assignedId, req.ip, req.port, total)

    val resp = RegisterResponse(
      workerId = assignedId,
      workerCount = total
    )

    respObs.onNext(resp)
    respObs.onCompleted()
  }

  def registeredWorkers: Map[Int,(String,Int)] = workers.toMap
}
