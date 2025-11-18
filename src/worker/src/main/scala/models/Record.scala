package models

case class Record(bytes: Array[Byte]) {
  require(bytes.length == 100)
}