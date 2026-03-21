package nd.mahn.lyricssync.result;

import lombok.Data;
import org.jsoup.nodes.Document;

@Data
public class LyricsFetchResult {
    private final String lyrics;
    private final Document doc;
    private final String source;

    public boolean hasLyrics() {
        return !lyrics.isBlank();
    }
}