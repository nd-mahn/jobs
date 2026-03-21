package nd.mahn.lyricssync.scanner;

import lombok.extern.slf4j.Slf4j;
import nd.mahn.lyricssync.result.ScanResult;
import nd.mahn.lyricssync.result.SummaryResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ScanResultCollector {

    public SummaryResult collect(CompletionService<ScanResult> cs, int totalTasks) {
        SummaryResult summary = new SummaryResult(0, 0, 0, null);
        int processed = 0;

        while (processed < totalTasks && !Thread.currentThread().isInterrupted()) {
            try {
                processNextResult(cs, summary);
                processed++;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for results, stopping collection");
            } catch (ExecutionException ee) {
                processed++;
                summary.incrementErrors();
                log.error("Task threw an exception", ee.getCause());
            }
        }
        return summary;
    }

    private void processNextResult(CompletionService<ScanResult> cs, SummaryResult summary) throws InterruptedException, ExecutionException {
        Future<ScanResult> future = cs.poll(60, TimeUnit.SECONDS);
        if (future != null) {
            ScanResult r = future.get();
            updateSummary(r, summary);
        }
    }

    private void updateSummary(ScanResult r, SummaryResult summary) {
        if (r.getError() != null) {
            summary.incrementErrors();
        }
        if (r.getLyricsSource() != null) {
            summary.incrementOkLyrics();
        }
        if (r.getArtSource() != null) {
            summary.incrementOkArt();
        }
        summary.setOutputMetadata(r.getOutputMetadata());
    }
}
