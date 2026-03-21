package nd.mahn.lyricssync.scanner;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.lyricssync.event.FileCreatedEvent;
import nd.mahn.lyricssync.fetcher.AlbumArtFetcher;
import nd.mahn.lyricssync.fetcher.LyricsFetcher;
import nd.mahn.lyricssync.publisher.MetadataReadyPublisher;
import nd.mahn.lyricssync.result.LyricsFetchResult;
import nd.mahn.lyricssync.result.LyricsProperties;
import nd.mahn.lyricssync.result.ScanResult;
import nd.mahn.lyricssync.result.SummaryResult;
import nd.mahn.lyricssync.utils.StringUtils;
import nd.mahn.lyricssync.watcher.DirectoryWatcher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    private final DirectoryWatcher directoryWatcher;
    private final ScanResultCollector collector;
    private final MetadataReadyPublisher metadataReadyPublisher;

    // Sử dụng ConcurrentHashMap để tracking các file đang được xử lý
    private final ConcurrentHashMap<Path, Boolean> processingFiles = new ConcurrentHashMap<>();

    public MusicScanner(@Qualifier("scannerExecutor") ThreadPoolTaskExecutor executor,
                        LyricsProperties properties,
                        LyricsFetcher lyricsFetcher,
                        AlbumArtFetcher albumArtFetcher, DirectoryWatcher directoryWatcher, ScanResultCollector collector, MetadataReadyPublisher metadataReadyPublisher) {
        this.executor = executor;
        this.properties = properties;
        this.lyricsFetcher = lyricsFetcher;
        this.albumArtFetcher = albumArtFetcher;
        this.directoryWatcher = directoryWatcher;
        this.collector = collector;
        this.metadataReadyPublisher = metadataReadyPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // initial scan
        executor.submit(() -> {
            try {
                scan(properties.getPathScanner());
            } catch (Exception e) {
                log.error("Initial scan failed", e);
            }
        });

        // start watcher
        try {
            Path dir = Paths.get(properties.getPathScanner()).toAbsolutePath().normalize();
            directoryWatcher.startWatching(dir);
        } catch (IOException e) {
            log.error("Failed to start directory watcher", e);
        }
    }

    @EventListener
    public void onFileCreated(FileCreatedEvent event) {
        Path file = event.getFile();
        log.info("Received FileCreatedEvent for {}", file);
        
        // Kiểm tra xem file có đang được xử lý không
        if (processingFiles.putIfAbsent(file, true) != null) {
            log.warn("File is already being processed, skipping: {}", file);
            return;
        }

        // submit single file processing to the scanner executor
        executor.submit(() -> {
            try {
                // optional: check file still exists and is mp3
                if (Files.exists(file) && file.toString().toLowerCase().endsWith(".mp3")) {
                    processFile(file);
                } else {
                    log.warn("File not found or not mp3: {}", file);
                }
            } catch (Exception e) {
                log.error("Error processing file from event {}", file, e);
            } finally {
                processingFiles.remove(file); // Xóa khỏi map khi xong
            }
        });
    }

    public void scan(String rootDir) throws Exception {
        Path pathRootDir = Paths.get(rootDir).toAbsolutePath().normalize();
        if (!Files.exists(pathRootDir)) {
            Files.createDirectories(pathRootDir);
            log.info("Created missing scanner root: {}", pathRootDir);
        }

        Path pathOutputMetaData = Paths.get(properties.getOutputMetaData()).toAbsolutePath().normalize();
        if (!Files.exists(pathOutputMetaData)) {
            Files.createDirectories(pathOutputMetaData);
            log.info("Created missing output metadata directory: {}", pathOutputMetaData);
        }

        List<Path> mp3s = findMp3Files(pathRootDir.toString());
        log.info("Found {} MP3 files in '{}'", mp3s.size(), pathRootDir);

        if (mp3s.isEmpty()) {
            log.info("No MP3 files to process.");
            return;
        }

        CompletionService<ScanResult> cs = submitAll(mp3s);
        SummaryResult summary = collector.collect(cs, mp3s.size());
        
        log.info("[=== SUMMARY ===] Lyrics: {}/{} | Art: {}/{} | Errors: {}", summary.getOkLyrics(), mp3s.size(), summary.getOkArt(), mp3s.size(), summary.getErrors());
    }

    private CompletionService<ScanResult> submitAll(List<Path> mp3s) {
        ExecutorService pool = executor.getThreadPoolExecutor();
        CompletionService<ScanResult> futures = new ExecutorCompletionService<>(pool);
        AtomicInteger done = new AtomicInteger(0);
        for (Path mp3 : mp3s) {
            futures.submit(createTask(mp3, mp3s.size(), done));
        }
        return futures;
    }

    private Callable<ScanResult> createTask(Path mp3, int total, AtomicInteger done) {
        return () -> {
            ScanResult r = processFile(mp3);
            log.info("Processed [{}/{}]: file='{}', lyrics='{}', art='{}', error={}",
                    done.incrementAndGet(), total,
                    mp3.getFileName(),
                    r.getLyricsSource() != null ? r.getLyricsSource() : "—",
                    r.getArtSource() != null ? r.getArtSource() : "—",
                    r.getError() != null ? r.getError() : "none");
            return r;
        };
    }

    private List<Path> findMp3Files(String root) throws IOException {
        Path rootPath = Paths.get(root).toAbsolutePath().normalize();
        List<Path> list = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(rootPath)) {
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

            String key = StringUtils.safeName(title + (artist.isBlank() ? "" : "_" + artist));
            Path outDir = Paths.get(properties.getOutputMetaData(), key).toAbsolutePath().normalize();
            Files.createDirectories(outDir);

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

            String meta = String.format("title=%s%nartist=%s%nlyrics_source=%s%n", title, artist, lyricsResult.getSource());
            Files.writeString(outDir.resolve("meta.txt"), meta, StandardCharsets.UTF_8);

            // Bắn event nếu có dữ liệu mới (lyrics hoặc art)
            if (res.getLyricsSource() != null || res.getArtSource() != null) {
                 metadataReadyPublisher.publishMetadataReadyDone(outDir);
            }

        } catch (Exception e) {
            log.error("Error processing file {}", mp3Path, e);
            res.setError(e.getMessage());
        }
        return res;
    }
}
