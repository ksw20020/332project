package repositories

import register.grpcRegister._
import io.grpc.ManagedChannel

/** Worker → Master RPC 호출을 Repository가 직접 담당 */
class GrpcRegisterRepository(channel: ManagedChannel) {
  private val stub = RegisterServiceGrpc.blockingStub(channel)

  def register(ip: String, port: Int): RegisterResponse = {
    val req = RegisterRequest(ip = ip, port = port)
    stub.grpcRegister(req)
  }
}
