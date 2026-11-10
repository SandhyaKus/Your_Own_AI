package yourownai.controller;

import com.sun.net.httpserver.HttpExchange;
import yourownai.client.OllamaClient;
import yourownai.config.AppConfig;
import yourownai.services.DocumentService;
import yourownai.services.VectorDBService;
import yourownai.utils.HttpUtil;
import yourownai.utils.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Misc endpoints: /status /stats and the web UI at "/". */
public class SystemController implements Controller {
    private final VectorDBService db;
    private final DocumentService docs;
    private final OllamaClient ollama;

    public SystemController(VectorDBService db, DocumentService docs, OllamaClient ollama) {
        this.db = db;
        this.docs = docs;
        this.ollama = ollama;
    }

    @Override
    public boolean handle(HttpExchange ex, String method, String path, String body) throws IOException {
        if (!method.equals("GET")) return false;
        switch (path) {
            case "/status" -> status(ex);
            case "/stats"  -> stats(ex);
            case "/"       -> index(ex);
            default -> { return false; }
        }
        return true;
    }

    private void status(HttpExchange ex) throws IOException {
        boolean up = ollama.isAvailable();
        HttpUtil.json(ex, "{\"ollamaAvailable\":" + up
                + ",\"embedModel\":" + Json.str(ollama.embedModel)
                + ",\"genModel\":" + Json.str(ollama.genModel)
                + ",\"docCount\":" + docs.size()
                + ",\"docDims\":" + docs.getDims()
                + ",\"demoDims\":" + AppConfig.DIMS
                + ",\"demoCount\":" + db.size() + "}");
    }

    private void stats(HttpExchange ex) throws IOException {
        HttpUtil.json(ex, "{\"count\":" + db.size() + ",\"dims\":" + AppConfig.DIMS
                + ",\"algorithms\":[\"bruteforce\",\"kdtree\",\"hnsw\"]"
                + ",\"metrics\":[\"euclidean\",\"cosine\",\"manhattan\"]}");
    }

    private void index(HttpExchange ex) throws IOException {
        Path f = Path.of(AppConfig.FRONTEND_FILE);
        if (!Files.exists(f)) {
            HttpUtil.send(ex, 404, "text/plain",
                    AppConfig.FRONTEND_FILE + " not found - run the server from the project root");
            return;
        }
        HttpUtil.send(ex, 200, "text/html; charset=utf-8", Files.readString(f, StandardCharsets.UTF_8));
    }
}
