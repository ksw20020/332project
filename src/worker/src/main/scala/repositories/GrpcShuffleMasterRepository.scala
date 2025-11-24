package repositories

import io.grpc.stub.StreamObserver
import shuffle.control.grpcShuffle._

class GrpcShuffleMasterRepository(
                             channel: io.grpc.ManagedChannel,
                             workerId: Int,
                             onStartRound: (Int, String, Int) => Unit,
                           ) {
  private val stub = ShuffleControlServiceGrpc.stub(channel)

  private val responseObserver = new StreamObserver[ShuffleMsg] {
    override def onNext(msg: ShuffleMsg): Unit = {
      msg.payload match {
        case ShuffleMsg.Payload.Start(start) =>
          println(s"Worker $workerId: Start round ${start.round} with partner ${start.partnerIp}:${start.partnerPort}")

          // [변경] 마스터가 준 정보를 콜백으로 넘김
          onStartRound(start.round, start.partnerIp, start.partnerPort)

        case _ =>
          println(s"Worker $workerId: Unknown message received")
      }
    }

    override def onError(t: Throwable): Unit =
      println(s"Worker $workerId: Stream error: ${t.getMessage}")

    override def onCompleted(): Unit =
      println(s"Worker $workerId: Stream completed by master")
  }

  private lazy val requestObserver: StreamObserver[ShuffleMsg] =
    stub.grpcShuffleStreamControl(responseObserver)

  def sendRoundDone(roundId: Int): Unit = {
    val msg = ShuffleMsg(
      payload = ShuffleMsg.Payload.Done(
        RoundDone(round = roundId, workerId = workerId)
      )
    )
    this.synchronized {
      requestObserver.onNext(msg)
    }
  }

}