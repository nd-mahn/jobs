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
public class GeniusProvider extends BaseLyricsProvider {
    private final HtmlParser htmlParser;

    @Override
    public String getName() {
        return "Genius";
    }

    @Override
    protected LyricsFetchResult fetchInternal(String artist, String title) throws Exception {
        String q = StringUtils.encode(StringUtils.nullToEmpty(title) + " " + StringUtils.nullToEmpty(artist));
        String url = "https://genius.com/search?q=" + q;
        Document searchDoc = htmlParser.doc(url);
        Element songLink = searchDoc.selectFirst("a.mini_card[href*='/lyrics']");
        if (songLink == null) songLink = searchDoc.selectFirst("a[href*='-lyrics']");
        if (songLink == null) return null;
        Document songDoc = htmlParser.doc(songLink.absUrl("href"));
        StringBuilder sb = new StringBuilder();
        for (Element c : songDoc.select("div[data-lyrics-container=true]"))
            sb.append(StringUtils.cleanHtml(c.html())).append("\n");
        return new LyricsFetchResult(sb.toString().trim(), songDoc, getName());
    }
}
