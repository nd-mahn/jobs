package nd.mahn.lyricssync.watcher;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.lyricssync.publisher.FileEventPublisher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DirectoryWatcher {
    private final FileEventPublisher publisher;
    
    private final ExecutorService watcherExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService fileProcessorExecutor = Executors.newCachedThreadPool();

    private WatchService watchService;

    // Map để theo dõi các file đang được xử lý (debounce)
    private final ConcurrentHashMap<String, Boolean> processingFiles = new ConcurrentHashMap<>();
    
    // Set để lưu các file được phép thay đổi (do chính Applier sửa), Watcher sẽ bỏ qua các file này
    private final Set<String> expectedChanges = ConcurrentHashMap.newKeySet();

    public DirectoryWatcher(FileEventPublisher publisher) {
        this.publisher = publisher;
    }

    // MusicApplier sẽ gọi hàm này trước khi ghi file
    public void expectChange(Path file) {
        String key = file.toAbsolutePath().toString();
        expectedChanges.add(key);
        log.debug("Expecting change for {}, will ignore next event", key);
    }

    public void startWatching(Path dir) throws IOException {
        if (watchService != null) return;
        watchService = FileSystems.getDefault().newWatchService();
        dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);

        watcherExecutor.submit(() -> {
            log.info("Directory watcher started for {}", dir);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = watchService.poll(5, TimeUnit.SECONDS);
                    if (key != null) {
                        processEvents(key, dir);
                        if (!key.reset()) break;
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                log.info("Directory watcher stopped for {}", dir);
            }
        });
    }

    private void processEvents(WatchKey key, Path dir) {
        for (WatchEvent<?> ev : key.pollEvents()) {
            if (ev.kind() == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }
            Path context = (Path) ev.context();
            Path child = dir.resolve(context);
            
            if (isTargetFile(child)) {
                handleFileEventAsync(child);
            }
        }
    }

    private boolean isTargetFile(Path path) {
        return path.toString().toLowerCase().endsWith(".mp3");
    }

    private void handleFileEventAsync(Path child) {
        String fileKey = child.toAbsolutePath().toString();

        // 1. Kiểm tra xem file này có nằm trong danh sách "được phép thay đổi" không
        if (expectedChanges.remove(fileKey)) {
            log.info("Ignored self-induced change event for: {}", child.getFileName());
            return; // Bỏ qua event này
        }

        // 2. Debounce logic
        if (processingFiles.putIfAbsent(fileKey, Boolean.TRUE) != null) {
            return;
        }

        fileProcessorExecutor.submit(() -> {
            try {
                if (waitForStable(child)) {
                    log.info("Publishing file created event for {}", child);
                    publisher.publishFileCreated(child);
                }
            } catch (Exception e) {
                log.error("Error handling file event for {}", child, e);
            } finally {
                processingFiles.remove(fileKey);
            }
        });
    }

    private boolean waitForStable(Path file) throws InterruptedException {
        long previous = -1;
        for (int i = 0; i < 5; i++) {
            if (!Files.exists(file)) return false;
            try {
                long size = Files.size(file);
                if (size == previous && size > 0) return true;
                previous = size;
            } catch (IOException e) {
                // ignore
            }
            Thread.sleep(500);
        }
        return false;
    }

    @PreDestroy
    public void stop() throws IOException {
        watcherExecutor.shutdownNow();
        fileProcessorExecutor.shutdownNow();
        if (watchService != null) watchService.close();
    }
}
