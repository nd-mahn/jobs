package nd.mahn.lyricssync;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Fallback chain lấy lyrics:
 * 1. loibaihat.biz  — scraping, nhạc Việt
 * 2. lrclib.net     — REST API miễn phí, không key, nhạc quốc tế
 * 3. lyrics.ovh     — REST API đơn giản
 * 4. azlyrics.com   — scraping, kho lớn nhạc Âu Mỹ
 * 5. genius.com     — scraping, fallback cuối
 * <p>
 * Document từ genius & loibaihat được giữ lại trong LyricsFetchResult
 * để AlbumArtFetcher tái dùng (lấy og:image) mà không gọi HTTP thêm.
 */
public class LyricsFetcher {

    private static final int TIMEOUT_MS = 12_000;

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build();

    // ── Entry point ───────────────────────────────────────────────────────────
    public LyricsFetchResult fetch(String artist, String title) {
        String lyrics;
        Document loibaihatDoc = null;
        Document geniusDoc = null;

        // 1. loibaihat.biz
//        DocAndLyrics r1 = fromLoiBaiHat(title);
//        if (r1 != null && ok(r1.lyrics)) {
//            loibaihatDoc = r1.doc;
//            return new LyricsFetchResult(r1.lyrics, null, loibaihatDoc, "loibaihat.biz");
//        }
//        if (r1 != null) loibaihatDoc = r1.doc; // giữ doc dù không có lyrics

        // 2. lrclib.net
        lyrics = fromLrcLib(artist, title);
        if (ok(lyrics)) return new LyricsFetchResult(lyrics, null, loibaihatDoc, "lrclib.net");

        // 3. lyrics.ovh
        lyrics = fromLyricsOvh(artist, title);
        if (ok(lyrics)) return new LyricsFetchResult(lyrics, null, loibaihatDoc, "lyrics.ovh");

        // 4. azlyrics.com
        lyrics = fromAzLyrics(artist, title);
        if (ok(lyrics)) return new LyricsFetchResult(lyrics, null, loibaihatDoc, "azlyrics.com");

        // 5. genius.com
        DocAndLyrics r5 = fromGenius(artist, title);
        if (r5 != null && ok(r5.lyrics)) {
            geniusDoc = r5.doc;
            return new LyricsFetchResult(r5.lyrics, geniusDoc, loibaihatDoc, "genius.com");
        }
        if (r5 != null) geniusDoc = r5.doc; // giữ doc dù không có lyrics

        // Trả về EMPTY nhưng vẫn giữ doc để AlbumArtFetcher dùng
        return new LyricsFetchResult("", geniusDoc, loibaihatDoc, "none");
    }

    // ── 1. loibaihat.biz ──────────────────────────────────────────────────────
    private DocAndLyrics fromLoiBaiHat(String title) {
        try {
            Document searchDoc = Jsoup.connect("https://loibaihat.biz/timkiem/")
                    .data("keyword", nullToEmpty(title))
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(TIMEOUT_MS)
                    .get();

            Element link = searchDoc.selectFirst("div.list-lyric-song div.ten a");
            if (link == null) return null;

            Document songDoc = Jsoup.connect(link.absUrl("href"))
                    .userAgent("Mozilla/5.0")
                    .timeout(TIMEOUT_MS)
                    .get();

            Element lyricDiv = songDoc.selectFirst(".lyric-song, .lyric-content");
            String lyrics = lyricDiv != null ? cleanHtml(lyricDiv.html()) : "";
            return new DocAndLyrics(songDoc, lyrics);
        } catch (Exception e) {
            return null;
        }
    }

