package nd.mahn.lyricssync.fetcher.lyrics;

import nd.mahn.lyricssync.result.LyricsFetchResult;

public interface LyricsProvider {
    String getName();

    LyricsFetchResult fetch(String artist, String title);
}
