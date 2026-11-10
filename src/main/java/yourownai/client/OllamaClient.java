package yourownai.client;

import yourownai.config.AppConfig;
import yourownai.utils.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Minimal client for the local Ollama REST API (/api/tags, /api/embeddings, /api/generate). */
public final class OllamaClient {
    private final String base;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public String embedModel = AppConfig.EMBED_MODEL;
    public String genModel   = AppConfig.GEN_MODEL;

    public OllamaClient() { this(AppConfig.OLLAMA_HOST, AppConfig.OLLAMA_PORT); }

    public OllamaClient(String host, int port) { this.base = "http://" + host + ":" + port; }

    public boolean isAvailable() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/tags"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String post(String path, String json, int timeoutSec) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return res.statusCode() == 200 ? res.body() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns an empty array if Ollama is not running or the model is missing. */
    public float[] embed(String text) {
        String body = "{\"model\":" + Json.str(embedModel) + ",\"prompt\":" + Json.str(text) + "}";
        String res = post("/api/embeddings", body, 30);
        if (res == null) return new float[0];
        return Json.extractArr(res, "embedding");
    }

    /** Returns an error string if Ollama is unavailable. */
    public String generate(String prompt) {
        String body = "{\"model\":" + Json.str(genModel) + ",\"prompt\":" + Json.str(prompt)
                + ",\"stream\":false}";
        String res = post("/api/generate", body, 180);   // LLMs can be slow
        if (res == null) return "ERROR: Ollama unavailable. Run: ollama serve";
        return Json.extractStr(res, "response");
    }
}
