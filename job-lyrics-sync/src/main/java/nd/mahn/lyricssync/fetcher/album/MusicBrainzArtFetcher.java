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
public class MusicBrainzArtFetcher implements AlbumArtProvider {

    private final HttpTransport httpTransport;

    public byte[] fetchArt(String artist, String title, LyricsFetchResult lyricsResult) {
        byte[] result;
        result = fromMusicBrainz(artist, title);
        if (StringUtils.ok(result)) {
            log.info("[art OK] {} ← artist='{}' title='{}'", "MusicBrainz/CAA", artist, title);
            return result;
        }
        log.warn("  [art] Không tìm được: {}", title);
        return new byte[0];
    }

    private byte[] fromMusicBrainz(String artist, String title) {
        try {
            String query = "recording:\"" + StringUtils.nullToEmpty(title) + "\""
                    + (StringUtils.isBlank(artist) ? "" : " AND artist:\"" + artist + "\"");
            String url = "https://musicbrainz.org/ws/2/release/?fmt=json&limit=3&query=" + StringUtils.encode(query);

            String body = httpTransport.httpGet(url, "MusicScanner/1.0");
            JSONArray releases = new JSONObject(body).optJSONArray("releases");
            if (releases == null || releases.isEmpty()) return new byte[0];

            for (int i = 0; i < releases.length(); i++) {
                String mbid = releases.getJSONObject(i).optString("id", "");
                if (StringUtils.isBlank(mbid)) continue;
                try {
                    byte[] bytes = httpTransport.downloadBytes("https://coverartarchive.org/release/" + mbid + "/front-1200");
                    if (StringUtils.ok(bytes)) return bytes;
                } catch (Exception ignored) {
                }
            }
            return new byte[0];
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
