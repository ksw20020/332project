package services

import repositories.GrpcShuffleRepository

class ShuffleMasterService(
                            channel: io.grpc.ManagedChannel,
                            workerId: Int,
                            onStartRound: Int => Unit,
                          ) {
  private val repository = new GrpcShuffleRepository(
    channel = channel,
    workerId = workerId,
    onStartRound = onStartRound,
  )

  def reportRoundDoneToMaster(roundId: Int): Unit = {
    repository.sendRoundDone(roundId)
  }
}
