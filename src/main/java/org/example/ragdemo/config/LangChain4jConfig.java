package org.example.ragdemo.config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.example.ragdemo.property.ChunkProperty;
import org.example.ragdemo.property.OllamaClientProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

@Configuration
public class LangChain4jConfig {
    public static final String CHUNK_TABLE = "chunk_embedding";

    @Bean
    EmbeddingModel embeddingModel(OllamaClientProperty property) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(property.baseUrl())
                .modelName(property.embedModel())
                .build();
    }

    @Bean
    ChatModel chatModel(OllamaClientProperty property) {
        return OllamaChatModel.builder()
                .baseUrl(property.baseUrl())
                .modelName(property.chatModel())
                .build();
    }

    @Bean
    DocumentSplitter documentSplitter(ChunkProperty property) {
        return DocumentSplitters.recursive(property.size(), property.overlap());
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(CHUNK_TABLE)
                .createTable(false)
                .skipCreateVectorExtension(true)
                .metadataStorageConfig(DefaultMetadataStorageConfig.builder()
                        .storageMode(MetadataStorageMode.COMBINED_JSONB)
                        .columnDefinitions(List.of("metadata JSONB NULL"))
                        .build())
                .build();
    }
}
