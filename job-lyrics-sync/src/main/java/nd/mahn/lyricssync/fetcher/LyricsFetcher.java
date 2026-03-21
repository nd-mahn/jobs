package nd.mahn.lyricssync.fetcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.lyricssync.fetcher.lyrics.LyricsFactory;
import nd.mahn.lyricssync.fetcher.lyrics.LyricsProvider;
import nd.mahn.lyricssync.result.LyricsFetchResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LyricsFetcher {
    private final LyricsFactory providers;

    public LyricsFetchResult fetch(String artist, String title) {
        List<LyricsProvider> lyricsProviders = providers.getAllProviders();
        if (lyricsProviders.isEmpty()) {
            throw new IllegalStateException("No LyricsProvider registered");
        }
        for (LyricsProvider provider : lyricsProviders) {
            LyricsFetchResult result = provider.fetch(artist, title);
            if (result != null && result.hasLyrics()) {
                log.debug("Found lyrics from provider {} for {} - {}", provider.getClass().getSimpleName(), artist, title);
                return result;
            }
        }
        return new LyricsFetchResult("", null, "none");
    }
}
