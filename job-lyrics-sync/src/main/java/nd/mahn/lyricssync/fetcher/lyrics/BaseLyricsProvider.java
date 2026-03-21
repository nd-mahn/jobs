package nd.mahn.lyricssync.fetcher.lyrics;

import lombok.extern.slf4j.Slf4j;
import nd.mahn.lyricssync.result.LyricsFetchResult;
import nd.mahn.lyricssync.utils.StringUtils;

@Slf4j
public abstract class BaseLyricsProvider implements LyricsProvider {

    @Override
    public LyricsFetchResult fetch(String artist, String title) {
        try {
            LyricsFetchResult result = fetchInternal(artist, title);
            if (result != null && result.hasLyrics()) {
                return result;
            }
        } catch (Exception e) {
            log.error("Error fetching lyrics from {} for {} - {}", getName(), artist, title, e);
        }
        return new LyricsFetchResult("", null, "none");
    }

    protected abstract LyricsFetchResult fetchInternal(String artist, String title) throws Exception;

    protected LyricsFetchResult lyrics(String lyrics) {
        return new LyricsFetchResult(StringUtils.nullToEmpty(lyrics), null, getName());
    }
}