    // ── 2. lrclib.net ─────────────────────────────────────────────────────────
    private String fromLrcLib(String artist, String title) {
        try {
            String q = encode(nullToEmpty(title) + " " + nullToEmpty(artist));
            String body = httpGet("https://lrclib.net/api/search?q=" + q, "MusicScanner/1.0");
            JSONArray results = new JSONArray(body);
            if (results.isEmpty()) return "";

            JSONObject first = results.getJSONObject(0);
            String synced = first.optString("syncedLyrics", "");
            if (ok(synced)) return stripLrcTimestamps(synced);

            return first.optString("plainLyrics", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    // ── 3. lyrics.ovh ─────────────────────────────────────────────────────────
    private String fromLyricsOvh(String artist, String title) {
        if (isBlank(artist) || isBlank(title)) return "";
        try {
            String url = "https://api.lyrics.ovh/v1/" + encode(artist) + "/" + encode(title);
            String body = httpGet(url, null);
            return new JSONObject(body).optString("lyrics", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    // ── 4. azlyrics.com ───────────────────────────────────────────────────────
    private String fromAzLyrics(String artist, String title) {
        try {
            String a = slugify(nullToEmpty(artist));
            String t = slugify(nullToEmpty(title));
            if (a.isEmpty() || t.isEmpty()) return "";
            if (a.startsWith("the")) a = a.substring(3);

            Document doc = Jsoup.connect("https://www.azlyrics.com/lyrics/" + a + "/" + t + ".html")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .referrer("https://www.google.com")
                    .timeout(TIMEOUT_MS)
                    .get();

            Element container = doc.selectFirst(".col-xs-12.col-lg-8");
            if (container == null) return "";

            for (Element div : container.select("div:not([class]):not([id])")) {
                String text = div.text().trim();
                if (text.length() > 100) return cleanHtml(div.html());
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    // ── 5. genius.com ─────────────────────────────────────────────────────────
    private DocAndLyrics fromGenius(String artist, String title) {
        try {
            String q = encode(nullToEmpty(title) + " " + nullToEmpty(artist));
            Document searchDoc = Jsoup.connect("https://genius.com/search?q=" + q)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(TIMEOUT_MS)
                    .get();

            Element songLink = searchDoc.selectFirst("a.mini_card[href*='/lyrics']");
            if (songLink == null) songLink = searchDoc.selectFirst("a[href*='-lyrics']");
            if (songLink == null) return null;

            Document songDoc = Jsoup.connect(songLink.absUrl("href"))
                    .userAgent("Mozilla/5.0")
                    .timeout(TIMEOUT_MS)
                    .get();

            StringBuilder sb = new StringBuilder();
            for (Element c : songDoc.select("div[data-lyrics-container=true]"))
                sb.append(cleanHtml(c.html())).append("\n");

            return new DocAndLyrics(songDoc, sb.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String cleanHtml(String html) {
        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]*>", "")
                .replaceAll("&amp;", "&").replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'").replaceAll("&nbsp;", " ")
                .trim();
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (!l.isEmpty() && !l.toLowerCase().contains("lời bài hát"))
                sb.append(l).append("\n");
        }
        return sb.toString().trim();
    }

    private String stripLrcTimestamps(String lrc) {
        StringBuilder sb = new StringBuilder();
        for (String line : lrc.split("\n")) {
            String s = line.replaceAll("^\\[\\d+:\\d+\\.\\d+\\]\\s*", "").trim();
            if (!s.isEmpty() && !s.matches("\\[\\w+:.*\\]")) sb.append(s).append("\n");
        }
        return sb.toString().trim();
    }

    private String httpGet(String url, String ua) throws IOException {
        Request.Builder rb = new Request.Builder().url(url);
        if (!isBlank(ua)) rb.header("User-Agent", ua);
        try (Response r = HTTP.newCall(rb.build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            return r.body().string();
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String slugify(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "").trim();
    }

    private static boolean ok(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ── Inner holder ──────────────────────────────────────────────────────────
    private static class DocAndLyrics {
        final Document doc;
        final String lyrics;

        DocAndLyrics(Document doc, String lyrics) {
            this.doc = doc;
            this.lyrics = lyrics;
        }
    }
}