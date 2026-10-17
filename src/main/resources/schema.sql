CREATE EXTENSION IF NOT EXISTS vector;

-- layout expected by LangChain4j PgVectorEmbeddingStore (COMBINED_JSONB metadata)
CREATE TABLE IF NOT EXISTS chunk_embedding (
    embedding_id UUID PRIMARY KEY,
    embedding    vector(768) NOT NULL,
    text         TEXT NULL,
    metadata     JSONB NULL
);

CREATE INDEX IF NOT EXISTS chunk_embedding_hnsw
    ON chunk_embedding USING hnsw (embedding vector_cosine_ops);
