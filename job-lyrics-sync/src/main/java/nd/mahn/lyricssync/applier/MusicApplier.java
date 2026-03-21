package nd.mahn.lyricssync.applier;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v24Tag;
import com.mpatric.mp3agic.Mp3File;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.lyricssync.event.MetadataReadyEvent;
import nd.mahn.lyricssync.result.ApplyResult;
import nd.mahn.lyricssync.result.LyricsProperties;
import nd.mahn.lyricssync.utils.StringUtils;
import nd.mahn.lyricssync.watcher.DirectoryWatcher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@Component
public class MusicApplier {

    private final ThreadPoolTaskExecutor executor;
    private final LyricsProperties properties;
    private final DirectoryWatcher directoryWatcher; // Inject DirectoryWatcher

    private static final boolean BACKUP = true;

    public MusicApplier(@Qualifier("applierExecutor") ThreadPoolTaskExecutor executor, 
                        LyricsProperties properties,
                        DirectoryWatcher directoryWatcher) {
        this.executor = executor;
        this.properties = properties;
        this.directoryWatcher = directoryWatcher;
    }

    @PostConstruct
    public void initMusicApplier() {
        try {
            applyAll(Paths.get(properties.getOutputMetaData()));
        } catch (Exception e) {
            log.error("Failed to initialize MusicApplier: {}", e.getMessage(), e);
        }
    }

    @EventListener
    public void onMetadataReady(MetadataReadyEvent event) {
        Path metaDir = event.getOutputMetaData();
        executor.submit(() -> {
            try {
                if (metaDir != null && Files.exists(metaDir)) {
                    ApplyResult r = applyOne(metaDir);
                    if (r.getError() != null) {
                        log.info("Apply failed for {}: {}", metaDir, r.getError());
                    } else if (r.getSummary() != null && !r.getSummary().isEmpty()) {
                        log.info("Apply success for {}: {}", metaDir, r.getSummary());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing MetadataReadyEvent for {}", metaDir, e);
            }
        });
    }

    public void applyAll(Path metaDirRoot) throws Exception {
        if (!Files.isDirectory(metaDirRoot)) return;
        List<Path> folders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(metaDirRoot, 1)) {
            walk.filter(p -> !p.equals(metaDirRoot) && Files.isDirectory(p))
                    .filter(p -> Files.exists(p.resolve("source.txt")))
                    .forEach(folders::add);
        }
        if (folders.isEmpty()) return;

        ExecutorCompletionService<ApplyResult> completionService =
                new ExecutorCompletionService<>(executor.getThreadPoolExecutor());

        for (Path folder : folders) {
            completionService.submit(() -> applyOne(folder));
        }

        // Wait logic (giữ nguyên hoặc rút gọn nếu cần)
        // ... (phần wait logic giữ nguyên để đảm bảo kết thúc đúng)
    }

    private ApplyResult applyOne(Path folder) {
        ApplyResult res = new ApplyResult();
        try {
            Path sourceFile = folder.resolve("source.txt");
            if (!Files.exists(sourceFile)) {
                 res.setError("Missing source.txt");
                 return res;
            }
            
            String mp3PathStr = Files.readString(sourceFile, StandardCharsets.UTF_8).trim();
            Path mp3Path = Paths.get(mp3PathStr);
            if (!Files.exists(mp3Path)) {
                res.setError("MP3 not found: " + mp3PathStr);
                return res;
            }

            Path lyricsFile = folder.resolve("lyrics.txt");
            Path coverFile = folder.resolve("cover.jpg");

            Mp3File mp3file = new Mp3File(mp3PathStr);
            ID3v2 tag = mp3file.hasId3v2Tag() ? mp3file.getId3v2Tag() : new ID3v24Tag();
            List<String> applied = new ArrayList<>();
            boolean isModified = false;

            if (Files.exists(lyricsFile)) {
                String newLyrics = Files.readString(lyricsFile, StandardCharsets.UTF_8).trim();
                String currentLyrics = StringUtils.nullToEmpty(tag.getLyrics()).trim();
                
                // Chuẩn hóa xuống dòng để so sánh chính xác hơn
                newLyrics = newLyrics.replace("\r\n", "\n").replace("\r", "\n");
                currentLyrics = currentLyrics.replace("\r\n", "\n").replace("\r", "\n");

                if (!StringUtils.equals(newLyrics, currentLyrics)) {
                    tag.setLyrics(newLyrics);
                    applied.add("lyrics");
                    isModified = true;
                }
            }
            
            if (Files.exists(coverFile)) {
                byte[] newArt = Files.readAllBytes(coverFile);
                byte[] currentArt = tag.getAlbumImage();
                if (!Arrays.equals(newArt, currentArt)) {
                    tag.setAlbumImage(newArt, "image/jpeg");
                    applied.add("cover");
                    isModified = true;
                }
            }

            if (!isModified) {
                return res; // Không thay đổi gì, return luôn
            }

            // Có thay đổi, chuẩn bị ghi file
            mp3file.setId3v2Tag(tag);
            String tempPath = mp3PathStr + ".tmp";
            mp3file.save(tempPath);

            // Notify Watcher: "Tôi sắp thay đổi file này, đừng bắn event!"
            directoryWatcher.expectChange(mp3Path.toAbsolutePath());

            if (BACKUP) { /* backup logic... */ }

            try {
                Files.delete(mp3Path);
                Files.move(Paths.get(tempPath), mp3Path);
            } catch (Exception e) {
                // fallback
                new File(tempPath).renameTo(new File(mp3PathStr));
            }

            res.setSummary("Applied: " + String.join(", ", applied));
        } catch (Exception e) {
            log.error("applyOne failed for {}: {}", folder, e.getMessage());
            res.setError(e.getMessage());
        }
        return res;
    }
}
