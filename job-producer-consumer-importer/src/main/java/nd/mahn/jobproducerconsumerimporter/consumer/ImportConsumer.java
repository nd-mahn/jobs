package nd.mahn.jobproducerconsumerimporter.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.jobproducerconsumerimporter.dto.MnpDto;
import nd.mahn.jobproducerconsumerimporter.repository.MnpRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImportConsumer implements Runnable {
    private final BlockingQueue<List<MnpDto>> blockingQueue;
    private final MnpRepository mnpRepository;

    @Override
    public void run() {
        log.info("ImportConsumer started.");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<MnpDto> firstBatch = blockingQueue.poll(5, TimeUnit.SECONDS);

                if (firstBatch != null && !firstBatch.isEmpty()) {
                    processCurrentQueue(firstBatch);
                }

            } catch (InterruptedException e) {
                log.warn("ImportConsumer interrupted: {}", e.getMessage());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in ImportConsumer cycle: {}", e.getMessage(), e);
            }
        }
        log.info("ImportConsumer stopped.");
    }

    private void processCurrentQueue(List<MnpDto> firstBatch) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("Start a new import cycle...");
            mnpRepository.truncate();
            mnpRepository.insert(firstBatch);
            int totalInserted = firstBatch.size();
            List<List<MnpDto>> remainingBatches = new ArrayList<>();
            blockingQueue.drainTo(remainingBatches);

            for (List<MnpDto> batch : remainingBatches) {
                if (batch != null && !batch.isEmpty()) {
                    mnpRepository.insert(batch);
                    totalInserted += batch.size();
                }
            }
            log.info("Total records inserted to TEMP: {}", totalInserted);
            mnpRepository.merge();
            mnpRepository.truncate();

            log.info("Import cycle completed in {} ms", (System.currentTimeMillis() - startTime));
        } catch (Exception e) {
            log.error("Critical error in import cycle: {}", e.getMessage(), e);
            try {
                mnpRepository.truncate();
            } catch (Exception ignored) {
            }
        }
    }
}
