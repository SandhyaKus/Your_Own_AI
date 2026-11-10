package yourownai.config;

/** Central place for all settings. Change a value here, recompile, restart. */
public final class AppConfig {
    private AppConfig() {}

    // Server
    public static final int PORT = 8080;
    public static final String FRONTEND_FILE = "frontend/index.html";   // relative to project root

    // Demo vectors
    public static final int DIMS = 16;

    // Ollama
    public static final String OLLAMA_HOST = "127.0.0.1";
    public static final int OLLAMA_PORT = 11434;
    public static final String EMBED_MODEL = "nomic-embed-text";
    public static final String GEN_MODEL = "llama3.2";        // use "llama3.2:1b" for a faster/smaller model

    // Document chunking
    public static final int CHUNK_WORDS = 250;
    public static final int CHUNK_OVERLAP = 30;

    public static final String OLLAMA_HELP =
            "{\"error\":\"Ollama unavailable. Install from https://ollama.com then run: "
          + "ollama pull nomic-embed-text && ollama pull llama3.2\"}";
}
