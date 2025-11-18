package repositories

import java.io._
import java.nio.file.{Files, Paths}
import scala.util.Try

/**
 * Repository, which is responsible for storing and reading data to and from the file system. Used to handle intermediate or final output files in distributed sorting operations.
 */
class FileStorageRepository {

    /**
     * Reads data by the specified size (bytes) from the specified path.
     * If you read the entire file, you can set amount to -1.
     *
     * @param path 읽을 파일의 경로 (String)
     * @param amount 읽을 데이터의 크기 (Int). -1이면 파일 전체를 읽습니다.
     * @return 읽은 데이터 (Array[Byte])
     */
    def read(path: String, amount: Int): Try[Array[Byte]] = Try {
        val file = new File(path)
        if (!file.exists() || !file.isFile) {
            throw new FileNotFoundException(s"File not found or is not a file: $path")
        }

        val fileLength = file.length()
        val bytesToRead = if (amount == -1 || amount > fileLength) fileLength.toInt else amount

        val buffer = new Array[Byte](bytesToRead)
        
        // Try-with-resources 패턴을 위한 Loan Pattern
        var fis: FileInputStream = null
        try {
            fis = new FileInputStream(file)
            val bytesRead = fis.read(buffer, 0, bytesToRead)
            
            // Return only the correct reading, as the actual number of bytes read may differ from the number of bytes requested
            if (bytesRead == bytesToRead) {
                buffer
            } else if (bytesRead > 0) {
                // EOF 도달 등으로 인해 요청한 양보다 적게 읽었을 경우
                java.util.Arrays.copyOfRange(buffer, 0, bytesRead)
            } else {
                // 읽은 데이터가 없을 경우 (파일 크기가 0이거나)
                Array.empty[Byte]
            }
        } finally {
            if (fis != null) fis.close()
        }
    }

    /**
     * 주어진 데이터를 지정된 경로에 저장합니다.
     *
     * @param path 데이터를 저장할 파일의 경로 (String)
     * @param data 저장할 데이터 (Array[Byte])
     */
    def save(path: String, data: Array[Byte]): Try[Unit] = Try {
        val file = new File(path)
        
        // 부모 디렉토리가 존재하지 않으면 생성
        Option(file.getParentFile).foreach { parent =>
            if (!parent.exists()) {
                Files.createDirectories(Paths.get(parent.getAbsolutePath))
            }
        }

        // 데이터 저장
        var fos: FileOutputStream = null
        try {
            fos = new FileOutputStream(file)
            fos.write(data)
        } finally {
            if (fos != null) fos.close()
        }
    }

    /**
     * 파일이 존재하는지 확인합니다.
     * @param path 확인할 파일의 경로 (String)
     * @return 파일 존재 여부 (Boolean)
     */
    def exists(path: String): Boolean = {
        Files.exists(Paths.get(path))
    }
}
