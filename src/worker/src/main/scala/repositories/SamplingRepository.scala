def sendSample(workerId: Int, samples: Seq[Array[Byte]]): Unit = {
  val req = SampleRequest(
    workerId = workerId,
    samples = samples
  )
  stub.sendSample(req)
}

def receivePartitionInfo(): Seq[Array[Byte]] = {
  val info = blockingStub.getPartitionInfo(Empty())
  info.pivots
}
