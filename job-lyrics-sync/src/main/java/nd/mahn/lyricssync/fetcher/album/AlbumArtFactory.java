package nd.mahn.lyricssync.fetcher.album;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AlbumArtFactory {
    private final List<AlbumArtProvider> providers;

    @Autowired
    public AlbumArtFactory(List<AlbumArtProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public List<AlbumArtProvider> getAllProviders() {
        return providers;
    }
}