package yourownai.model;

/** A stored vector with metadata (used by all three indexes). */
public final class VectorItem {
    public final int id;
    public final String metadata;
    public final String category;
    public final float[] emb;

    public VectorItem(int id, String metadata, String category, float[] emb) {
        this.id = id;
        this.metadata = metadata;
        this.category = category;
        this.emb = emb;
    }
}
