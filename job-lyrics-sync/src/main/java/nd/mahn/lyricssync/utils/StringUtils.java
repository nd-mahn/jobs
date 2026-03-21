package nd.mahn.lyricssync.utils;

import lombok.experimental.UtilityClass;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@UtilityClass
public class StringUtils {

    public static String cleanHtml(String html) {
        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")   // thay <br> bằng xuống dòng
                .replaceAll("<script[^>]*>.*?</script>", "") // bỏ toàn bộ script
                .replaceAll("<[^>]*>", "")
                .replaceAll(".*adsbygoogle.*", "")// bỏ toàn bộ thẻ HTML
                .replaceAll("^\\[\\d+:\\d+\\.\\d+]\\s*", "")
                .replaceAll("\\[\\d{1,2}:\\d{2}(?:\\.\\d{2})?]", "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .trim();

        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (!l.isEmpty() && !l.toLowerCase().contains("lời bài hát")) {
                sb.append(l).append("\n");
            }
        }
        return sb.toString().trim();
    }

    public static String safeName(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    public static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static String slugify(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "").trim();
    }

    public static boolean ok(String s) {
        return s != null && !s.isBlank();
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }


    public static boolean ok(byte[] b) {
        return b != null && b.length > 0;
    }

    public static boolean equals(String newLyrics, String currentLyrics) {
        return newLyrics.equals(currentLyrics);
    }
}
