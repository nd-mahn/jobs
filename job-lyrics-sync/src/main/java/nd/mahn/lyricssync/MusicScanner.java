package dev.m.music;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Phase 1 – Quét đệ quy thư mục MP3, fetch lyrics + art song song.
 * Kết quả lưu vào output_metadata/<key>/ để review trước khi apply.
 * <p>
 * output_metadata/
 * TênBài_Nghệsĩ/
 * source.txt    ← path tuyệt đối file MP3 gốc
 * lyrics.txt    ← review/sửa tại đây
 * cover.jpg     ← kiểm tra tại đây
 * meta.txt      ← nguồn lấy được (để debug)
 */
public class MusicScanner {

    private static final int THREADS = Runtime.getRuntime().availableProcessors() * 2;
    private static final String OUTPUT_DIR = "output_metadata";

    private final LyricsFetcher lyricsFetcher = new LyricsFetcher();
    private final AlbumArtFetcher albumArtFetcher = new AlbumArtFetcher();

    // ── main ──────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        String rootDir = args.length > 0 ? args[0] : "maven/data";
        new MusicScanner().scan(rootDir);
    }

    public void scan(String rootDir) throws Exception {
        List<Path> mp3s = findMp3Files(rootDir);
        System.out.printf("Tìm thấy %d file MP3 trong '%s'%n%n", mp3s.size(), rootDir);
        Files.createDirectories(Paths.get(OUTPUT_DIR));

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<ScanResult>> futures = new ArrayList<>();
        AtomicInteger done = new AtomicInteger(0);

        for (Path mp3 : mp3s) {
            futures.add(pool.submit(() -> {
                ScanResult r = processFile(mp3);
                System.out.printf("[%d/%d] %-45s lyrics=%-12s art=%-20s%s%n",
                        done.incrementAndGet(), mp3s.size(),
                        mp3.getFileName(),
                        r.lyricsSource != null ? r.lyricsSource : "—",
                        r.artSource != null ? r.artSource : "—",
                        r.error != null ? " ✗ " + r.error : "");
                return r;
            }));
        }

        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.HOURS);

        // Tổng kết
        int okLyrics = 0, okArt = 0, errors = 0;
        for (Future<ScanResult> f : futures) {
            ScanResult r = f.get();
            if (r.error != null) errors++;
            if (r.lyricsSource != null) okLyrics++;
            if (r.artSource != null) okArt++;
        }
        System.out.printf("%n=== TỔNG KẾT ===%nLyrics: %d/%d | Ảnh: %d/%d | Lỗi: %d%n",
                okLyrics, mp3s.size(), okArt, mp3s.size(), errors);
    }

    // ── Quét đệ quy ───────────────────────────────────────────────────────────
    private List<Path> findMp3Files(String root) throws IOException {
        List<Path> list = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Paths.get(root))) {
            walk.filter(p -> p.toString().toLowerCase().endsWith(".mp3"))
                    .forEach(list::add);
        }
        return list;
    }

    // ── Xử lý 1 file ─────────────────────────────────────────────────────────
    private ScanResult processFile(Path mp3Path) {
        ScanResult res = new ScanResult(mp3Path);
        try {
            Mp3File mp3file = new Mp3File(mp3Path.toString());
            String artist = "", title = "";

            if (mp3file.hasId3v2Tag()) {
                ID3v2 tag = mp3file.getId3v2Tag();
                artist = nullToEmpty(tag.getArtist());
                title = nullToEmpty(tag.getTitle());
            }
            if (title.isBlank())
                title = mp3Path.getFileName().toString().replaceAll("(?i)\\.mp3$", "");

            // Thư mục output cho bài này
            String key = safeName(title + (artist.isBlank() ? "" : "_" + artist));
            Path outDir = Paths.get(OUTPUT_DIR, key);
            Files.createDirectories(outDir);

            // Ghi path MP3 gốc → Phase 2 đọc lại
            Files.writeString(outDir.resolve("source.txt"),
                    mp3Path.toAbsolutePath().toString(), StandardCharsets.UTF_8);

            // ── Lyrics ──────────────────────────────────────────────
            LyricsFetchResult lyricsResult = lyricsFetcher.fetch(artist, title);
            if (lyricsResult.hasLyrics()) {
                Files.writeString(outDir.resolve("lyrics.txt"),
                        lyricsResult.lyrics, StandardCharsets.UTF_8);
                res.lyricsSource = lyricsResult.source;
            }

            // ── Album art — truyền lyricsResult để tái dùng Document ──
            byte[] art = albumArtFetcher.fetch(artist, title, lyricsResult);
            if (art != null) {
                Files.write(outDir.resolve("cover.jpg"), art);
                res.artSource = "ok"; // AlbumArtFetcher đã log chi tiết nguồn
            }

            // Ghi meta debug
            String meta = String.format("title=%s%nartist=%s%nlyrics_source=%s%n",
                    title, artist, lyricsResult.source);
            Files.writeString(outDir.resolve("meta.txt"), meta, StandardCharsets.UTF_8);

        } catch (Exception e) {
            res.error = e.getMessage();
        }
        return res;
    }

    private static String safeName(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static class ScanResult {
        final Path source;
        String lyricsSource, artSource, error;

        ScanResult(Path src) {
            this.source = src;
        }
    }
}