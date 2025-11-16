package services

import managers.SamplingCoordinator
import shuffle.control.grpcShuffle._
import com.google.protobuf.empty.Empty

import scala.concurrent.{ExecutionContext, Future}

/** 샘플링 전용 gRPC 서비스 (Master 측).
  *
  *  - Worker → Master : SendSample()
  *  - Worker ← Master : GetPartitionInfo()
  */
class SamplingService(
    coordinator: SamplingCoordinator
)(implicit ec: ExecutionContext)
    extends SamplingServiceGrpc.SamplingService {

  /** Worker가 보낸 샘플을 Master가 수집 */
  override def sendSample(req: SampleRequest): Future[SampleAck] = {
    // req.samples: Seq[ByteString]
    coordinator.addSample(req.workerId, req.samples)
    Future.successful(SampleAck(ok = true))
  }

  /** 지금까지 모은 샘플로 피벗 계산 후 Worker에게 전달 */
  override def getPartitionInfo(req: Empty): Future[PartitionInfo] = {
    val pivots = coordinator.computePivots()
    Future.successful(PartitionInfo(pivots = pivots))
  }
}
