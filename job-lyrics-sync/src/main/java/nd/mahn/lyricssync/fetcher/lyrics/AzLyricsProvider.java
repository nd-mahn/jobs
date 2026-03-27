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
public class AzLyricsProvider extends BaseLyricsProvider {
    private final HtmlParser htmlParser;

    @Override
    public String getName() {
        return "AzLyrics";
    }

    @Override
    protected LyricsFetchResult fetchInternal(String artist, String title) throws Exception {
        try {
            String a = StringUtils.slugify(StringUtils.nullToEmpty(artist));
            String t = StringUtils.slugify(StringUtils.nullToEmpty(title));
            if (a.isEmpty() || t.isEmpty()) return null;
            if (a.startsWith("the")) a = a.substring(3);
            String url = "https://www.azlyrics.com/lyrics/" + a + "/" + t + ".html";
            Document doc = htmlParser.docReferrer(url);
            Element container = doc.selectFirst(".col-xs-12.col-lg-8");
            if (container == null) return null;
            for (Element div : container.select("div:not([class]):not([id])")) {
                String text = div.text().trim();
                if (text.length() > 100) return lyrics(StringUtils.cleanHtml(div.html()));
            }
            return new LyricsFetchResult("", null, "none");
        } catch (Exception e) {
            log.error("Exception artist, title not found {}", e.getMessage());
            return new LyricsFetchResult("", null, "none");
        }
    }
}
