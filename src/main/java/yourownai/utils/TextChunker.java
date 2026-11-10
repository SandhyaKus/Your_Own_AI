package yourownai.utils;

import java.util.ArrayList;
import java.util.List;

/** Splits text into overlapping word chunks. */
public final class TextChunker {
    private TextChunker() {}

    public static List<String> chunk(String text, int chunkWords, int overlapWords) {
        List<String> chunks = new ArrayList<>();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return chunks;
        String[] words = trimmed.split("\\s+");
        if (words.length <= chunkWords) { chunks.add(text); return chunks; }

        int step = chunkWords - overlapWords;
        for (int i = 0; i < words.length; i += step) {
            int end = Math.min(i + chunkWords, words.length);
            chunks.add(String.join(" ", java.util.Arrays.copyOfRange(words, i, end)));
            if (end == words.length) break;
        }
        return chunks;
    }
}
