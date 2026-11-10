package yourownai.algorithms;

import yourownai.model.Pair;
import yourownai.model.VectorItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Exact k-d tree (axis-aligned binary space partitioning). */
public final class KDTree {
    private static final class Node {
        final VectorItem item;
        Node left, right;
        Node(VectorItem item) { this.item = item; }
    }

    private Node root;
    private final int dims;

    public KDTree(int dims) { this.dims = dims; }

    private Node ins(Node n, VectorItem v, int d) {
        if (n == null) return new Node(v);
        int ax = d % dims;
        if (v.emb[ax] < n.item.emb[ax]) n.left = ins(n.left, v, d + 1);
        else                            n.right = ins(n.right, v, d + 1);
        return n;
    }

    private void knn(Node n, float[] q, int k, int d, Distance.Fn dist, PriorityQueue<Pair> heap) {
        if (n == null) return;
        float dn = dist.apply(q, n.item.emb);
        if (heap.size() < k || dn < heap.peek().d()) {
            heap.add(new Pair(dn, n.item.id));
            if (heap.size() > k) heap.poll();
        }
        int ax = d % dims;
        float diff = q[ax] - n.item.emb[ax];
        Node closer  = diff < 0 ? n.left : n.right;
        Node farther = diff < 0 ? n.right : n.left;
        knn(closer, q, k, d + 1, dist, heap);
        if (heap.size() < k || Math.abs(diff) < heap.peek().d())
            knn(farther, q, k, d + 1, dist, heap);
    }

    public void insert(VectorItem v) { root = ins(root, v, 0); }

    public List<Pair> knn(float[] q, int k, Distance.Fn dist) {
        PriorityQueue<Pair> heap = new PriorityQueue<>(Comparator.reverseOrder()); // max-heap
        if (k > 0) knn(root, q, k, 0, dist, heap);
        List<Pair> r = new ArrayList<>(heap);
        Collections.sort(r);
        return r;
    }

    public void rebuild(List<VectorItem> items) {
        root = null;
        for (VectorItem v : items) insert(v);
    }
}
