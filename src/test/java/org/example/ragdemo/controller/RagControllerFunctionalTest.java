package org.example.ragdemo.controller;

import com.jayway.jsonpath.JsonPath;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import org.example.ragdemo.support.FakeChatModel;
import org.example.ragdemo.support.FakeEmbeddingModel;
import org.example.ragdemo.support.FunctionalTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static org.example.ragdemo.config.LangChain4jConfig.CHUNK_TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RagControllerFunctionalTest extends FunctionalTest {
    private static final String HANDBOOK = "harbourline-handbook.md";
    private static final String RECIPE = "sourdough-recipe.md";

    @Autowired
    DocumentSplitter splitter;

    @Nested
    class Ingest {

        @Test
        void storesChunksWithEmbeddingsForUploadedFile() throws Exception {
            int expected = splitter.split(Document.from(fixture(HANDBOOK))).size();
            assertThat(expected).isGreaterThan(1);

            mvc.perform(multipart("/api/ingest").file(fixtureFile(HANDBOOK)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chunks").value(expected));

            assertThat(chunkCount()).isEqualTo(expected);
            assertThat(jdbc.sql("SELECT DISTINCT metadata->>'source' FROM " + CHUNK_TABLE).query(String.class).list())
                    .containsExactly(HANDBOOK);
            assertThat(jdbc.sql("SELECT (metadata->>'index')::int AS idx FROM " + CHUNK_TABLE + " ORDER BY idx")
                    .query(Integer.class).list())
                    .containsExactlyElementsOf(IntStream.range(0, expected).boxed().toList());
            assertThat(jdbc.sql("SELECT count(*) FROM " + CHUNK_TABLE + " WHERE vector_dims(embedding) = ?")
                    .param(FakeEmbeddingModel.DIMENSIONS).query(Integer.class).single())
                    .isEqualTo(expected);

            assertThat(EMBEDDINGS.calls()).singleElement().satisfies(texts -> assertThat(texts).hasSize(expected));
        }

        @Test
        void appendsChunksWhenSeveralFilesAreIngested() throws Exception {
            ingestFixture(HANDBOOK);
            ingestFixture(RECIPE);

            assertThat(jdbc.sql("SELECT DISTINCT metadata->>'source' AS source FROM " + CHUNK_TABLE + " ORDER BY source").query(String.class).list())
                    .containsExactly(HANDBOOK, RECIPE);
        }

        @Test
        void emptyFileProducesNoChunks() throws Exception {
            mvc.perform(multipart("/api/ingest").file(textFile("empty.txt", "")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chunks").value(0));

            assertThat(chunkCount()).isZero();
            assertThat(EMBEDDINGS.calls()).isEmpty();
        }

        @Test
        void rejectsRequestWithoutFile() throws Exception {
            mvc.perform(multipart("/api/ingest"))
                    .andExpect(status().isBadRequest());

            assertThat(chunkCount()).isZero();
        }
    }

    @Nested
    class Ask {

        @Test
        void answersUsingMostRelevantIngestedChunks() throws Exception {
            ingestFixture(HANDBOOK);
            ingestFixture(RECIPE);
            EMBEDDINGS.reset();

            mvc.perform(post("/api/ask")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"question": "When does the nightly settlement batch run?"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.answer").value(FakeChatModel.ANSWER))
                    .andExpect(jsonPath("$.sources", hasSize(5)))
                    .andExpect(jsonPath("$.sources[0].source").value(HANDBOOK))
                    .andExpect(jsonPath("$.sources[0].content", containsString("02:30")));

            assertThat(EMBEDDINGS.calls()).singleElement()
                    .satisfies(texts -> assertThat(texts)
                            .containsExactly("When does the nightly settlement batch run?"));

            assertThat(CHAT.calls()).singleElement().satisfies(chat -> {
                assertThat(chat.system()).contains("Answer using ONLY the provided context");
                assertThat(chat.user())
                        .startsWith("Context:\n[1] (" + HANDBOOK + ")\n")
                        .contains("02:30")
                        .endsWith("Question: When does the nightly settlement batch run?");
            });
        }

        @Test
        void returnsSourcesOrderedByScore() throws Exception {
            ingestFixture(HANDBOOK);
            ingestFixture(RECIPE);

            String body = mvc.perform(post("/api/ask")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"question": "How long should the sourdough loaf bake in the Dutch oven?"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sources[0].source").value(RECIPE))
                    .andReturn().getResponse().getContentAsString();

            List<Number> raw = JsonPath.read(body, "$.sources[*].score");
            List<Double> scores = raw.stream().map(Number::doubleValue).toList();
            assertThat(scores).isSortedAccordingTo(Comparator.reverseOrder());
        }

        @Test
        void answersWithNoSourcesWhenNothingIsIngested() throws Exception {
            mvc.perform(post("/api/ask")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"question": "Who founded Harbourline?"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.answer").value(FakeChatModel.ANSWER))
                    .andExpect(jsonPath("$.sources", empty()));

            assertThat(CHAT.calls()).singleElement()
                    .satisfies(chat -> assertThat(chat.user())
                            .isEqualTo("Context:\n\nQuestion: Who founded Harbourline?"));
        }

        @Test
        void rejectsRequestWithoutBody() throws Exception {
            mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            assertThat(CHAT.calls()).isEmpty();
        }
    }
}
