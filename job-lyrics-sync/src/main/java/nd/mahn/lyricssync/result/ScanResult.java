package nd.mahn.lyricssync.result;

import lombok.Data;

import java.nio.file.Path;

@Data
public class ScanResult {
    private Path source;
    private String lyricsSource;
    private String artSource;
    private String error;
    private Path outputMetadata;

    public ScanResult(Path src) {
        this.source = src;
    }
}
