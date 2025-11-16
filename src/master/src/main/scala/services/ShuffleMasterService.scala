class ShuffleMasterService(coordinator: SamplingCoordinator)
    extends ShuffleMasterServiceGrpc.ShuffleMasterService {

  override def sendSample(req: SampleRequest): Future[SampleAck] = {
    coordinator.addSample(req.workerId, req.samples)
    Future.successful(SampleAck(ok = true))
  }

  override def sendPartitionInfo(req: Empty): Future[PartitionInfo] = {
    val pivots = coordinator.computePivots()
    Future.successful(PartitionInfo(pivots))
  }
}