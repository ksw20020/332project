package repositories

import io.grpc.ManagedChannel
import sampling.grpcSampling._
import com.google.protobuf.ByteString
import io.grpc.stub.StreamObserver

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

class SamplingRepository extends SamplingServiceGrpc.SamplingService {

  private val workerStreams: TrieMap[Int, StreamObserver[SamplingMsg]] = TrieMap.empty
  var onWorkerRequest: (Int, Seq[ByteString]) => Unit = (_, _) => () // (workerId, samples)

  override def grpcSampling(responseObserver: StreamObserver[SamplingMsg]): StreamObserver[SamplingMsg] = {
    new StreamObserver[SamplingMsg] {
      override def onNext(msg: SamplingMsg): Unit = {
        msg.payload match {
          case SamplingMsg.Payload.Request(req) =>
            registerWorkerStream(req.workerId, responseObserver)
            onWorkerRequest(req.workerId, req.samples)
            
          case _ =>
            //println("Unknown SamplingMsg payload received")
        }
      }

      override def onError(t: Throwable): Unit = {
        //println(s"Stream error: ${t.getMessage}")
      }

      override def onCompleted(): Unit ={
        //println("Sampling Stream completed by remote worker")
        responseObserver.onCompleted()
      }
    }
  }
  
  def sendSamplingResult(result: Seq[ByteString]): Unit = {
    val msg = SamplingMsg(
      payload = SamplingMsg.Payload.Result(
        SamplingResult(pivots = result)
      )
    )
    workerStreams.foreach { case (workerId, observer) =>
      observer onNext msg
    }
    workerStreams.clear()
  }

  private def registerWorkerStream(workerId: Int, stream: StreamObserver[SamplingMsg]): Unit =
    workerStreams.put(workerId, stream)
}

