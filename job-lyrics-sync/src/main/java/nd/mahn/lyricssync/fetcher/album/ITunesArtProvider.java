package nd.mahn.lyricssync.fetcher.album;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.lyricssync.result.LyricsFetchResult;
import nd.mahn.lyricssync.utils.HttpTransport;
import nd.mahn.lyricssync.utils.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ITunesArtProvider implements AlbumArtProvider {
    private final HttpTransport httpTransport;

    public byte[] fetchArt(String artist, String title, LyricsFetchResult lyricsResult) {
        byte[] result = fromItunes(artist, title);
        if (StringUtils.ok(result)) {
            log.info("[art OK] {} ← artist='{}' title='{}'", "iTunes", artist, title);
            return result;
        }
        return new byte[0];
    }

    private byte[] fromItunes(String artist, String title) {
        try {
            String main = (!StringUtils.isBlank(artist) && artist.contains(","))
                    ? artist.split(",")[0].trim() : StringUtils.nullToEmpty(artist);
            String term = (main + " " + StringUtils.nullToEmpty(title)).trim();
            String url = "https://itunes.apple.com/search?term="
                    + StringUtils.encode(term) + "&limit=1&entity=song";

            String body = httpTransport.httpGet(url, null);
            JSONArray results = new JSONObject(body).optJSONArray("results");
            if (results == null || results.isEmpty()) return new byte[0];

            String imgUrl = results.getJSONObject(0)
                    .optString("artworkUrl100", "")
                    .replace("100x100bb", "1000x1000bb");
            return StringUtils.isBlank(imgUrl) ? null : httpTransport.downloadBytes(imgUrl);
        } catch (Exception e) {
            return new byte[0];
        }
    }
}