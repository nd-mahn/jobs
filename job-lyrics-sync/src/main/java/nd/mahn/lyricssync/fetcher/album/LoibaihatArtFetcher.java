package nd.mahn.lyricssync.fetcher.album;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.lyricssync.result.LyricsFetchResult;
import nd.mahn.lyricssync.utils.HttpTransport;
import nd.mahn.lyricssync.utils.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoibaihatArtFetcher implements AlbumArtProvider {

    private final HttpTransport httpTransport;

    public byte[] fetchArt(String artist, String title, LyricsFetchResult lyricsResult) {
        Document document = lyricsResult != null ? lyricsResult.getDoc() : null;
        byte[] result = fromOgImage(document); // 0 HTTP request thêm
        if (StringUtils.ok(result)) {
            log.info("[art OK] {} ← artist='{}' title='{}'", lyricsResult.getSource(), artist, title);
            return result;
        }
        return new byte[0];
    }

    private byte[] fromOgImage(Document doc) {
        if (doc == null) return new byte[0];
        try {
            Element meta = doc.selectFirst("meta[property=og:image]");
            if (meta == null) return new byte[0];
            String imgUrl = meta.attr("content");
            // Nâng resolution nếu là Genius thumbnail
            imgUrl = imgUrl.replaceAll("\\.\\d+x\\d+x\\d+\\.jpg$", ".1000x1000x1.jpg");
            return StringUtils.isBlank(imgUrl) ? null : httpTransport.downloadBytes(imgUrl);
        } catch (Exception e) {
            return new byte[0];
        }
    }
}