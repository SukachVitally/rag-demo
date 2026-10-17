# RAG demo

A minimal Retrieval-Augmented Generation (RAG) service built with Spring Boot and LangChain4j.
Upload text documents, then ask questions that are answered by a local LLM using only the content you uploaded.

Everything runs locally: [Ollama](https://ollama.com) serves the models and PostgreSQL with
[pgvector](https://github.com/pgvector/pgvector) stores the embeddings.

## How it works

```
                 ┌──────────── /api/ingest ────────────┐
 file ──► split into chunks ──► embed (nomic-embed-text) ──► pgvector (chunk_embedding)
                                                                   │
 question ──► embed ──► top 5 similar chunks ◄─────────────────────┘
                              │
                              └──► prompt with context ──► llama3.1:8b ──► answer + sources
                 └──────────── /api/ask ───────────────┘
```

- **Chunking**: LangChain4j recursive splitter (paragraph → line → sentence → word), 1000 chars with 150 chars overlap.
- **Embeddings**: `nomic-embed-text` via Ollama (768 dimensions).
- **Storage / search**: LangChain4j `PgVectorEmbeddingStore`, cosine similarity with an HNSW index.
- **Answering**: `llama3.1:8b` via Ollama, instructed to answer only from the retrieved context and cite sources as `[1]`, `[2]`, ….

## Tech stack

- Java 25, Spring Boot 4.1 (Web MVC, JDBC)
- LangChain4j 1.22 (`langchain4j`, `langchain4j-ollama`, `langchain4j-pgvector`)
- PostgreSQL 17 + pgvector
- Ollama
- Tests: JUnit 6, MockMvc, Testcontainers

## Getting started

### Prerequisites

- JDK 25
- Docker (with Docker Compose)

### 1. Start PostgreSQL and Ollama

```bash
docker compose up -d
```

This starts:

| Service       | Port  | Notes                                                         |
|---------------|-------|---------------------------------------------------------------|
| `db`          | 5432  | `pgvector/pgvector:pg17`, database/user/password: `rag`       |
| `ollama`      | 11434 | models are kept in the `ollama-models` volume                 |
| `ollama-init` | –     | one-off job that pulls `nomic-embed-text` and `llama3.1:8b`   |

The first model pull downloads several GB; wait for it to finish:

```bash
docker compose logs -f ollama-init
```

### 2. Run the application

```bash
./gradlew bootRun
```

The API is available at `http://localhost:8080`. The database schema (`src/main/resources/schema.sql`) is applied on startup.

## API

### `POST /api/ingest`

Uploads a UTF-8 text/Markdown file, splits it into chunks, embeds them and stores them.

```bash
curl -F file=@docs/notes.md http://localhost:8080/api/ingest
```

```json
{ "chunks": 9 }
```

### `POST /api/ask`

Answers a question using the most relevant ingested chunks.

```bash
curl -H 'Content-Type: application/json' \
     -d '{"question": "When does the nightly settlement batch run?"}' \
     http://localhost:8080/api/ask
```

```json
{
  "answer": "According to the context in [3] (notes.md), the nightly settlement batch runs at 02:30 UK time.",
  "sources": [
    { "source": "notes.md", "content": "...", "score": 0.79 }
  ]
}
```

`score` is LangChain4j's relevance score `(1 + cosine similarity) / 2`, in the range 0–1; sources are ordered from most to least relevant.

### Try it with the sample document

[`docs/notes.md`](docs/notes.md) is an engineering handbook of a **fictional** company. Because none of its facts exist
in the model's training data, a correct answer proves that retrieval worked. Example questions:

- Who founded Harbourline?
- What happened during "the Double Tap" incident?
- On which day does the on-call rotation change over?

## Configuration

All settings live in `src/main/resources/application.yaml` and can be overridden with environment variables or
command-line arguments (e.g. `--app.chunk.size=500`).

| Property                       | Default                                | Description                         |
|--------------------------------|----------------------------------------|-------------------------------------|
| `spring.datasource.url`        | `jdbc:postgresql://localhost:5432/rag` | PostgreSQL with pgvector            |
| `app.olama-client.base-url`    | `http://localhost:11434`               | Ollama URL                          |
| `app.olama-client.embed-model` | `nomic-embed-text`                     | Embedding model                     |
| `app.olama-client.chat-model`  | `llama3.1:8b`                          | Chat model                          |
| `app.chunk.size`               | `1000`                                 | Max chunk size in characters        |
| `app.chunk.overlap`            | `150`                                  | Overlap between chunks (characters) |

The `chunk_embedding` table has a fixed `vector(768)` column. If you switch to an embedding model with a different
dimension, update `schema.sql` and recreate the table.

## Project structure

```
src/main/java/org/example/ragdemo
├── config/LangChain4jConfig.java   # Ollama models, splitter and pgvector store beans
├── controller/RagController.java   # /api/ingest and /api/ask
├── service/IngestService.java      # split → embed → store (EmbeddingStoreIngestor)
├── service/RagService.java         # retrieve → build prompt → chat
├── dto/                            # request/response records
└── property/                       # @ConfigurationProperties records
src/main/resources/schema.sql       # pgvector extension, chunk_embedding table, HNSW index
```

## Tests

```bash
./gradlew test
```

The functional tests (`RagControllerFunctionalTest`) start the full application against a real pgvector database in
Testcontainers, so **Docker must be running**. Ollama is not needed: the embedding and chat models are replaced with
in-memory fakes (`FakeEmbeddingModel`, `FakeChatModel`). The fake embeddings are deterministic bag-of-words vectors, so
vector search still ranks relevant chunks first. Test documents are in `src/test/resources/fixtures/`.

## CI

GitHub Actions (`.github/workflows/build.yml`) runs `./gradlew build`, including the functional tests, on every push to
`main` and on pull requests. The HTML test report is uploaded as the `test-report` artifact.
