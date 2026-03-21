package nd.mahn.lyricssync;

import org.jsoup.nodes.Document;

/**
 * Kết quả fetch lyrics — chứa cả Document gốc để AlbumArtFetcher tái dùng
 * mà không cần gọi thêm HTTP request.
 */
public class LyricsFetchResult {
    public final String   lyrics;
    public final Document geniusSongDoc;
    public final Document loibaihatSongDoc;
    public final String   source; // tên nguồn lấy được, để log

    public LyricsFetchResult(String lyrics, Document geniusDoc,
                             Document loibaihatDoc, String source) {
        this.lyrics           = lyrics == null ? "" : lyrics.trim();
        this.geniusSongDoc    = geniusDoc;
        this.loibaihatSongDoc = loibaihatDoc;
        this.source           = source;
    }

    public boolean hasLyrics() { return !lyrics.isBlank(); }

    public static final LyricsFetchResult EMPTY =
            new LyricsFetchResult("", null, null, "none");
}