package nd.mahn.jobproducerconsumerimporter.config;

import lombok.extern.slf4j.Slf4j;
import nd.mahn.jobproducerconsumerimporter.dto.MnpDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Slf4j
@Configuration
public class BeanConfig {
    @Value("${queue.size:10}")
    private int queueSize;

    @Bean
    public BlockingQueue<List<MnpDto>> blockingQueue() {
        log.info("Initializing BlockingQueue with size: {}", queueSize);
        return new ArrayBlockingQueue<>(queueSize);
    }
}
