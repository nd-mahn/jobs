package nd.mahn.lyricssync.scanner;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.lyricssync.fetcher.AlbumArtFetcher;
import nd.mahn.lyricssync.fetcher.LyricsFetcher;
import nd.mahn.lyricssync.result.LyricsFetchResult;
import nd.mahn.lyricssync.result.LyricsProperties;
import nd.mahn.lyricssync.result.ScanResult;
import nd.mahn.lyricssync.result.SummaryResult;
import nd.mahn.lyricssync.utils.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@Component
public class MusicScanner {

    private final ThreadPoolTaskExecutor executor;
    private final LyricsProperties properties;
    private final LyricsFetcher lyricsFetcher;
    private final AlbumArtFetcher albumArtFetcher;

    public MusicScanner(@Qualifier("scannerExecutor") ThreadPoolTaskExecutor executor,
                        LyricsProperties properties,
                        LyricsFetcher lyricsFetcher,
                        AlbumArtFetcher albumArtFetcher) {
        this.executor = executor;
        this.properties = properties;
        this.lyricsFetcher = lyricsFetcher;
        this.albumArtFetcher = albumArtFetcher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initMusicScanner() {
        System.out.println("initMusicScanner called");
        log.info("About to submit scan task");
        try {
            executor.submit(() -> {
                try {
                    scan(properties.getPathScanner());
                } catch (Exception e) {
                    log.error("Scan failed", e);
                }
            });
            log.info("Submitted scan task");
        } catch (Exception e) {
            log.error("Failed to submit scan task", e);
        }
    }

    public void scan(String rootDir) throws Exception {
        List<Path> mp3s = findMp3Files(rootDir);
        log.info("Tìm thấy {} file MP3 trong '{}'", mp3s.size(), rootDir);
        Files.createDirectories(Paths.get(properties.getOutputMetaData()));

        if (mp3s.isEmpty()) {
            log.info("Không có file MP3 để xử lý.");
            return;
        }
        CompletionService<ScanResult> cs = submitAll(mp3s);
        SummaryResult summary = collectSummary(cs, mp3s);
        log.info("=== TỔNG KẾT ===\nLyrics: {}/{} | Ảnh: {}/{} | Lỗi: {}", summary.getOkLyrics(), mp3s.size(), summary.getOkArt(), mp3s.size(), summary.getErrors());
    }

    private CompletionService<ScanResult> submitAll(List<Path> mp3s) {
        ExecutorService pool = executor.getThreadPoolExecutor();
        CompletionService<ScanResult> futures = new ExecutorCompletionService<>(pool);
        AtomicInteger done = new AtomicInteger(0);
        // submit tasks
        for (Path mp3 : mp3s) {
            futures.submit(createTask(mp3, mp3s.size(), done));
        }
        return futures;
    }

    private Callable<ScanResult> createTask(Path mp3, int total, AtomicInteger done) {
        return () -> {
            ScanResult r = processFile(mp3);
            log.info("Đã xử lý [{}/{}]: file='{}', lyrics='{}', art='{}', error={}", done.incrementAndGet(), total, mp3.getFileName(), r.getLyricsSource() != null ? r.getLyricsSource() : "—", r.getArtSource() != null ? r.getArtSource() : "—", r.getError() != null ? r.getError() : "none");
            return r;
        };
    }

    private SummaryResult collectSummary(CompletionService<ScanResult> cs, List<Path> mp3s) {
        int processed = 0;
        int okLyrics = 0;
        int okArt = 0;
        int errors = 0;
        long perTaskTimeoutSec = 60; // timeout chờ mỗi task hoàn thành (tùy chỉnh)
        while (processed < mp3s.size()) {
            Future<ScanResult> future;
            try {
                future = cs.poll(perTaskTimeoutSec, TimeUnit.SECONDS);
                if (future == null) {
                    log.warn("Timeout chờ task hoàn thành sau {}s (processed={}/{})", perTaskTimeoutSec, processed, mp3s.size());
                    continue;
                }
                ScanResult r = future.get();
                processed++;
                if (r.getError() != null) errors++;
                if (r.getLyricsSource() != null) okLyrics++;
                if (r.getArtSource() != null) okArt++;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Thread bị interrupt khi chờ kết quả, dừng thu thập kết quả");
                break;
            } catch (ExecutionException ee) {
                processed++;
                errors++;
                log.error("Task ném exception", ee.getCause());
            }
        }
        return new SummaryResult(okLyrics, okArt, errors);
    }

    private List<Path> findMp3Files(String root) throws IOException {
        List<Path> list = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Paths.get(root))) {
            walk.filter(p -> p.toString().toLowerCase().endsWith(".mp3")).forEach(list::add);
        }
        return list;
    }

    private ScanResult processFile(Path mp3Path) {
        ScanResult res = new ScanResult(mp3Path);
        try {
            Mp3File mp3file = new Mp3File(mp3Path.toString());
            String artist = "";
            String title = "";

            if (mp3file.hasId3v2Tag()) {
                ID3v2 tag = mp3file.getId3v2Tag();
                artist = StringUtils.nullToEmpty(tag.getArtist());
                title = StringUtils.nullToEmpty(tag.getTitle());
            }
            if (title.isBlank()) title = mp3Path.getFileName().toString().replaceAll("(?i)\\.mp3$", "");

            // Thư mục output cho bài này
            String key = StringUtils.safeName(title + (artist.isBlank() ? "" : "_" + artist));
            Path outDir = Paths.get(properties.getOutputMetaData(), key);
            Files.createDirectories(outDir);

            // Ghi path MP3 gốc → Phase 2 đọc lại
            Files.writeString(outDir.resolve("source.txt"), mp3Path.toAbsolutePath().toString(), StandardCharsets.UTF_8);

            LyricsFetchResult lyricsResult = lyricsFetcher.fetch(artist, title);
            if (lyricsResult.hasLyrics()) {
                Files.writeString(outDir.resolve("lyrics.txt"), lyricsResult.getLyrics(), StandardCharsets.UTF_8);
                res.setLyricsSource(lyricsResult.getSource());
            }

            byte[] art = albumArtFetcher.fetch(artist, title, lyricsResult);
            if (art != null) {
                Files.write(outDir.resolve("cover.jpg"), art);
                res.setArtSource("OK");
            }

            // Ghi meta debug
            String meta = String.format("title=%s%nartist=%s%nlyrics_source=%s%n", title, artist, lyricsResult.getSource());
            Files.writeString(outDir.resolve("meta.txt"), meta, StandardCharsets.UTF_8);

        } catch (Exception e) {
            res.setError(e.getMessage());
        }
        return res;
    }
}