package repositories

import io.grpc.{ManagedChannelBuilder, ServerBuilder}
import io.grpc.stub.StreamObserver
import shuffle.control.grpcShuffle.*

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, Future}

sealed trait WorkerRole
object WorkerRole {
  case object Server extends WorkerRole
  case object Client extends WorkerRole
}

class GrpcShuffleWorkerRepository(
                  onReceiveBatch: (RecordBatch) => Future[Unit],
                  onReceiveReady: () => Future[RecordBatch]
                )(implicit ec: ExecutionContext) {

  @volatile private var clientStream: StreamObserver[ExchangeMsg] = _
  @volatile private var serverStream: StreamObserver[ExchangeMsg] = _
  private val getFirstRequest: AtomicBoolean = AtomicBoolean(false)

  /** server or client */
  def start(role: WorkerRole, port: Int, host: String): Unit = {
    clientStream = null
    serverStream = null
    getFirstRequest.set(false)
    role match {
      case WorkerRole.Server =>
        startServer(port)

      case WorkerRole.Client =>
        connectToPeer(host, port)
    }
  }

  /**
   * Start gRPC server
   */
  private def startServer(port: Int): Unit = {
    val serviceImpl = new WorkerExchangerGrpc.WorkerExchanger {
      override def exchange(
                             responseObserver: StreamObserver[ExchangeMsg]
                           ): StreamObserver[ExchangeMsg] = {

        serverStream = responseObserver
        createExchangeStream(isInboundFromClient = true)
      }
    }

    ServerBuilder
      .forPort(port)
      .addService(WorkerExchangerGrpc.bindService(serviceImpl, ec))
      .build()
      .start()

    println(s"[WorkerNode] Server started on $port")
  }

  /**
   * Connect to peer worker (client mode)
   */
  private def connectToPeer(peerHost: String, peerPort: Int): Unit = {
    val channel = ManagedChannelBuilder
      .forAddress(peerHost, peerPort)
      .usePlaintext()
      .build()

    val stub = WorkerExchangerGrpc.stub(channel)

    clientStream = stub.exchange(createExchangeStream(isInboundFromClient = false))

    println(s"[WorkerNode] Connected to peer $peerHost:$peerPort")
    sendReady()
  }

  /**
   * Unified stream handler (server ↔ client identical logic)
   */
  private def createExchangeStream(isInboundFromClient: Boolean)
  : StreamObserver[ExchangeMsg] = {

    new StreamObserver[ExchangeMsg] {

      override def onNext(msg: ExchangeMsg): Unit = msg.msg match {
        case ExchangeMsg.Msg.Batch(batch) =>
          handleRecordBatch(batch)

        case ExchangeMsg.Msg.Ready(ready) =>
          handleReady()
      }

      override def onError(t: Throwable): Unit = {
        println(s"[Stream] Error: ${t.getMessage}")
      }

      override def onCompleted(): Unit = {
        println("[Stream] Completed")
        sendComplete()
      }
    }
  }

  /** Process RecordBatch */
  private def handleRecordBatch(rb: RecordBatch): Unit = {
    val f = onReceiveBatch(rb)

    f.onComplete { _ => sendReady() }
  }

  private def handleReady(): Unit = {
    if (!getFirstRequest.getAndSet(true)) {
      sendReady()
    }
    val f = onReceiveReady()
    
    f.onComplete { _ => sendBatch(_)}
  }

  /** Send READY message */
  private def sendReady(): Unit = {
    val ready = ExchangeMsg(
      ExchangeMsg.Msg.Ready(Ready.defaultInstance)
    )

    sendMessage(ready)
  }

  /** Send RecordBatch */
  private def sendBatch(rb: RecordBatch): Unit = {
    val msg = ExchangeMsg(
      ExchangeMsg.Msg.Batch(rb)
    )
    sendMessage(msg)
  }

  private def sendComplete(): Unit = {
    if (clientStream != null) clientStream.onCompleted()
    if (serverStream != null) serverStream.onCompleted()
  }

  private def sendMessage(msg: ExchangeMsg): Unit = {
    if (clientStream != null) clientStream.onNext(msg)
    if (serverStream != null) serverStream.onNext(msg)
  }
}
