package dev.m.music;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v24Tag;
import com.mpatric.mp3agic.Mp3File;

import java.io.File;
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
 * Phase 2 – Đọc output_metadata/, apply lyrics + cover đã review vào MP3.
 * Để bỏ 1 bài: xóa folder hoặc xóa file lyrics.txt/cover.jpg tương ứng.
 */
public class MusicApplier {

    private static final int THREADS = Runtime.getRuntime().availableProcessors() * 2;
    private static final String META_DIR = "output_metadata";
    private static final boolean BACKUP = true; // tạo .bak trước khi ghi đè

    public static void main(String[] args) throws Exception {
        new MusicApplier().applyAll(META_DIR);
    }

    public void applyAll(String metaDir) throws Exception {
        List<Path> folders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Paths.get(metaDir), 1)) {
            walk.filter(p -> !p.equals(Paths.get(metaDir)) && Files.isDirectory(p))
                    .filter(p -> Files.exists(p.resolve("source.txt")))
                    .forEach(folders::add);
        }
        System.out.printf("Tìm thấy %d bài cần apply%n%n", folders.size());

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<ApplyResult>> futures = new ArrayList<>();
        AtomicInteger done = new AtomicInteger(0);

        for (Path folder : folders) {
            futures.add(pool.submit(() -> {
                ApplyResult r = applyOne(folder);
                System.out.printf("[%d/%d] %-45s %s%n",
                        done.incrementAndGet(), folders.size(), folder.getFileName(),
                        r.error != null ? "✗ " + r.error : "✓ " + r.summary);
                return r;
            }));
        }

        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.HOURS);

        int ok = 0, err = 0;
        for (Future<ApplyResult> f : futures) {
            if (f.get().error != null) err++;
            else ok++;
        }
        System.out.printf("%n=== TỔNG KẾT ===%nThành công: %d | Lỗi: %d%n", ok, err);
    }

    private ApplyResult applyOne(Path folder) {
        ApplyResult res = new ApplyResult();
        try {
            String mp3Path = Files.readString(folder.resolve("source.txt"), StandardCharsets.UTF_8).trim();
            if (!Files.exists(Paths.get(mp3Path))) {
                res.error = "Không tìm thấy file: " + mp3Path;
                return res;
            }

            Path lyricsFile = folder.resolve("lyrics.txt");
            Path coverFile = folder.resolve("cover.jpg");

            if (!Files.exists(lyricsFile) && !Files.exists(coverFile)) {
                res.error = "Không có gì để apply";
                return res;
            }

            Mp3File mp3file = new Mp3File(mp3Path);
            ID3v2 tag = mp3file.hasId3v2Tag() ? mp3file.getId3v2Tag() : new ID3v24Tag();
            List<String> applied = new ArrayList<>();

            if (Files.exists(lyricsFile)) {
                tag.setLyrics(Files.readString(lyricsFile, StandardCharsets.UTF_8));
                applied.add("lyrics");
            }
            if (Files.exists(coverFile)) {
                tag.setAlbumImage(Files.readAllBytes(coverFile), "image/jpeg");
                applied.add("cover");
            }

            mp3file.setId3v2Tag(tag);
            String tempPath = mp3Path + ".tmp";
            mp3file.save(tempPath);

            if (BACKUP) {
                Path bak = Paths.get(mp3Path + ".bak");
                if (!Files.exists(bak)) {
                    Files.copy(Paths.get(mp3Path), bak);
                }
            }
            Files.delete(Paths.get(mp3Path));
            new File(tempPath).renameTo(new File(mp3Path));
            res.summary = "Applied: " + String.join(", ", applied);
        } catch (Exception e) {
            res.error = e.getMessage();
        }
        return res;
    }

    static class ApplyResult {
        String summary, error;
    }
}