package org.example.ragdemo.support;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory replacement for the Ollama {@link EmbeddingModel}.
 * <p>
 * Embeddings are deterministic bag-of-words vectors (stop words dropped, feature hashing into 768 dims, L2-normalised),
 * so texts that share words end up close in cosine space and vector search behaves meaningfully.
 * Every call is recorded for assertions.
 */
public class FakeEmbeddingModel implements EmbeddingModel {
    public static final int DIMENSIONS = 768;
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "are", "was", "with", "that", "this", "from", "has", "have", "how", "what",
            "when", "where", "who", "why", "does", "did", "its", "into", "not", "only", "any", "all", "each");

    private final List<List<String>> calls = new CopyOnWriteArrayList<>();

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        var texts = segments.stream().map(TextSegment::text).toList();
        calls.add(texts);
        return Response.from(texts.stream().map(t -> Embedding.from(embedding(t))).toList());
    }

    @Override
    public int dimension() {
        return DIMENSIONS;
    }

    /** Texts passed to each embedding call, in call order. */
    public List<List<String>> calls() { return calls; }

    public void reset() {
        calls.clear();
    }

    public static float[] embedding(String text) {
        float[] v = new float[DIMENSIONS];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() < 3 || STOP_WORDS.contains(token)) continue;
            v[Math.floorMod(token.hashCode(), DIMENSIONS)] += 1;
        }
        double norm = 0;
        for (float x : v) norm += x * x;
        if (norm == 0) {
            v[0] = 1;                                                    // pgvector cosine needs a non-zero vector
            return v;
        }
        float n = (float) Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) v[i] /= n;
        return v;
    }
}
