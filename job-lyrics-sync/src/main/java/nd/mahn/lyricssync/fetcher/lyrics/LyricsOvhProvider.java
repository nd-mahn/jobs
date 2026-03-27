package nd.mahn.lyricssync.fetcher.lyrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.lyricssync.result.LyricsFetchResult;
import nd.mahn.lyricssync.utils.HttpTransport;
import nd.mahn.lyricssync.utils.StringUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LyricsOvhProvider extends BaseLyricsProvider {
    private final HttpTransport process;

    @Override
    public String getName() {
        return "LyricsOvh";
    }

    @Override
    protected LyricsFetchResult fetchInternal(String artist, String title) throws Exception {
        try {
            if (StringUtils.isBlank(artist) || StringUtils.isBlank(title))
                return new LyricsFetchResult("", null, "none");
            String url = "https://api.lyrics.ovh/v1/" + StringUtils.encode(artist) + "/" + StringUtils.encode(title);
            String body = process.httpGet(url, null);
            String text = new JSONObject(body).optString("lyrics", "").trim();
            return lyrics(text);
        } catch (Exception e) {
            log.error("Exception artist, title not found {}", e.getMessage());
            return new LyricsFetchResult("", null, "none");
        }
    }
}
