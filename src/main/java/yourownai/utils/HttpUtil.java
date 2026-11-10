package yourownai.utils;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Small helpers for writing responses and reading query parameters. */
public final class HttpUtil {
    private HttpUtil() {}

    public static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().set("Content-Type", type);
        if (status == 204) { ex.sendResponseHeaders(204, -1); return; }
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
    }

    public static void json(HttpExchange ex, String body) throws IOException {
        send(ex, 200, "application/json; charset=utf-8", body);
    }

    public static Map<String, String> queryParams(HttpExchange ex) {
        Map<String, String> m = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return m;
        for (String pair : q.split("&")) {
            if (pair.isEmpty()) continue;
            int i = pair.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            m.putIfAbsent(k, v);
        }
        return m;
    }

    public static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    public static String orDefault(String s, String def) { return (s == null || s.isEmpty()) ? def : s; }
}
