import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

public class BinaryDataGenerator {

    private static final int RECORD_SIZE = 100; // 100 bytes (10 key + 90 value)

    public static void main(String[] args) {
        int numWorkers = 20;          
        int sizeMbPerWorker = 1;     
        String outputDir = "./worker_inputs"; 
        createWorkerInputs(numWorkers, sizeMbPerWorker, outputDir);
    }

   
    public static void createWorkerInputs(int numWorkers, int sizeMbPerWorker, String outputDir) {
        File dir = new File(outputDir);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                System.out.println("Directory '" + outputDir + "' created.");
            } else {
                System.err.println("Failed to create directory: " + outputDir);
                return;
            }
        }

        long totalBytesPerWorker = (long) sizeMbPerWorker * 1024 * 1024;
        long recordsPerWorker = totalBytesPerWorker / RECORD_SIZE;

        System.out.println("Generating BINARY data for " + numWorkers + " workers...");
        System.out.println(" - Size per worker: " + sizeMbPerWorker + "MB (" + recordsPerWorker + " records)");

        Random random = new Random();

       
        for (int i = 0; i < numWorkers; i++) {
            String fileName = String.format("input.worker.%d", i);
            File file = new File(dir, fileName);

            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
                int batchSize = 1000;
                byte[] batchBuffer = new byte[RECORD_SIZE * batchSize];
                
                long remainingRecords = recordsPerWorker;

                while (remainingRecords > 0) {
                    int currentBatchCount = (int) Math.min(batchSize, remainingRecords);
                    int currentBytesToWrite = currentBatchCount * RECORD_SIZE;

                    if (currentBytesToWrite == batchBuffer.length) {
                        random.nextBytes(batchBuffer);
                    } else {
                        byte[] tempBuffer = new byte[currentBytesToWrite];
                        random.nextBytes(tempBuffer);
                        System.arraycopy(tempBuffer, 0, batchBuffer, 0, currentBytesToWrite);
                    }

                    bos.write(batchBuffer, 0, currentBytesToWrite);
                    remainingRecords -= currentBatchCount;
                }

                System.out.println(" [Done] Created " + file.getPath());

            } catch (IOException e) {
                System.err.println("Error writing file: " + file.getPath());
                e.printStackTrace();
            }
        }

        System.out.println("\nAll binary input files generated successfully!");
    }
}
