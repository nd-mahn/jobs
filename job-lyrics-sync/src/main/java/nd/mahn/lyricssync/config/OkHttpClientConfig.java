package nd.mahn.lyricssync.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpClientConfig {
    @Bean
    public OkHttpClient getOkHttpClient() {
        return new OkHttpClient.Builder().connectTimeout(12_000, TimeUnit.MILLISECONDS).readTimeout(12_000, TimeUnit.MILLISECONDS).build();
    }
}
