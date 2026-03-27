package nd.mahn.jobproducerconsumerimporter.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.jobproducerconsumerimporter.dto.MnpDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileProducer implements Runnable {
    private final BlockingQueue<List<MnpDto>> listBlockingQueue;
    private final AtomicInteger batchSize = new AtomicInteger(100000);
    @Value("${file.path}")
    private String pathFile;

    @Override
    public void run() {
        log.info("FileProducer started.");
        while (!Thread.currentThread().isInterrupted()) {
            long startTime = System.currentTimeMillis();
            try {
                produce();

                long duration = System.currentTimeMillis() - startTime;
                log.debug("FileProducer cycle completed in {} ms", duration);
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                log.warn("FileProducer interrupted: {}", e.getMessage());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in FileProducer loop: {}", e.getMessage(), e);
            }
        }
        log.info("FileProducer stopped.");
    }

    private void produce() {
        Path path = Path.of(pathFile);
        if (!Files.exists(path)) {
            log.trace("File not found at: {}", pathFile);
            return;
        }

        log.info("Starting to process file: {}", pathFile);
        List<MnpDto> currentBatch = new ArrayList<>();
        int totalCount = 0;

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                MnpDto dto = conver(line, String.valueOf(totalCount));
                if (dto != null) {
                    currentBatch.add(dto);
                    totalCount++;
                }

                if (currentBatch.size() >= batchSize.get()) {
                    log.info("Pushing batch of size: {}", currentBatch.size());
                    listBlockingQueue.put(new ArrayList<>(currentBatch));
                    currentBatch.clear();
                }
            }

            // Gửi batch cuối cùng nếu còn dữ liệu
            if (!currentBatch.isEmpty()) {
                log.info("Pushing final batch of size: {}", currentBatch.size());
                listBlockingQueue.put(currentBatch);
            }

            log.info("Finished processing file. Total records: {}", totalCount);

            Files.move(path, path.resolveSibling(path.getFileName() + ".done"));

        } catch (IOException e) {
            log.error("IO Error reading file: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            log.warn("Produce process interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private MnpDto conver(String line, String index) {
        try {
            String[] arr = line.split(",");
            if (arr.length < 7) {
                log.warn("Line {} is invalid (not enough columns): {}", index, line);
                return null;
            }
            return new MnpDto(
                    arr[0].trim(),
                    arr[1].trim(),
                    arr[2].trim(),
                    arr[3].trim(),
                    arr[4].trim(),
                    arr[5].trim(),
                    arr[6].trim(),
                    index
            );
        } catch (Exception e) {
            log.error("Error parsing line {}: {}", index, e.getMessage());
            return null;
        }
    }
}
