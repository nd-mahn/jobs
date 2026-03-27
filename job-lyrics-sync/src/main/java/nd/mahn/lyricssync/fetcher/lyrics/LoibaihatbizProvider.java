package nd.mahn.lyricssync.fetcher.lyrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.lyricssync.result.LyricsFetchResult;
import nd.mahn.lyricssync.utils.HtmlParser;
import nd.mahn.lyricssync.utils.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoibaihatbizProvider extends BaseLyricsProvider {
    private final HtmlParser htmlParser;

    @Override
    public String getName() {
        return "Loibaihatbiz";
    }

    @Override
    protected LyricsFetchResult fetchInternal(String artist, String title) throws Exception {
        try {
            String url = "https://loibaihat.biz/timkiem/";
            Document searchDoc = htmlParser.doc(url, title);
            Element link = searchDoc.selectFirst("div.list-lyric-song div.ten a");
            if (link == null) return null;
            Document songDoc = htmlParser.doc(link.absUrl("href"));
            Element lyricDiv = songDoc.selectFirst(".lyric-song, .lyric-content");
            String lyrics = lyricDiv != null ? StringUtils.cleanHtml(lyricDiv.html()) : "";
            return new LyricsFetchResult(lyrics, songDoc, getName());
        } catch (Exception e) {
            log.error("Exception artist, title not found {}", e.getMessage());
            return new LyricsFetchResult("", null, "none");
        }
    }
}
