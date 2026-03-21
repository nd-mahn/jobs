package nd.mahn.lyricssync.result;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;

@Data
@AllArgsConstructor
public class SummaryResult {
    private int okLyrics;
    private int okArt;
    private int errors;
    private Path outputMetadata;

    public void incrementOkLyrics() {
        this.okLyrics++;
    }

    public void incrementOkArt() {
        this.okArt++;
    }

    public void incrementErrors() {
        this.errors++;
    }

    public boolean hasSuccess() {
        return this.okLyrics > 0 && this.okArt > 0;
    }
}
