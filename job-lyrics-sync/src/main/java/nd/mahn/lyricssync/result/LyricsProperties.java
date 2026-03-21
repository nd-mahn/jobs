package nd.mahn.lyricssync.result;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "job.lyrics")
public class LyricsProperties {
    private String pathScanner;
    private String outputMetaData;
}

