package dev.m.music;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Fallback chain lấy ảnh bìa:
 * 1. iTunes API          — miễn phí, 1000x1000, không key
 * 2. MusicBrainz + CAA  — API mở, cộng đồng, 1200px
 * 3. Genius og:image     — tái dùng Document từ LyricsFetcher (0 HTTP thêm)
 * 4. loibaihat og:image  — tái dùng Document từ LyricsFetcher (0 HTTP thêm)
 * 5. Last.fm             — free API key tại last.fm/api/account/create
 */
public class AlbumArtFetcher {

    private static final int TIMEOUT_MS = 12_000;

    // Đăng ký miễn phí: https://www.last.fm/api/account/create
    // Để trống → nguồn Last.fm bị bỏ qua tự động
    private static final String LASTFM_API_KEY = "";

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true) // cần cho MusicBrainz→CAA redirect
            .build();

    // ── Entry point chính (truyền Document tái dùng từ LyricsFetcher) ─────────
    public byte[] fetch(String artist, String title, LyricsFetchResult lyricsResult) {
        Document geniusDoc = lyricsResult != null ? lyricsResult.geniusSongDoc : null;
        Document loibaihatDoc = lyricsResult != null ? lyricsResult.loibaihatSongDoc : null;

        byte[] result;

        result = fromItunes(artist, title);
        if (ok(result)) {
            log("iTunes", title);
            return result;
        }

        result = fromMusicBrainz(artist, title);
        if (ok(result)) {
            log("MusicBrainz/CAA", title);
            return result;
        }

        result = fromOgImage(geniusDoc);   // 0 HTTP request thêm
        if (ok(result)) {
            log("Genius og:image", title);
            return result;
        }

        result = fromOgImage(loibaihatDoc); // 0 HTTP request thêm
        if (ok(result)) {
            log("loibaihat og:image", title);
            return result;
        }

        result = fromLastFm(artist, title);
        if (ok(result)) {
            log("Last.fm", title);
            return result;
        }

        System.err.println("  [art] Không tìm được: " + title);
        return null;
    }

    /**
     * Overload khi không có LyricsFetchResult
     */
    public byte[] fetch(String artist, String title) {
        return fetch(artist, title, null);
    }

    // ── 1. iTunes ─────────────────────────────────────────────────────────────
    private byte[] fromItunes(String artist, String title) {
        try {
            String main = (!isBlank(artist) && artist.contains(","))
                    ? artist.split(",")[0].trim() : nullToEmpty(artist);
            String term = (main + " " + nullToEmpty(title)).trim();
            String url = "https://itunes.apple.com/search?term="
                    + encode(term) + "&limit=1&entity=song";

            String body = httpGet(url, null);
            JSONArray results = new JSONObject(body).optJSONArray("results");
            if (results == null || results.isEmpty()) return null;

            String imgUrl = results.getJSONObject(0)
                    .optString("artworkUrl100", "")
                    .replace("100x100bb", "1000x1000bb");
            return isBlank(imgUrl) ? null : downloadBytes(imgUrl);
        } catch (Exception e) {
            return null;
        }
    }

    // ── 2. MusicBrainz + Cover Art Archive ───────────────────────────────────
    private byte[] fromMusicBrainz(String artist, String title) {
        try {
            String query = "recording:\"" + nullToEmpty(title) + "\""
                    + (isBlank(artist) ? "" : " AND artist:\"" + artist + "\"");
            String url = "https://musicbrainz.org/ws/2/release/?fmt=json&limit=3&query=" + encode(query);

            String body = httpGet(url, "MusicScanner/1.0");
            JSONArray releases = new JSONObject(body).optJSONArray("releases");
            if (releases == null || releases.isEmpty()) return null;

            for (int i = 0; i < releases.length(); i++) {
                String mbid = releases.getJSONObject(i).optString("id", "");
                if (isBlank(mbid)) continue;
                try {
                    byte[] bytes = downloadBytes("https://coverartarchive.org/release/" + mbid + "/front-1200");
                    if (ok(bytes)) return bytes;
                } catch (Exception ignored) {
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── 3 & 4. og:image từ Document đã có sẵn ────────────────────────────────
    private byte[] fromOgImage(Document doc) {
        if (doc == null) return null;
        try {
            Element meta = doc.selectFirst("meta[property=og:image]");
            if (meta == null) return null;
            String imgUrl = meta.attr("content");
            // Nâng resolution nếu là Genius thumbnail
            imgUrl = imgUrl.replaceAll("\\.\\d+x\\d+x\\d+\\.jpg$", ".1000x1000x1.jpg");
            return isBlank(imgUrl) ? null : downloadBytes(imgUrl);
        } catch (Exception e) {
            return null;
        }
    }

    // ── 5. Last.fm ────────────────────────────────────────────────────────────
    private byte[] fromLastFm(String artist, String title) {
        if (isBlank(LASTFM_API_KEY) || isBlank(artist) || isBlank(title)) return null;
        try {
            String url = "https://ws.audioscrobbler.com/2.0/?method=track.getInfo"
                    + "&api_key=" + LASTFM_API_KEY
                    + "&artist=" + encode(artist)
                    + "&track=" + encode(title)
                    + "&format=json";

            String body = httpGet(url, null);
            JSONObject track = new JSONObject(body).optJSONObject("track");
            if (track == null) return null;

            JSONArray images = track.optJSONArray("image");
            if (images == null) return null;

            for (String size : new String[]{"mega", "extralarge", "large"}) {
                for (int i = 0; i < images.length(); i++) {
                    JSONObject img = images.getJSONObject(i);
                    if (size.equals(img.optString("size"))) {
                        String imgUrl = img.optString("#text", "");
                        if (!isBlank(imgUrl)) return downloadBytes(imgUrl);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String httpGet(String url, String ua) throws IOException {
        Request.Builder rb = new Request.Builder().url(url);
        if (!isBlank(ua)) rb.header("User-Agent", ua);
        try (Response r = HTTP.newCall(rb.build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            return r.body().string();
        }
    }

    private byte[] downloadBytes(String urlStr) throws IOException {
        Request req = new Request.Builder().url(urlStr).build();
        try (Response r = HTTP.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            return r.body().bytes();
        }
    }

    private static boolean ok(byte[] b) {
        return b != null && b.length > 0;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static void log(String src, String title) {
        System.out.printf("  [art  OK] %-22s ← %s%n", src, title);
    }
}