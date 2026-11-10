package yourownai.controller;

import com.sun.net.httpserver.HttpExchange;
import yourownai.algorithms.Distance;
import yourownai.algorithms.HNSW;
import yourownai.config.AppConfig;
import yourownai.model.VectorItem;
import yourownai.services.VectorDBService;
import yourownai.utils.HttpUtil;
import yourownai.utils.Json;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Demo-vector endpoints: /search /insert /delete/:id /items /benchmark /hnsw-info */
public class VectorController implements Controller {
    private static final int DIMS = AppConfig.DIMS;
    private static final Pattern DELETE = Pattern.compile("^/delete/(\\d+)$");

    private final VectorDBService db;

    public VectorController(VectorDBService db) { this.db = db; }

    @Override
    public boolean handle(HttpExchange ex, String method, String path, String body) throws IOException {
        if (method.equals("GET")) {
            switch (path) {
                case "/search"    -> { search(ex); return true; }
                case "/items"     -> { items(ex); return true; }
                case "/benchmark" -> { benchmark(ex); return true; }
                case "/hnsw-info" -> { hnswInfo(ex); return true; }
                default -> { return false; }
            }
        }
        if (method.equals("POST") && path.equals("/insert")) { insert(ex, body); return true; }
        if (method.equals("DELETE")) {
            Matcher m = DELETE.matcher(path);
            if (m.matches()) { delete(ex, m.group(1)); return true; }
        }
        return false;
    }

    private void search(HttpExchange ex) throws IOException {
        Map<String, String> p = HttpUtil.queryParams(ex);
        float[] q = Json.parseVec(p.get("v"));
        if (q.length != DIMS) { HttpUtil.json(ex, "{\"error\":\"need " + DIMS + "D vector\"}"); return; }
        int k = HttpUtil.parseInt(p.getOrDefault("k", ""), 5);
        String metric = HttpUtil.orDefault(p.get("metric"), "cosine");
        String algo = HttpUtil.orDefault(p.get("algo"), "hnsw");

        VectorDBService.SearchOut out = db.search(q, k, metric, algo);
        StringBuilder ss = new StringBuilder("{\"results\":[");
        for (int i = 0; i < out.hits().size(); i++) {
            if (i > 0) ss.append(',');
            VectorDBService.Hit h = out.hits().get(i);
            ss.append("{\"id\":").append(h.id())
              .append(",\"metadata\":").append(Json.str(h.meta()))
              .append(",\"category\":").append(Json.str(h.cat()))
              .append(",\"distance\":").append(Json.fixed(h.dist(), 6))
              .append(",\"embedding\":").append(Json.vec(h.emb())).append('}');
        }
        ss.append("],\"latencyUs\":").append(out.us())
          .append(",\"algo\":").append(Json.str(out.algo()))
          .append(",\"metric\":").append(Json.str(out.metric())).append('}');
        HttpUtil.json(ex, ss.toString());
    }

    private void insert(HttpExchange ex, String body) throws IOException {
        String meta = Json.extractStr(body, "metadata");
        String cat = Json.extractStr(body, "category");
        float[] emb = Json.extractArr(body, "embedding");
        if (meta.isEmpty() || emb.length != DIMS) {
            HttpUtil.json(ex, "{\"error\":\"invalid body\"}");
            return;
        }
        int id = db.insert(meta, cat, emb, Distance.get("cosine"));
        HttpUtil.json(ex, "{\"id\":" + id + "}");
    }

    private void delete(HttpExchange ex, String idStr) throws IOException {
        boolean ok;
        try { ok = db.remove(Integer.parseInt(idStr)); } catch (NumberFormatException e) { ok = false; }
        HttpUtil.json(ex, "{\"ok\":" + ok + "}");
    }

    private void items(HttpExchange ex) throws IOException {
        List<VectorItem> items = db.all();
        StringBuilder ss = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) ss.append(',');
            VectorItem v = items.get(i);
            ss.append("{\"id\":").append(v.id)
              .append(",\"metadata\":").append(Json.str(v.metadata))
              .append(",\"category\":").append(Json.str(v.category))
              .append(",\"embedding\":").append(Json.vec(v.emb)).append('}');
        }
        HttpUtil.json(ex, ss.append(']').toString());
    }

    private void benchmark(HttpExchange ex) throws IOException {
        Map<String, String> p = HttpUtil.queryParams(ex);
        float[] q = Json.parseVec(p.get("v"));
        if (q.length != DIMS) { HttpUtil.json(ex, "{\"error\":\"need " + DIMS + "D vector\"}"); return; }
        int k = HttpUtil.parseInt(p.getOrDefault("k", ""), 5);
        String metric = HttpUtil.orDefault(p.get("metric"), "cosine");
        VectorDBService.BenchOut b = db.benchmark(q, k, metric);
        HttpUtil.json(ex, "{\"bruteforceUs\":" + b.bfUs() + ",\"kdtreeUs\":" + b.kdUs()
                + ",\"hnswUs\":" + b.hnswUs() + ",\"itemCount\":" + b.n() + "}");
    }

    private void hnswInfo(HttpExchange ex) throws IOException {
        HNSW.GraphInfo gi = db.hnswInfo();
        StringBuilder ss = new StringBuilder();
        ss.append("{\"topLayer\":").append(gi.topLayer)
          .append(",\"nodeCount\":").append(gi.nodeCount)
          .append(",\"nodesPerLayer\":[");
        for (int i = 0; i < gi.nodesPerLayer.length; i++) { if (i > 0) ss.append(','); ss.append(gi.nodesPerLayer[i]); }
        ss.append("],\"edgesPerLayer\":[");
        for (int i = 0; i < gi.edgesPerLayer.length; i++) { if (i > 0) ss.append(','); ss.append(gi.edgesPerLayer[i]); }
        ss.append("],\"nodes\":[");
        for (int i = 0; i < gi.nodes.size(); i++) {
            if (i > 0) ss.append(',');
            HNSW.NV n = gi.nodes.get(i);
            ss.append("{\"id\":").append(n.id())
              .append(",\"metadata\":").append(Json.str(n.metadata()))
              .append(",\"category\":").append(Json.str(n.category()))
              .append(",\"maxLyr\":").append(n.maxLyr()).append('}');
        }
        ss.append("],\"edges\":[");
        for (int i = 0; i < gi.edges.size(); i++) {
            if (i > 0) ss.append(',');
            HNSW.EV e = gi.edges.get(i);
            ss.append("{\"src\":").append(e.src()).append(",\"dst\":").append(e.dst())
              .append(",\"lyr\":").append(e.lyr()).append('}');
        }
        ss.append("]}");
        HttpUtil.json(ex, ss.toString());
    }
}
