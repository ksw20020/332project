package services

import repositories.FileStorageRepository
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.mutable.PriorityQueue
import java.io.File
import models._

type RecordBatch = List[Record]

class SortService(
  fileRepo: FileStorageRepository
)(implicit ec: ExecutionContext) {

  private val RECORD_SIZE = 100
  private val READ_SIZE = 10000

  def sortNextBatch(filePath: String, offset: Long): Future[RecordBatch] = {
    Future {
      val length = RECORD_SIZE * READ_SIZE
      val records: List[Record] = try {
        fileRepo.readBlock(filePath, offset, length)
      } catch {
        case e: Exception =>
          throw new RuntimeException(s"Failed to read chunk at block", e)
      }

      if (records.nonEmpty) {
        records.sortBy(_.key)
      } else {
        List.empty[Record]
      }
    }
  }

  def kWayMerge(inputFiles: Seq[String], outputFilePath: String): Future[Unit] = {
    Future {
      val iterators = inputFiles.map(path => new ChunkedRecordIterator(path, fileRepo, RECORD_SIZE * READ_SIZE))
      
      val pq = new PriorityQueue[ChunkedRecordIterator]()(Ordering.by[ChunkedRecordIterator, RecordKey](_.head.key).reverse)

      iterators.foreach { it =>
        if (it.hasNext) pq.enqueue(it)
      }

      val tempFile = new File(outputFilePath)
      if (tempFile.exists()) tempFile.delete()

      while (pq.nonEmpty) {
        val minIter = pq.dequeue()
        val record = minIter.next()

        fileRepo.saveRecord(outputFilePath, record.bytes, append = true)

        if (minIter.hasNext) {
          pq.enqueue(minIter)
        }
      }
    }
  }

  private class ChunkedRecordIterator(path: String, repo: FileStorageRepository, chunkSize: Long) extends Iterator[Record] {
    private var currentOffset: Long = 0
    private var buffer: BufferedIterator[Record] = Iterator.empty.buffered
    private var _hasNext: Boolean = true

    fetchNextChunk()

    private def fetchNextChunk(): Unit = {
      if (buffer.hasNext) return

      try {
        val records = repo.readBlock(path, currentOffset, chunkSize)
        if (records.isEmpty) {
          _hasNext = false
        } else {
          buffer = records.iterator.buffered
          currentOffset += records.size * 100L
        }
      } catch {
        case _: Exception => _hasNext = false
      }
    }

    override def hasNext: Boolean = {
      if (buffer.hasNext) true
      else {
        fetchNextChunk()
        _hasNext && buffer.hasNext
      }
    }

    def head: Record = {
      if (hasNext) buffer.head
      else throw new NoSuchElementException("Iterator is empty")
    }

    override def next(): Record = {
      if (hasNext) buffer.next()
      else throw new NoSuchElementException("Iterator is empty")
    }
  }
}