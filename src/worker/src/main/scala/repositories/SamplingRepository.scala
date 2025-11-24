package repositories

import io.grpc.ManagedChannel
import sampling.grpcSampling.*
import com.google.protobuf.ByteString
import io.grpc.stub.StreamObserver
import models.{Record, RecordKey}

import scala.concurrent.{ExecutionContext, Future}

/** Worker → Master 샘플링/파티션용 RPC 클라이언트 */
class SamplingRepository(
                          channel: ManagedChannel,
                          workerId: Int,
                          onReceiveResult: Seq[ByteString] => Unit,
                        ) {

  private val stub = SamplingServiceGrpc.stub(channel)

  private val responseObserver = new StreamObserver[SamplingMsg] {
    override def onNext(msg: SamplingMsg): Unit = {
      msg.payload match {
        case SamplingMsg.Payload.Result(result) =>
          onReceiveResult(result.pivots)
          requestObserver.onCompleted()

        case _ =>
          println(s"Worker $workerId: Unknown message received")
      }
    }

    override def onError(t: Throwable): Unit =
      println(s"Worker $workerId: Stream error: ${t.getMessage}")

    override def onCompleted(): Unit = {
      println(s"Worker $workerId: Stream completed by master")
    }
  }

  private lazy val requestObserver: StreamObserver[SamplingMsg] =
    stub.grpcSampling(responseObserver)

  def sendSamplingRequest(samples: Seq[RecordKey]): Unit = {
    val msg = SamplingMsg(
      payload = SamplingMsg.Payload.Request(
        SamplingRequest(
          workerId = workerId,
          samples = samples.map(record => ByteString.copyFrom(record.key))
        )
      )
    )
    requestObserver.onNext(msg)
  }
}
