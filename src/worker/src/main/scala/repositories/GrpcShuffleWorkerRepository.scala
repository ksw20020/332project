package repositories

import io.grpc.{ManagedChannel, ManagedChannelBuilder, ServerBuilder, StatusRuntimeException}
import io.grpc.stub.StreamObserver
import shuffle.control.grpcShuffle.*

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}

sealed trait WorkerRole
object WorkerRole {
  case object Server extends WorkerRole
  case object Client extends WorkerRole
}

class GrpcShuffleWorkerRepository(
                                   onReceiveBatch: RecordBatch => Future[Unit],
                                   onReceiveReady: () => Future[RecordBatch]
                                 )(implicit ec: ExecutionContext) {

  @volatile private var clientStream: StreamObserver[ExchangeMsg] = _
  @volatile private var clientChannel: ManagedChannel = _
  @volatile private var serverStream: StreamObserver[ExchangeMsg] = _
  @volatile private var server: io.grpc.Server = _
  private val getFirstRequest: AtomicBoolean = new AtomicBoolean(false)
  private val isSendingDone: AtomicBoolean = new AtomicBoolean(false)

  /** server or client */
  def start(role: WorkerRole, port: Int, host: String): Future[Unit] = {
    val p = Promise[Unit]()
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
    val serviceImpl2 = new HealthCheckServiceGrpc.HealthCheckService {
      override def ping(request: HealthCheckRequest): Future[HealthCheckResponse] = {
        Future {
          HealthCheckResponse()
        }
      }
    }

    server = ServerBuilder
      .forPort(port)
      .addService(WorkerExchangerGrpc.bindService(serviceImpl, ec))
      .addService(HealthCheckServiceGrpc.bindService(serviceImpl2, ec))
      .build()
      .start()

    println(s"[WorkerNode] Server started on $port")
  }

  /**
   * Connect to peer worker (client mode)
   */
  private def connectToPeer(peerHost: String, peerPort: Int, promise: Promise[Unit]): Unit = {
    clientChannel = ManagedChannelBuilder
      .forAddress(peerHost, peerPort)
      .usePlaintext()
      .build()

    val stub = WorkerExchangerGrpc.stub(clientChannel)
    val stubs = HealthCheckServiceGrpc.blockingStub(clientChannel)

    println(s"[WorkerNode] Connected to peer $peerHost:$peerPort")
    @tailrec
    def retrySendReady(): Unit = {
      try {
        val blockingStub = HealthCheckServiceGrpc.blockingStub(clientChannel)

        blockingStub.ping(HealthCheckRequest())

      } catch {
        case e: StatusRuntimeException =>
          println("retry")
          Thread.sleep(1000)
          retrySendReady()
      }
    }
    retrySendReady()

    clientStream = stub.exchange(createExchangeStream(promise))
    sendReady()
  }

  /**
   * Unified stream handler (server ↔ client identical logic)
   */
  private def createExchangeStream(promise: Promise[Unit])
  : StreamObserver[ExchangeMsg] = {

    new StreamObserver[ExchangeMsg] {

      override def onNext(msg: ExchangeMsg): Unit =
      try {
        msg.msg match {
          case ExchangeMsg.Msg.Batch(batch) =>
            handleRecordBatch(batch)

          case ExchangeMsg.Msg.Ready(ready) =>
            handleReady()

          case ExchangeMsg.Msg.Done(done) =>
            handleDone()
        }
      } catch {
        case e: Exception =>
          println(s"CRITICAL ERROR in onNext: ${e.getMessage}")
          e.printStackTrace()
      }

      override def onError(t: Throwable): Unit = {
        println(s"[Stream] Error: ${t.getMessage}")
        promise.tryFailure(t)
      }

      override def onCompleted(): Unit = {
        println("[Stream] Completed")
        sendComplete()
        shutdown()
        promise.trySuccess(())
      }
    }
  }

  /** Process RecordBatch */
  private def handleRecordBatch(rb: RecordBatch): Unit = {
    println("get batch")
    val f = onReceiveBatch(rb)

    f.onComplete { _ => sendReady() }
  }

  private def handleReady(): Unit = {
    println("get ready")
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
    println("get Done")
    if (isSendingDone.get()) {
      if (clientStream != null) {
        sendComplete()
      } else {
        sendDone()
      }
    }
  }

  /** Send READY message */
  private def sendReady(): Unit = {
    getFirstRequest.set(true)
    val ready = ExchangeMsg(
      ExchangeMsg.Msg.Ready(Ready(ok = true))
    )

    sendMessage(ready)
    println("sent ready")
  }

  /** Send RecordBatch */
  private def sendBatch(rb: RecordBatch): Unit = {
    val msg = ExchangeMsg(
      ExchangeMsg.Msg.Batch(rb)
    )
    sendMessage(msg)
    println("sent batch")
  }

  private def sendDone(): Unit = {
    val ready = ExchangeMsg(
      ExchangeMsg.Msg.Done(Done(done = true))
    )
    println("sent done")

    sendMessage(ready)
  }

  private def sendComplete(): Unit = {
    println("complete")
    if (clientStream != null) clientStream.onCompleted()
    clientStream = null
  }

  private def sendMessage(msg: ExchangeMsg): Unit = {
    this.synchronized {
      if (clientStream != null) clientStream.onNext(msg)
      if (serverStream != null) serverStream.onNext(msg)
    }
  }

  private def shutdown(): Unit = {
    if (serverStream != null && server != null) {
      serverStream.onCompleted()
      server.shutdown
      try
        server.awaitTermination(30, TimeUnit.SECONDS)
      catch {
        case ex: InterruptedException =>
      }
      server.shutdownNow()
    }
    if (clientChannel != null) {
      clientChannel.shutdown()
      try {
        clientChannel.awaitTermination(5, TimeUnit.SECONDS)
      } catch {
        case ex: InterruptedException =>
      }
      clientChannel.shutdownNow()
    }
  }
}
