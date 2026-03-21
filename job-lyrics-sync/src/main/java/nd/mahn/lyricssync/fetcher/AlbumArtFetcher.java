package nd.mahn.lyricssync.fetcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.lyricssync.fetcher.album.AlbumArtFactory;
import nd.mahn.lyricssync.fetcher.album.AlbumArtProvider;
import nd.mahn.lyricssync.result.LyricsFetchResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlbumArtFetcher {
    private final AlbumArtFactory albumArtFactory;

    public byte[] fetch(String artist, String title, LyricsFetchResult lyricsResult) {
        List<AlbumArtProvider> albumArtProviders = albumArtFactory.getAllProviders();
        if (albumArtProviders.isEmpty()) {
            throw new IllegalStateException("No albumArtProviders registered");
        }
        for (AlbumArtProvider provider : albumArtProviders) {
            if (provider == null) continue;
            log.info("[album art] provider={}", provider.getClass().getSimpleName());
            try {
                byte[] bytes = provider.fetchArt(artist, title, lyricsResult);
                if (bytes != null && bytes.length > 0) {
                    return bytes;
                }
            } catch (Exception ex) {
                log.warn("Provider {} failed while fetching album art for {} - {}: {}", provider.getClass().getSimpleName(), artist, title, ex.getMessage(), ex);
            }
        }
        return new byte[0];
    }
}
