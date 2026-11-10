package yourownai.model;

/** (distance, id) pair. Natural order = ascending distance, then id (same as std::pair). */
public record Pair(float d, int id) implements Comparable<Pair> {
    @Override
    public int compareTo(Pair o) {
        int c = Float.compare(d, o.d);
        return c != 0 ? c : Integer.compare(id, o.id);
    }
}
