package yourownai.services;

import yourownai.algorithms.BruteForce;
import yourownai.algorithms.Distance;
import yourownai.algorithms.HNSW;
import yourownai.model.Pair;
import yourownai.model.VectorItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HNSW index over real Ollama embeddings (dimension decided by the first insert). */
public final class DocumentService {
    public record DocItem(int id, String title, String text, float[] emb) {}
    public record DocHit(float dist, DocItem doc) {}

    private final Map<Integer, DocItem> store = new LinkedHashMap<>();
    private final HNSW hnsw = new HNSW(16, 200);
    private final BruteForce bf = new BruteForce();   // exact fallback for small sets
    private int nextId = 1;
    private int dims = 0;

    public synchronized int insert(String title, String text, float[] emb) {
        if (dims == 0) dims = emb.length;
        DocItem item = new DocItem(nextId++, title, text, emb);
        store.put(item.id(), item);
        VectorItem vi = new VectorItem(item.id(), title, "doc", emb);
        hnsw.insert(vi, Distance::cosine);
        bf.insert(vi);
        return item.id();
    }

    public synchronized List<DocHit> search(float[] q, int k) { return search(q, k, 0.7f); }

    public synchronized List<DocHit> search(float[] q, int k, float maxDist) {
        List<DocHit> out = new ArrayList<>();
        if (store.isEmpty()) return out;
        List<Pair> raw = (store.size() < 10)
                ? bf.knn(q, k, Distance::cosine)
                : hnsw.knn(q, k, 50, Distance::cosine);
        for (Pair p : raw) {
            DocItem d = store.get(p.id());
            if (d != null && p.d() <= maxDist) out.add(new DocHit(p.d(), d));
        }
        return out;
    }

    public synchronized boolean remove(int id) {
        if (!store.containsKey(id)) return false;
        store.remove(id);
        hnsw.remove(id);
        bf.remove(id);
        return true;
    }

    public synchronized List<DocItem> all() { return new ArrayList<>(store.values()); }

    public synchronized int size() { return store.size(); }

    public synchronized int getDims() { return dims; }
}
