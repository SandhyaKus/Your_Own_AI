package yourownai.algorithms;

import yourownai.model.Pair;
import yourownai.model.VectorItem;
import yourownai.services.DocumentService;
import yourownai.services.VectorDBService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

/**
 * HNSW - Hierarchical Navigable Small World graph (approximate nearest neighbours).
 * Not thread-safe by itself; callers (VectorDBService / DocumentService) synchronize.
 */
public final class HNSW {
    private static final class Node {
        final VectorItem item;
        final int maxLyr;
        final List<List<Integer>> nbrs;
        Node(VectorItem item, int maxLyr) {
            this.item = item;
            this.maxLyr = maxLyr;
            this.nbrs = new ArrayList<>();
            for (int i = 0; i <= maxLyr; i++) nbrs.add(new ArrayList<>());
        }
    }

    private final Map<Integer, Node> G = new HashMap<>();
    private final int M, M0, efBuild;
    private final float mL;
    private int topLayer = -1;
    private int entryPt = -1;
    private final Random rng = new Random(42);

    public HNSW(int m, int efBuild) {
        this.M = m;
        this.M0 = 2 * m;
        this.efBuild = efBuild;
        this.mL = (float) (1.0 / Math.log(m));
    }

    private int randLevel() {
        float u = rng.nextFloat();
        if (u <= 0f) u = Float.MIN_VALUE;
        return (int) Math.floor(-Math.log(u) * mL);
    }

    private List<Pair> searchLayer(float[] q, int ep, int ef, int lyr, Distance.Fn dist) {
        Set<Integer> vis = new HashSet<>();
        PriorityQueue<Pair> cands = new PriorityQueue<>();                              // min-heap
        PriorityQueue<Pair> found = new PriorityQueue<>(Comparator.reverseOrder());     // max-heap

        float d0 = dist.apply(q, G.get(ep).item.emb);
        vis.add(ep);
        cands.add(new Pair(d0, ep));
        found.add(new Pair(d0, ep));

        while (!cands.isEmpty()) {
            Pair c = cands.poll();
            if (found.size() >= ef && c.d() > found.peek().d()) break;
            Node cn = G.get(c.id());
            if (cn == null || lyr >= cn.nbrs.size()) continue;
            for (int nid : cn.nbrs.get(lyr)) {
                if (vis.contains(nid) || !G.containsKey(nid)) continue;
                vis.add(nid);
                float nd = dist.apply(q, G.get(nid).item.emb);
                if (found.size() < ef || nd < found.peek().d()) {
                    cands.add(new Pair(nd, nid));
                    found.add(new Pair(nd, nid));
                    if (found.size() > ef) found.poll();
                }
            }
        }
        List<Pair> res = new ArrayList<>(found);
        Collections.sort(res);
        return res;
    }

    private List<Integer> selectNbrs(List<Pair> cands, int maxM) {
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i < Math.min(cands.size(), maxM); i++) r.add(cands.get(i).id());
        return r;
    }

    public void insert(VectorItem item, Distance.Fn dist) {
        int id = item.id;
        int lvl = randLevel();
        G.put(id, new Node(item, lvl));

        if (entryPt == -1) { entryPt = id; topLayer = lvl; return; }

        int ep = entryPt;
        for (int lc = topLayer; lc > lvl; lc--) {
            if (lc < G.get(ep).nbrs.size()) {
                List<Pair> W = searchLayer(item.emb, ep, 1, lc, dist);
                if (!W.isEmpty()) ep = W.get(0).id();
            }
        }
        for (int lc = Math.min(topLayer, lvl); lc >= 0; lc--) {
            List<Pair> W = searchLayer(item.emb, ep, efBuild, lc, dist);
            int maxM = (lc == 0) ? M0 : M;
            List<Integer> sel = selectNbrs(W, maxM);
            G.get(id).nbrs.set(lc, new ArrayList<>(sel));

            for (int nid : sel) {
                Node nn = G.get(nid);
                if (nn == null) continue;
                while (nn.nbrs.size() <= lc) nn.nbrs.add(new ArrayList<>());
                List<Integer> conn = nn.nbrs.get(lc);
                conn.add(id);
                if (conn.size() > maxM) {
                    List<Pair> ds = new ArrayList<>();
                    for (int c : conn) {
                        Node cn = G.get(c);
                        if (cn != null) ds.add(new Pair(dist.apply(nn.item.emb, cn.item.emb), c));
                    }
                    Collections.sort(ds);
                    conn.clear();
                    for (int i = 0; i < maxM && i < ds.size(); i++) conn.add(ds.get(i).id());
                }
            }
            if (!W.isEmpty()) ep = W.get(0).id();
        }
        if (lvl > topLayer) { topLayer = lvl; entryPt = id; }
    }

    public List<Pair> knn(float[] q, int k, int ef, Distance.Fn dist) {
        if (entryPt == -1) return new ArrayList<>();
        int ep = entryPt;
        for (int lc = topLayer; lc > 0; lc--) {
            if (lc < G.get(ep).nbrs.size()) {
                List<Pair> W = searchLayer(q, ep, 1, lc, dist);
                if (!W.isEmpty()) ep = W.get(0).id();
            }
        }
        List<Pair> W = searchLayer(q, ep, Math.max(ef, k), 0, dist);
        if (W.size() > k) return new ArrayList<>(W.subList(0, Math.max(k, 0)));
        return W;
    }

    public void remove(int id) {
        if (!G.containsKey(id)) return;
        for (Node nd : G.values())
            for (List<Integer> layer : nd.nbrs)
                layer.removeIf(x -> x == id);
        if (entryPt == id) {
            entryPt = -1;
            for (int nid : G.keySet()) if (nid != id) { entryPt = nid; break; }
        }
        G.remove(id);
    }

    // ── Graph introspection for the UI ────────────────────────────────
    public record NV(int id, String metadata, String category, int maxLyr) {}
    public record EV(int src, int dst, int lyr) {}

    public static final class GraphInfo {
        public int topLayer, nodeCount;
        public int[] nodesPerLayer, edgesPerLayer;
        public final List<NV> nodes = new ArrayList<>();
        public final List<EV> edges = new ArrayList<>();
    }

    public GraphInfo getInfo() {
        GraphInfo gi = new GraphInfo();
        gi.topLayer = topLayer;
        gi.nodeCount = G.size();
        int maxL = Math.max(topLayer + 1, 1);
        gi.nodesPerLayer = new int[maxL];
        gi.edgesPerLayer = new int[maxL];
        for (Map.Entry<Integer, Node> e : G.entrySet()) {
            int id = e.getKey();
            Node nd = e.getValue();
            gi.nodes.add(new NV(id, nd.item.metadata, nd.item.category, nd.maxLyr));
            for (int lc = 0; lc <= nd.maxLyr && lc < maxL; lc++) {
                gi.nodesPerLayer[lc]++;
                if (lc < nd.nbrs.size())
                    for (int nid : nd.nbrs.get(lc))
                        if (id < nid) {
                            gi.edgesPerLayer[lc]++;
                            gi.edges.add(new EV(id, nid, lc));
                        }
            }
        }
        return gi;
    }

    public int size() { return G.size(); }
}
