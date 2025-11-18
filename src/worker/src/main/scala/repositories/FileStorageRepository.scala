package repositories

import java.io._
import scala.util.Using

// 이전에 정의된 Record 클래스 (100바이트 레코드)
case class Record(bytes: Array[Byte]) {
  require(bytes.length == 100)
}

// FileStorageRepository 인터페이스 정의
trait FileStorageRepository {
  /**
    * 주어진 경로의 입력 블록 파일을 읽어 100바이트 단위의 Record 목록을 반환합니다.
    * @param path 입력 파일 경로
    * @return Record 객체의 목록
    */
  def readBlock(path: String): List[Record]

  /**
    * 주어진 데이터를 파일에 씁니다. PartitionService에서 임시 파일 쓰기에 사용됩니다.
    * @param path 저장할 파일 경로
    * @param recordBytes 100바이트 레코드의 Array[Byte]
    * @param append 기존 파일에 이어쓸지 여부 (Partitioning 시에는 true)
    */
  def saveRecord(path: String, recordBytes: Array[Byte], append: Boolean): Unit

  /**
    * 주어진 경로의 파일을 삭제합니다 (예: 임시 파일 정리 시).
    * @param path 삭제할 파일 경로
    * @return 성공 여부
    */
  def deleteFile(path: String): Boolean
}

// FileStorageRepository
class DiskFileStorageRepository extends FileStorageRepository {

  private val RECORD_SIZE = 100 // 100 바이트

  /**
    * 파일 읽기 구현
    */
  override def readBlock(path: String): List[Record] = {
    val file = new File(path)
    if (!file.exists() || file.length() == 0) return List.empty

    // 파일 크기가 100바이트의 배수인지 확인하는 로직 추가
    if (file.length() % RECORD_SIZE != 0) {
      throw new IOException(s"File size (${file.length()} bytes) is not a multiple of record size ($RECORD_SIZE bytes).")
    }

    // Using 블록을 사용해 자원을 안전하게 닫습니다 (try-with-resources와 유사)
    Using(new FileInputStream(file)) { fis =>
      val records = scala.collection.mutable.ListBuffer[Record]()
      var bytesRead: Int = 0
      val buffer = new Array[Byte](RECORD_SIZE)

      while ({ bytesRead = fis.read(buffer); bytesRead != -1 }) {
        if (bytesRead == RECORD_SIZE) {
          // 배열 복사본을 만들어 Record 객체를 생성합니다.
          records += Record(buffer.clone()) 
        } else if (bytesRead > 0) {
          // 파일 끝에서 100바이트 미만의 데이터가 남은 경우 - 오류 발생생
          throw new IOException(s"Incomplete record found. Read $bytesRead bytes instead of $RECORD_SIZE.")
        }
      }
      records.toList
    }.getOrElse(throw new IOException(s"Failed to read data from file: $path"))
  }

  /**
    * 레코드 쓰기 구현 (PartitionService에서 호출됨)
    */
  override def saveRecord(path: String, recordBytes: Array[Byte], append: Boolean): Unit = {
    require(recordBytes.length == RECORD_SIZE, s"Data must be exactly $RECORD_SIZE bytes.")

    Using(new FileOutputStream(path, append)) { fos =>
      fos.write(recordBytes)
    }.getOrElse(throw new IOException(s"Failed to write data to file: $path"))
  }
  
  override def deleteFile(path: String): Boolean = {
    val file = new File(path)
    if (file.exists()) {
      file.delete()
    } else {
      true 
    }
  }
}