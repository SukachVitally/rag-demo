package org.example.ragdemo.support;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.example.ragdemo.config.LangChain4jConfig.CHUNK_TABLE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base fixture for functional tests: full Spring context, real pgvector database in a container,
 * in-memory fake Ollama {@link EmbeddingModel} and {@link ChatModel}, and an empty chunk table before every test.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class FunctionalTest {

    // Started once for the whole run and shared by every subclass, matching Spring's cached context;
    // Ryuk removes it when the JVM exits.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    protected static final FakeEmbeddingModel EMBEDDINGS = new FakeEmbeddingModel();
    protected static final FakeChatModel CHAT = new FakeChatModel();

    @TestBean(methodName = "fakeEmbeddingModel")
    EmbeddingModel embeddingModel;

    @TestBean(methodName = "fakeChatModel")
    ChatModel chatModel;

    static EmbeddingModel fakeEmbeddingModel() {
        return EMBEDDINGS;
    }

    static ChatModel fakeChatModel() {
        return CHAT;
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void resetState() {
        jdbc.sql("TRUNCATE " + CHUNK_TABLE).update();
        EMBEDDINGS.reset();
        CHAT.reset();
    }

    protected static String fixture(String name) throws IOException {
        return new ClassPathResource("fixtures/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    protected static MockMultipartFile fixtureFile(String name) throws IOException {
        return textFile(name, fixture(name));
    }

    protected static MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/markdown", content.getBytes(StandardCharsets.UTF_8));
    }

    protected void ingestFixture(String name) throws Exception {
        mvc.perform(multipart("/api/ingest").file(fixtureFile(name))).andExpect(status().isOk());
    }

    protected int chunkCount() {
        return jdbc.sql("SELECT count(*) FROM " + CHUNK_TABLE).query(Integer.class).single();
    }
}
