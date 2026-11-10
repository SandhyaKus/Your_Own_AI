package yourownai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import yourownai.client.OllamaClient;
import yourownai.config.AppConfig;
import yourownai.controller.Controller;
import yourownai.controller.DocumentController;
import yourownai.controller.SystemController;
import yourownai.controller.VectorController;
import yourownai.data.DemoData;
import yourownai.services.DocumentService;
import yourownai.services.VectorDBService;
import yourownai.utils.HttpUtil;
import yourownai.utils.Json;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

/** Your_Own_AI - entry point. Wires services -> controllers -> HTTP server. */
public class Main {
    public static void main(String[] args) throws IOException {
        // services
        VectorDBService db = new VectorDBService(AppConfig.DIMS);
        DocumentService docs = new DocumentService();
        OllamaClient ollama = new OllamaClient();
        DemoData.load(db);

        // controllers
        List<Controller> controllers = List.of(
                new VectorController(db),
                new DocumentController(docs, ollama),
                new SystemController(db, docs, ollama));

        boolean ollamaUp = ollama.isAvailable();
        System.out.println("=== Your_Own_AI : VectorDB Engine (Java) ===");
        System.out.println("http://localhost:" + AppConfig.PORT);
        System.out.println(db.size() + " demo vectors | " + AppConfig.DIMS + " dims | HNSW+KD-Tree+BruteForce");
        System.out.println("Ollama: " + (ollamaUp ? "ONLINE" : "OFFLINE (install from ollama.com)"));
        if (ollamaUp) System.out.println("  embed model: " + ollama.embedModel + "  gen model: " + ollama.genModel);

        HttpServer server = HttpServer.create(new InetSocketAddress(AppConfig.PORT), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", ex -> {
            try {
                dispatch(ex, controllers);
            } catch (Throwable t) {
                t.printStackTrace();
                try { HttpUtil.send(ex, 500, "application/json", "{\"error\":" + Json.str(String.valueOf(t)) + "}"); }
                catch (IOException ignored) {}
            } finally {
                ex.close();
            }
        });
        server.start();
    }

    private static void dispatch(HttpExchange ex, List<Controller> controllers) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        if (method.equals("OPTIONS")) { HttpUtil.send(ex, 204, "text/plain", ""); return; }  // CORS preflight

        String body = method.equals("POST")
                ? new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8) : "";

        for (Controller c : controllers) {
            if (c.handle(ex, method, path, body)) return;
        }
        HttpUtil.send(ex, 404, "text/plain", "Not found");
    }
}
