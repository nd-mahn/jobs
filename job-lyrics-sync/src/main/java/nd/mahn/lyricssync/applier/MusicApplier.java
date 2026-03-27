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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.stream.Stream;

@Slf4j
@Component
public class MusicApplier {

    private static final String SOURCE_FILE = "source.txt";
    private static final String LYRICS_FILE = "lyrics.txt";
    private static final String COVER_FILE = "cover.jpg";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String BACKUP_SUFFIX = ".bak";

    private final ThreadPoolTaskExecutor executor;
    private final LyricsProperties properties;
    private final DirectoryWatcher directoryWatcher;

    public MusicApplier(
            @Qualifier("applierExecutor") ThreadPoolTaskExecutor executor,
            LyricsProperties properties,
            DirectoryWatcher directoryWatcher) {
        this.executor = executor;
        this.properties = properties;
        this.directoryWatcher = directoryWatcher;
    }

    @PostConstruct
    public void initMusicApplier() {
        executor.submit(() -> {
            try {
                applyAll(Paths.get(properties.getOutputMetaData()));
            } catch (Exception e) {
                log.error("Failed to initialize MusicApplier: {}", e.getMessage(), e);
            }
        });
    }

    @EventListener
    public void onMetadataReady(MetadataReadyEvent event) {
        Path metaDir = event.getOutputMetaData();
        if (metaDir == null || !Files.exists(metaDir)) return;

        executor.submit(() -> {
            try {
                ApplyResult result = applyOne(metaDir);
                logResult(metaDir.toString(), result);
            } catch (Exception e) {
                log.error("Error processing MetadataReadyEvent for {}", metaDir, e);
            }
        });
    }

    public void applyAll(Path metaDirRoot) {
        try {
            if (!Files.isDirectory(metaDirRoot)) return;

            List<Path> folders = collectFolders(metaDirRoot);
            if (folders.isEmpty()) return;

            ExecutorCompletionService<ApplyResult> completionService =
                    new ExecutorCompletionService<>(executor.getThreadPoolExecutor());

            for (Path folder : folders) {
                completionService.submit(() -> applyOne(folder));
            }

            for (int i = 0; i < folders.size(); i++) {
                Future<ApplyResult> future = completionService.take();
                ApplyResult result = future.get();
                logResult(result.getFolder(), result);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("applyAll interrupted: {}", e.getMessage());
        } catch (ExecutionException | IOException e) {
            log.error("applyAll failed: {}", e.getMessage(), e);
        }
    }

    private ApplyResult applyOne(Path folder) {
        ApplyResult res = new ApplyResult();
        res.setFolder(folder.toString());

        try {
            Path sourceFile = folder.resolve(SOURCE_FILE);
            if (!Files.exists(sourceFile)) {
                res.setError("Missing " + SOURCE_FILE);
                return res;
            }

            String mp3PathStr = Files.readString(sourceFile, StandardCharsets.UTF_8).trim();
            Path mp3Path = Paths.get(mp3PathStr);
            if (!Files.exists(mp3Path)) {
                res.setError("MP3 not found: " + mp3PathStr);
                return res;
            }

            Mp3File mp3file = new Mp3File(mp3PathStr);
            ID3v2 tag = mp3file.hasId3v2Tag() ? mp3file.getId3v2Tag() : new ID3v24Tag();

            List<String> applied = new ArrayList<>();
            boolean isModifiedLyric = applyLyricsIfChanged(folder, tag, applied);
            boolean isModifiedImage = applyImageIfChanged(folder, tag, applied);

            if (!isModifiedLyric && !isModifiedImage) return res;

            mp3file.setId3v2Tag(tag);
            Path tempPath = Path.of(mp3PathStr + TEMP_SUFFIX);
            mp3file.save(tempPath.toString());

            try {
                directoryWatcher.expectChange(mp3Path.toAbsolutePath());
                backupFile(mp3Path);
                Files.move(tempPath, mp3Path, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Files.deleteIfExists(tempPath);
                throw e;
            }

            res.setSummary("Applied: " + String.join(", ", applied));

        } catch (Exception e) {
            log.error("applyOne failed for {}: {}", folder, e.getMessage());
            res.setError(e.getMessage());
        }
        return res;
    }

    private boolean applyLyricsIfChanged(Path folder, ID3v2 tag, List<String> applied) {
        try {
            Path lyricsFile = folder.resolve(LYRICS_FILE);
            if (!Files.exists(lyricsFile)) return false;

            String newLyrics = normalize(Files.readString(lyricsFile, StandardCharsets.UTF_8).trim());
            String currentLyrics = normalize(StringUtils.nullToEmpty(tag.getLyrics()).trim());

            if (StringUtils.equals(newLyrics, currentLyrics)) return false;

            tag.setLyrics(newLyrics);
            applied.add("lyrics");
            return true;

        } catch (IOException e) {
            log.warn("Failed to apply lyrics from {}: {}", folder, e.getMessage());
            return false;
        }
    }

    private boolean applyImageIfChanged(Path folder, ID3v2 tag, List<String> applied) {
        try {
            Path coverFile = folder.resolve(COVER_FILE);
            if (!Files.exists(coverFile)) return false;

            byte[] newArt = Files.readAllBytes(coverFile);
            byte[] currentArt = tag.getAlbumImage();

            if (Arrays.equals(newArt, currentArt)) return false;

            tag.setAlbumImage(newArt, "image/jpeg");
            applied.add("cover");
            return true;

        } catch (IOException e) {
            log.warn("Failed to apply image from {}: {}", folder, e.getMessage());
            return false;
        }
    }

    private void backupFile(Path mp3Path) {
        Path bakDir = Paths.get(properties.getPathBackup());
        try {
            Files.createDirectories(bakDir);
            Path bak = bakDir.resolve(mp3Path.getFileName().toString() + BACKUP_SUFFIX);
            if (!Files.exists(bak)) {
                Files.copy(mp3Path, bak);
            }
        } catch (IOException e) {
            log.warn("Failed to backup {}: {}", mp3Path, e.getMessage());
        }
    }

    private List<Path> collectFolders(Path metaDirRoot) throws IOException {
        List<Path> folders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(metaDirRoot, 1)) {
            walk.filter(p -> !p.equals(metaDirRoot) && Files.isDirectory(p))
                    .filter(p -> Files.exists(p.resolve(SOURCE_FILE)))
                    .forEach(folders::add);
        }
        return folders;
    }

    private void logResult(String context, ApplyResult result) {
        if (result.getError() != null) {
            log.warn("Apply failed for {}: {}", context, result.getError());
        } else if (result.getSummary() != null && !result.getSummary().isEmpty()) {
            log.info("Apply success for {}: {}", context, result.getSummary());
        }
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
}