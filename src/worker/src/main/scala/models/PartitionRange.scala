package models

case class PartitionRange(
  id: Int,             
  startKey: RecordKey, 
  endKey: RecordKey,  
  destWorkerId: Int    
)