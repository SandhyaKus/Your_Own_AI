package yourownai.algorithms;

import yourownai.model.Pair;
import yourownai.model.VectorItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** O(N*d) exact search. Baseline. */
public final class BruteForce {
    public final List<VectorItem> items = new ArrayList<>();

    public void insert(VectorItem v) { items.add(v); }

    public List<Pair> knn(float[] q, int k, Distance.Fn dist) {
        List<Pair> r = new ArrayList<>(items.size());
        for (VectorItem v : items) r.add(new Pair(dist.apply(q, v.emb), v.id));
        Collections.sort(r);
        if (r.size() > k) return new ArrayList<>(r.subList(0, Math.max(k, 0)));
        return r;
    }

    public void remove(int id) { items.removeIf(v -> v.id == id); }
}
