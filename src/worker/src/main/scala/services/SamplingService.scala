package services

import com.google.protobuf.ByteString
import managers.Sampler
import repositories.SamplingRepository

import scala.concurrent.{ExecutionContext, Future}

/** Worker에서 실제 라운드별 작업을 수행하는 서비스.
  *
  *  - Round 1: 로컬 데이터 정렬 + 샘플 추출 + Master로 전송
  *  - Round 2: Master에서 pivot 받아서 파티션/셔플 (TODO)
  */
class SamplingService(
    workerId: Int,
    samplingRepo: SamplingRepository,
    sampler: Sampler
)(implicit ec: ExecutionContext) {

  /** ShuffleManager에서 roundId를 넘겨 호출하는 메인 엔트리 */
  def executeRound(roundId: Int): Future[Unit] = roundId match {
    case 1 => runSamplingRound()
    case 2 => runPartitionRound()
    case _ => Future.unit
  }

  /** Round 1: 샘플링 라운드 */
  private def runSamplingRound(): Future[Unit] = {
    Future {
      val local = loadLocalData()
      val sorted = sortLocalData(local)

      val keysOnly = sorted.map(_.take(10))   // 앞 10바이트가 key라 가정
      val samples = sampler.stratifiedSample(keysOnly)

      val byteStrings = samples.map(s => ByteString.copyFrom(s))
      samplingRepo.sendSample(workerId, byteStrings)
    }.flatten
  }


  /** Round 2: pivot 정보를 받아 파티션/셔플 수행 */
  private def runPartitionRound(): Future[Unit] = {
    for {
      pivots <- samplingRepo.fetchPartitionInfo()
      _      <- Future {
        applyPivotsAndShuffle(pivots)
      }
    } yield ()
  }

  // -----------------------------
  // 아래 함수들은 네 환경에 맞게 채워야 하는 부분
  // -----------------------------

  /** Gensort로 생성한 로컬 데이터를 읽어오는 부분
    *   - 파일 경로, 포맷 등은 과제/환경에 맞게 구현
    */
  private def loadLocalData(): Seq[Array[Byte]] = {
    // TODO: 로컬 입력 파일에서 레코드 읽어서 Seq[Array[Byte]] 로 반환
    // 예: 각 레코드가 100바이트(키 10 + value 90)라면 그 단위로 잘라서 읽기
    Seq.empty
  }

  /** 로컬 데이터 정렬 로직
    *   - 기본 구현은 키를 String으로 보고 사전식 정렬
    *   - 필요하면 훨씬 최적화된 비교 로직으로 교체 가능
    */
  private def sortLocalData(sorted: Seq[Array[Byte]]): Seq[Array[Byte]] = {
    // TODO: 키 부분(예: 앞 10바이트)만 잘라서 비교하는 쪽으로 최적화 가능
    sorted.sortBy(rec => new String(rec.take(10), "US-ASCII"))
  }

  /** Master에서 받은 pivots 기준으로
    *   - 로컬 데이터를 여러 파티션으로 나누고
    *   - 각 파티션을 대상 Worker에게 전송하는 로직
    */
  private def applyPivotsAndShuffle(pivots: Seq[ByteString]): Unit = {
    // TODO:
    //  1) loadLocalData() or 캐시된 로컬 정렬 데이터를 가져온다
    //  2) pivots 기준으로 구간 나누기
    //  3) 각 구간을 적절한 Worker에게 전송 (gRPC / 파일 등)
  }
}
