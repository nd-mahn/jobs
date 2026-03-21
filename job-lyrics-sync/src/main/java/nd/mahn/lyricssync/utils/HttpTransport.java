package nd.mahn.lyricssync.utils;

import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class HttpTransport {
    private final OkHttpClient okHttpClient;

    public String httpGet(String url, String ua) throws IOException {
        Request.Builder rb = new Request.Builder().url(url);
        if (!StringUtils.isBlank(ua)) rb.header("User-Agent", ua);
        try (Response r = okHttpClient.newCall(rb.build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            return r.body().string();
        }
    }

    public String httpGet(String url) throws IOException {
        Request.Builder rb = new Request.Builder().url(url);
        if (!StringUtils.isBlank("MusicScanner/1.0")) rb.header("User-Agent", "MusicScanner/1.0");
        try (Response r = okHttpClient.newCall(rb.build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            assert r.body() != null;
            return r.body().string();
        }
    }
    public byte[] downloadBytes(String urlStr) throws IOException {
        Request req = new Request.Builder().url(urlStr).build();
        try (Response r = okHttpClient.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            return r.body().bytes();
        }
    }
}
