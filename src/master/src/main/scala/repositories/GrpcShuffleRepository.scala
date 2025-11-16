package repositories

import shuffle.control.grpcShuffle.*
import io.grpc.stub.StreamObserver

import scala.collection.concurrent.TrieMap


class GrpcShuffleRepository extends ShuffleControlServiceGrpc.ShuffleControlService {

  private val workerStreams: TrieMap[Int, StreamObserver[ShuffleMsg]] = TrieMap.empty
  var onWorkerRoundDone: (Int, Int) => Unit = (_, _) => () // (workerId, roundId)
  var onWorkerDead: Int => Unit = _ => () // workerId

  override def grpcShuffleStreamControl(responseObserver: StreamObserver[ShuffleMsg]): StreamObserver[ShuffleMsg] = {
    new StreamObserver[ShuffleMsg] {
      override def onNext(msg: ShuffleMsg): Unit = {
        msg.payload match {
          case ShuffleMsg.Payload.Done(done) =>
            if (!workerStreams.contains(done.workerId)) {
              registerWorkerStream(done.workerId, responseObserver)
            }
            onWorkerRoundDone(done.workerId, done.round)
          case _ =>
            println("Unknown ShuffleMsg payload received")
        }
      }

      override def onError(t: Throwable): Unit = {
        println(s"Stream error: ${t.getMessage}")

        findWorkerId(responseObserver).foreach { deadWorkerId =>
          workerStreams.remove(deadWorkerId)
          onWorkerDead(deadWorkerId)
        }
      }


      override def onCompleted(): Unit ={
        println("Stream completed by remote worker")

        findWorkerId(responseObserver).foreach { deadWorkerId =>
          workerStreams.remove(deadWorkerId)
        }
      }
    }
  }

  def broadcastNextRound(roundId: Int): Unit = {
    val startMsg = ShuffleMsg(
      payload = ShuffleMsg.Payload.Start(RoundStart(roundId))
    )
    workerStreams.foreach { case (workerId, observer) =>
      observer onNext startMsg
    }
  }

  def sendNextRound(workerId: Int, roundId: Int): Unit = {
    val startMsg = ShuffleMsg(
      payload = ShuffleMsg.Payload.Start(RoundStart(roundId))
    )
    workerStreams.get(workerId).foreach(_.onNext(startMsg))
  }

  private def registerWorkerStream(workerId: Int, stream: StreamObserver[ShuffleMsg]): Unit =
    workerStreams.put(workerId, stream)

  private def findWorkerId(value: StreamObserver[ShuffleMsg]): Option[Int] = {
    workerStreams.find(_._2 eq value).map(_._1)
  }
}