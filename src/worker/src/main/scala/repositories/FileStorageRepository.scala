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
  def readBlock(path: String, offset: Long, length: Long): List[Record]

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
  override def readBlock(path: String, offset: Long, length: Long): List[Record] = {
    require(offset >= 0, "Offset must be non-negative.")
    require(length >= 0 && length % RECORD_SIZE == 0, "Length must be non-negative and a multiple of 100 bytes.")

    val recordsToRead = (length / RECORD_SIZE).toInt
    val records = scala.collection.mutable.ListBuffer[Record]()

    // RandomAccessFile을 사용해 파일 내 특정 위치로 이동합니다.
    Using(new RandomAccessFile(path, "r")) { raf =>
      // 1. 오프셋으로 이동
      raf.seek(offset) 
      
      // 2. 레코드 단위로 데이터 읽기
      for (_ <- 0 until recordsToRead) {
        val buffer = new Array[Byte](RECORD_SIZE)
        val bytesRead = raf.read(buffer)
        
        if (bytesRead == RECORD_SIZE) {
          records += Record(buffer.clone())
        } else if (bytesRead == -1) {
          // 파일 끝에 도달 (예상보다 일찍)
          println(s"Warning: Reached EOF earlier than expected at offset $offset.")
          return records.toList
        } else {
          throw new IOException(s"Incomplete record read: $bytesRead bytes instead of $RECORD_SIZE.")
        }
      }
      records.toList

    }.getOrElse(throw new IOException(s"Failed to read data from file: $path at offset $offset"))
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