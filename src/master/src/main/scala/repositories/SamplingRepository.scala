package repositories

import io.grpc.ManagedChannel
import sampling.grpcSampling._
import com.google.protobuf.ByteString

import scala.concurrent.{ExecutionContext, Future}

/** Worker → Master 샘플링/파티션용 RPC 클라이언트 */
class SamplingRepository(
    channel: ManagedChannel
)(implicit ec: ExecutionContext) {

  private val stub = SamplingServiceGrpc.stub(channel)

  /** Worker가 뽑은 샘플들을 Master로 보내는 함수 */
  def sendSample(workerId: Int, samples: Seq[ByteString]): Future[Unit] = {
    val req = SampleRequest(
      workerId = workerId,
      samples = samples
    )
    stub.sendSample(req).map(_ => ())
  }

  /** Master가 계산한 pivot(PartitionInfo)을 가져오는 함수 */
  def fetchPartitionInfo(): Future[Seq[ByteString]] = {
    stub.getPartitionInfo(GetPartitionRequest()).map(_.pivots)
  }
}
