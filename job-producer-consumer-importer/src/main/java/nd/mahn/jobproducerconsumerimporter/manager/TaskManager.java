package nd.mahn.jobproducerconsumerimporter.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.jobproducerconsumerimporter.consumer.ImportConsumer;
import nd.mahn.jobproducerconsumerimporter.producer.FileProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManager implements ApplicationRunner {
    private final FileProducer fileProducer;
    private final ImportConsumer importConsumer;
    
    @Qualifier("virtual")
    private final Executor executorService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Spring Boot Application started. Launching background tasks...");
        
        try {
            // Chạy Producer
            executorService.execute(fileProducer);
            for (int i = 0; i < 1; i++) {
                executorService.execute(importConsumer);
            }
            log.info("All tasks have been submitted to the executor.");
        } catch (Exception e) {
            log.error("Failed to start background tasks: {}", e.getMessage(), e);
        }
    }
}
