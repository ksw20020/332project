package repositories

import io.grpc.{ManagedChannelBuilder, ServerBuilder}
import io.grpc.stub.StreamObserver
import shuffle.control.grpcShuffle.*

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, Future, Promise}

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
  @volatile private var server: io.grpc.Server = _
  private val getFirstRequest: AtomicBoolean = AtomicBoolean(false)
  private val isSendingDone: AtomicBoolean = AtomicBoolean(false)

  /** server or client */
  def start(role: WorkerRole, port: Int, host: String): Future[Unit] = {
    val p = Promise[Unit]
    clientStream = null
    serverStream = null
    getFirstRequest.set(false)
    isSendingDone.set(false)
    role match {
      case WorkerRole.Server =>
        startServer(port, p)

      case WorkerRole.Client =>
        connectToPeer(host, port, p)
    }
    p.future
  }

  /**
   * Start gRPC server
   */
  private def startServer(port: Int, promise: Promise[Unit]): Unit = {
    val serviceImpl = new WorkerExchangerGrpc.WorkerExchanger {
      override def exchange(
                             responseObserver: StreamObserver[ExchangeMsg]
                           ): StreamObserver[ExchangeMsg] = {

        serverStream = responseObserver
        createExchangeStream(promise)
      }
    }

    server = ServerBuilder
      .forPort(port)
      .addService(WorkerExchangerGrpc.bindService(serviceImpl, ec))
      .build()
      .start()

    println(s"[WorkerNode] Server started on $port")
  }

  /**
   * Connect to peer worker (client mode)
   */
  private def connectToPeer(peerHost: String, peerPort: Int, promise: Promise[Unit]): Unit = {
    val channel = ManagedChannelBuilder
      .forAddress(peerHost, peerPort)
      .usePlaintext()
      .build()

    val stub = WorkerExchangerGrpc.stub(channel)

    clientStream = stub.exchange(createExchangeStream(promise))

    println(s"[WorkerNode] Connected to peer $peerHost:$peerPort")
    sendReady()
  }

  /**
   * Unified stream handler (server ↔ client identical logic)
   */
  private def createExchangeStream(promise: Promise[Unit])
  : StreamObserver[ExchangeMsg] = {

    new StreamObserver[ExchangeMsg] {

      override def onNext(msg: ExchangeMsg): Unit = msg.msg match {
        case ExchangeMsg.Msg.Batch(batch) =>
          handleRecordBatch(batch)

        case ExchangeMsg.Msg.Ready(ready) =>
          handleReady()

        case ExchangeMsg.Msg.Done(done) => 
          handleDone()
      }

      override def onError(t: Throwable): Unit = {
        println(s"[Stream] Error: ${t.getMessage}")
      }

      override def onCompleted(): Unit = {
        println("[Stream] Completed")
        sendComplete()
        promise.trySuccess(())
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
    
    f.foreach { rb =>
      if (rb.records.nonEmpty) {
        sendBatch(rb)
      } else {
        isSendingDone.set(true)
        sendDone()
      }
    }
  }
  
  private def handleDone(): Unit = {
    if (isSendingDone.get()) {
      sendComplete()
    }
  }

  /** Send READY message */
  private def sendReady(): Unit = {
    val ready = ExchangeMsg(
      ExchangeMsg.Msg.Ready(Ready(ok = true))
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
  
  private def sendDone(): Unit = {
    val ready = ExchangeMsg(
      ExchangeMsg.Msg.Done(Done(done = true))
    )

    sendMessage(ready)
  }

  private def sendComplete(): Unit = {
    if (clientStream != null) clientStream.onCompleted()
    if (serverStream != null) serverStream.onCompleted()
    shutdown()
    clientStream = null
    serverStream = null
  }

  private def sendMessage(msg: ExchangeMsg): Unit = {
    if (clientStream != null) clientStream.onNext(msg)
    if (serverStream != null) serverStream.onNext(msg)
  }
  
  private def shutdown(): Unit = {
    if (serverStream != null && server != null)
      server.shutdown()
  }
}
