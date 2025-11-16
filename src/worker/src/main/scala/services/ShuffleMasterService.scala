package services

import repositories.GrpcShuffleMasterRepository

class ShuffleMasterService(
                            channel: io.grpc.ManagedChannel,
                            workerId: Int,
                            onStartRound: Int => Unit,
                          ) {
  private val repository = new GrpcShuffleMasterRepository(
    channel = channel,
    workerId = workerId,
    onStartRound = onStartRound,
  )

  def reportRoundDoneToMaster(roundId: Int): Unit = {
    repository.sendRoundDone(roundId)
  }
}
