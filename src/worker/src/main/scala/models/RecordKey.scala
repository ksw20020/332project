package models

case class RecordKey(key: Array[Byte]) extends Comparable[RecordKey] {

  override def compareTo(other: RecordKey): Int = {
    var i = 0
    while (i < 10) {
      val diff = (key(i) & 0xFF) - (other.key(i) & 0xFF)
      
      if (diff != 0) return diff 
      i += 1
    }
    0 
  }

  override def equals(obj: Any): Boolean = obj match {
    case that: RecordKey => java.util.Arrays.equals(key, that.key)
    case _ => false
  }

  override def hashCode(): Int = java.util.Arrays.hashCode(key)
}