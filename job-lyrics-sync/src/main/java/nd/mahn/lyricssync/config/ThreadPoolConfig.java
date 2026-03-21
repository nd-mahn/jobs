package nd.mahn.lyricssync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadPoolConfig {

    @Bean("scannerExecutor")
    public ThreadPoolTaskExecutor scannerExecutor() {
        ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
        t.setCorePoolSize(5);
        t.setMaxPoolSize(20);
        t.setQueueCapacity(10);
        t.setThreadNamePrefix("scan-");
        t.initialize();
        return t;
    }

    @Bean("applierExecutor")
    public ThreadPoolTaskExecutor applierExecutor() {
        ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
        t.setCorePoolSize(5);
        t.setMaxPoolSize(20);
        t.setQueueCapacity(10);
        t.setThreadNamePrefix("apply-");
        t.initialize();
        return t;
    }
}
