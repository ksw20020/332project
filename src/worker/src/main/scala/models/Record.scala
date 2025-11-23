package models

case class Record(bytes: Array[Byte]) {
  
  require(bytes.length == 100, "Record must be exactly 100 bytes in length.")

  val key: RecordKey = RecordKey(bytes.take(10))
  val value: Array[Byte] = bytes.slice(10, 100)
}