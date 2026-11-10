package yourownai.services;

import yourownai.algorithms.BruteForce;
import yourownai.algorithms.Distance;
import yourownai.algorithms.HNSW;
import yourownai.algorithms.KDTree;
import yourownai.model.Pair;
import yourownai.model.VectorItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Unified demo index (16D): BruteForce + KD-Tree + HNSW over the same data. */
public final class VectorDBService {
    private final Map<Integer, VectorItem> store = new LinkedHashMap<>();
    private final BruteForce bf = new BruteForce();
    private final KDTree kdt;
    private final HNSW hnsw = new HNSW(16, 200);
    private int nextId = 1;
    public final int dims;

    public VectorDBService(int dims) {
        this.dims = dims;
        this.kdt = new KDTree(dims);
    }

    public synchronized int insert(String meta, String cat, float[] emb, Distance.Fn dist) {
        VectorItem v = new VectorItem(nextId++, meta, cat, emb);
        store.put(v.id, v);
        bf.insert(v);
        kdt.insert(v);
        hnsw.insert(v, dist);
        return v.id;
    }

    public synchronized boolean remove(int id) {
        if (!store.containsKey(id)) return false;
        store.remove(id);
        bf.remove(id);
        hnsw.remove(id);
        kdt.rebuild(new ArrayList<>(store.values()));
        return true;
    }

    public record Hit(int id, String meta, String cat, float[] emb, float dist) {}
    public record SearchOut(List<Hit> hits, long us, String algo, String metric) {}

    public synchronized SearchOut search(float[] q, int k, String metric, String algo) {
        Distance.Fn dfn = Distance.get(metric);
        long t0 = System.nanoTime();
        List<Pair> raw;
        if ("bruteforce".equals(algo))  raw = bf.knn(q, k, dfn);
        else if ("kdtree".equals(algo)) raw = kdt.knn(q, k, dfn);
        else                            raw = hnsw.knn(q, k, 50, dfn);
        long us = (System.nanoTime() - t0) / 1000;

        List<Hit> hits = new ArrayList<>();
        for (Pair p : raw) {
            VectorItem v = store.get(p.id());
            if (v != null) hits.add(new Hit(v.id, v.metadata, v.category, v.emb, p.d()));
        }
        return new SearchOut(hits, us, algo, metric);
    }

    public record BenchOut(long bfUs, long kdUs, long hnswUs, int n) {}

    public synchronized BenchOut benchmark(float[] q, int k, String metric) {
        Distance.Fn dfn = Distance.get(metric);
        long t = System.nanoTime();
        bf.knn(q, k, dfn);
        long bfUs = (System.nanoTime() - t) / 1000;
        t = System.nanoTime();
        kdt.knn(q, k, dfn);
        long kdUs = (System.nanoTime() - t) / 1000;
        t = System.nanoTime();
        hnsw.knn(q, k, 50, dfn);
        long hnswUs = (System.nanoTime() - t) / 1000;
        return new BenchOut(bfUs, kdUs, hnswUs, store.size());
    }

    public synchronized List<VectorItem> all() { return new ArrayList<>(store.values()); }

    public synchronized HNSW.GraphInfo hnswInfo() { return hnsw.getInfo(); }

    public synchronized int size() { return store.size(); }
}
