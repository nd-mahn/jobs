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
public class LastFmArtFetcher implements AlbumArtProvider {

    private final HttpTransport httpTransport;
    private static final String LASTFM_API_KEY = "";

    public byte[] fetchArt(String artist, String title, LyricsFetchResult lyricsResult) {
        byte[] result = fromLastFm(artist, title);
        if (StringUtils.ok(result)) {
            log.info("[art OK] {} ← artist='{}' title='{}'", "Last.fm", artist, title);
            return result;
        }
        log.warn("  [art] Không tìm được: {}", title);
        return new byte[0];
    }

    private byte[] fromLastFm(String artist, String title) {
        if (StringUtils.isBlank(LASTFM_API_KEY) || StringUtils.isBlank(artist) || StringUtils.isBlank(title))
            return new byte[0];
        try {
            String url = "https://ws.audioscrobbler.com/2.0/?method=track.getInfo"
                    + "&api_key=" + LASTFM_API_KEY
                    + "&artist=" + StringUtils.encode(artist)
                    + "&track=" + StringUtils.encode(title)
                    + "&format=json";
            String body = httpTransport.httpGet(url, null);
            JSONObject track = new JSONObject(body).optJSONObject("track");
            if (track == null) return new byte[0];
            JSONArray images = track.optJSONArray("image");
            if (images == null) return new byte[0];
            for (String size : new String[]{"mega", "extralarge", "large"}) {
                for (int i = 0; i < images.length(); i++) {
                    JSONObject img = images.getJSONObject(i);
                    if (size.equals(img.optString("size"))) {
                        String imgUrl = img.optString("#text", "");
                        if (!StringUtils.isBlank(imgUrl)) return httpTransport.downloadBytes(imgUrl);
                    }
                }
            }
            return new byte[0];
        } catch (Exception e) {
            return new byte[0];
        }
    }
}