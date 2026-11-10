package yourownai.controller;

import com.sun.net.httpserver.HttpExchange;
import yourownai.client.OllamaClient;
import yourownai.config.AppConfig;
import yourownai.services.DocumentService;
import yourownai.utils.HttpUtil;
import yourownai.utils.Json;
import yourownai.utils.TextChunker;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Document + RAG endpoints: /doc/insert /doc/list /doc/delete/:id /doc/search /doc/ask */
public class DocumentController implements Controller {
    private static final Pattern DELETE = Pattern.compile("^/doc/delete/(\\d+)$");

    private final DocumentService docs;
    private final OllamaClient ollama;

    public DocumentController(DocumentService docs, OllamaClient ollama) {
        this.docs = docs;
        this.ollama = ollama;
    }

    @Override
    public boolean handle(HttpExchange ex, String method, String path, String body) throws IOException {
        if (method.equals("GET") && path.equals("/doc/list")) { list(ex); return true; }
        if (method.equals("POST")) {
            switch (path) {
                case "/doc/insert" -> { insert(ex, body); return true; }
                case "/doc/search" -> { search(ex, body); return true; }
                case "/doc/ask"    -> { ask(ex, body); return true; }
                default -> { return false; }
            }
        }
        if (method.equals("DELETE")) {
            Matcher m = DELETE.matcher(path);
            if (m.matches()) { delete(ex, m.group(1)); return true; }
        }
        return false;
    }

    /** Chunk the text, embed each chunk through Ollama, store in the document index. */
    private void insert(HttpExchange ex, String body) throws IOException {
        String title = Json.extractStr(body, "title");
        String text = Json.extractStr(body, "text");
        if (title.isEmpty() || text.isEmpty()) { HttpUtil.json(ex, "{\"error\":\"need title and text\"}"); return; }

        List<String> chunks = TextChunker.chunk(text, AppConfig.CHUNK_WORDS, AppConfig.CHUNK_OVERLAP);
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            float[] emb = ollama.embed(chunks.get(i));
            if (emb.length == 0) { HttpUtil.json(ex, AppConfig.OLLAMA_HELP); return; }
            String chunkTitle = chunks.size() > 1
                    ? title + " [" + (i + 1) + "/" + chunks.size() + "]" : title;
            int id = docs.insert(chunkTitle, chunks.get(i), emb);
            if (i > 0) ids.append(',');
            ids.append(id);
        }
        HttpUtil.json(ex, "{\"ids\":[" + ids + "],\"chunks\":" + chunks.size() + ",\"dims\":" + docs.getDims() + "}");
    }

    private void delete(HttpExchange ex, String idStr) throws IOException {
        boolean ok;
        try { ok = docs.remove(Integer.parseInt(idStr)); } catch (NumberFormatException e) { ok = false; }
        HttpUtil.json(ex, "{\"ok\":" + ok + "}");
    }

    private void list(HttpExchange ex) throws IOException {
        List<DocumentService.DocItem> all = docs.all();
        StringBuilder ss = new StringBuilder("[");
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) ss.append(',');
            DocumentService.DocItem d = all.get(i);
            String preview = d.text().length() > 120 ? d.text().substring(0, 120) + "…" : d.text();
            int words = (int) d.text().chars().filter(c -> c == ' ').count() + 1;
            ss.append("{\"id\":").append(d.id())
              .append(",\"title\":").append(Json.str(d.title()))
              .append(",\"preview\":").append(Json.str(preview))
              .append(",\"words\":").append(words).append('}');
        }
        HttpUtil.json(ex, ss.append(']').toString());
    }

    /** Fast retrieval only (used by the UI visualizer). */
    private void search(HttpExchange ex, String body) throws IOException {
        String question = Json.extractStr(body, "question");
        int k = Json.extractInt(body, "k", 3);
        if (question.isEmpty()) { HttpUtil.json(ex, "{\"error\":\"need question\"}"); return; }

        float[] qEmb = ollama.embed(question);
        if (qEmb.length == 0) { HttpUtil.json(ex, "{\"error\":\"Ollama unavailable\"}"); return; }

        List<DocumentService.DocHit> hits = docs.search(qEmb, k);
        StringBuilder ss = new StringBuilder("{\"contexts\":[");
        for (int i = 0; i < hits.size(); i++) {
            if (i > 0) ss.append(',');
            DocumentService.DocHit h = hits.get(i);
            ss.append("{\"id\":").append(h.doc().id())
              .append(",\"title\":").append(Json.str(h.doc().title()))
              .append(",\"distance\":").append(Json.fixed(h.dist(), 4)).append('}');
        }
        HttpUtil.json(ex, ss.append("]}").toString());
    }

    /** Full RAG pipeline: embed question -> retrieve top-k -> build prompt -> generate. */
    private void ask(HttpExchange ex, String body) throws IOException {
        String question = Json.extractStr(body, "question");
        int k = Json.extractInt(body, "k", 3);
        if (question.isEmpty()) { HttpUtil.json(ex, "{\"error\":\"need question\"}"); return; }

        float[] qEmb = ollama.embed(question);
        if (qEmb.length == 0) { HttpUtil.json(ex, "{\"error\":\"Ollama unavailable\"}"); return; }

        List<DocumentService.DocHit> hits = docs.search(qEmb, k);

        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            ctx.append('[').append(i + 1).append("] ").append(hits.get(i).doc().title()).append(":\n")
               .append(hits.get(i).doc().text()).append("\n\n");
        }
        String prompt =
                "You are a helpful assistant. Answer the user's question directly. "
              + "Use the provided context if it contains relevant information. "
              + "If it doesn't, just use your own general knowledge. "
              + "IMPORTANT: Do NOT mention the 'context', 'provided text', or say things like 'the context doesn't mention'. "
              + "Just answer the question naturally.\n\n"
              + "Context:\n" + ctx
              + "Question: " + question + "\n\n"
              + "Answer:";

        String answer = ollama.generate(prompt);

        StringBuilder ss = new StringBuilder();
        ss.append("{\"answer\":").append(Json.str(answer))
          .append(",\"model\":").append(Json.str(ollama.genModel))
          .append(",\"contexts\":[");
        for (int i = 0; i < hits.size(); i++) {
            if (i > 0) ss.append(',');
            DocumentService.DocHit h = hits.get(i);
            ss.append("{\"id\":").append(h.doc().id())
              .append(",\"title\":").append(Json.str(h.doc().title()))
              .append(",\"text\":").append(Json.str(h.doc().text()))
              .append(",\"distance\":").append(Json.fixed(h.dist(), 4)).append('}');
        }
        ss.append("],\"docCount\":").append(docs.size()).append('}');
        HttpUtil.json(ex, ss.toString());
    }
}
