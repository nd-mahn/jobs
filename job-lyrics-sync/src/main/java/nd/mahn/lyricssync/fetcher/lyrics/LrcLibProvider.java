package nd.mahn.lyricssync.fetcher.lyrics;

import lombok.RequiredArgsConstructor;
import nd.mahn.lyricssync.result.LyricsFetchResult;
import nd.mahn.lyricssync.utils.HttpTransport;
import nd.mahn.lyricssync.utils.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LrcLibProvider extends BaseLyricsProvider {
    private final HttpTransport process;

    @Override
    public String getName() {
        return "LrcLib";
    }

    @Override
    protected LyricsFetchResult fetchInternal(String artist, String title) throws Exception {
        String q = StringUtils.encode(StringUtils.nullToEmpty(title) + " " + StringUtils.nullToEmpty(artist));
        String body = process.httpGet("https://lrclib.net/api/search?q=" + q);
        JSONArray results = new JSONArray(body);
        if (results.isEmpty()) return null;
        JSONObject first = results.getJSONObject(0);
        String synced = first.optString("syncedLyrics", "");
        if (StringUtils.ok(synced)) return lyrics(StringUtils.cleanHtml(synced));
        return lyrics(first.optString("plainLyrics", "").trim());
    }
}
