package nd.mahn.jobproducerconsumerimporter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
public class ThreadConfig {

    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        log.info("Configuring ThreadPoolTaskExecutor...");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("JobWorker-");
        executor.initialize();
        log.info("ThreadPoolTaskExecutor initialized with CorePoolSize: {}, MaxPoolSize: {}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize());
        return executor;
    }
@Primary
    @Bean("virtual")
    public Executor newVirtualThreadPerTaskExecutor() {
        log.info("Initializing Virtual Thread Per Task Executor (Java 21+)");
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
