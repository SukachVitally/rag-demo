package org.example.ragdemo.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class IngestService {
    public static final String SOURCE = "source";

    private final DocumentSplitter splitter;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;

    public int ingest(String source, String text) {
        if (text.isBlank()) return 0;                                  // Document.from rejects blank text
        var chunks = new AtomicInteger();
        EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .textSegmentTransformer(segment -> { chunks.incrementAndGet(); return segment; })
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(Document.from(text, Metadata.from(SOURCE, source)));
        return chunks.get();
    }
}
