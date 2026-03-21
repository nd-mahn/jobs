package nd.mahn.lyricssync.fetcher.album;

import nd.mahn.lyricssync.result.LyricsFetchResult;

public interface AlbumArtProvider {
    byte[] fetchArt(String artist, String title, LyricsFetchResult lyricsResult);
}
