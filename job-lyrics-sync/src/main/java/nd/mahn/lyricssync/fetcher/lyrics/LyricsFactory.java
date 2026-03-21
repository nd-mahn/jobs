package nd.mahn.lyricssync.fetcher.lyrics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LyricsFactory {
    private final List<LyricsProvider> providers;
    private final Map<String, LyricsProvider> registry;

    @Autowired
    public LyricsFactory(List<LyricsProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.registry = buildRegistry(this.providers);
    }

    private static Map<String, LyricsProvider> buildRegistry(List<LyricsProvider> providers) {
        return providers.stream()
                .flatMap(provider -> Stream.of(provider.getName())
                        .filter(Objects::nonNull)
                        .map(name -> new AbstractMap.SimpleEntry<>(name, provider)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (existing, replacement) -> existing
                ));
    }

    public LyricsProvider getProvider(String name) {
        return registry.get(name);
    }
    public List<LyricsProvider> getAllProviders() {
        return providers;
    }
    public Set<String> getAllKeyProvider() {
        return registry.keySet();
    }
}