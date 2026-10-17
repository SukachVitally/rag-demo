package org.example.ragdemo.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.example.ragdemo.dto.Hit;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagService {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;
    private final ChatModel chatModel;

    private static final String SYSTEM = """
        Answer using ONLY the provided context. If the answer is not in the
        context, say you don't know. Cite sources as [1], [2]...""";

    public record Answer(String answer, List<Hit> sources) {}

    public Answer ask(String question) {
        var q = embeddingModel.embed(question).content();
        var hits = store.search(EmbeddingSearchRequest.builder().queryEmbedding(q).maxResults(5).build())
                .matches().stream()
                .map(m -> new Hit(m.embedded().metadata().getString(IngestService.SOURCE), m.embedded().text(), m.score()))
                .toList();

        var ctx = new StringBuilder();
        for (int i = 0; i < hits.size(); i++)
            ctx.append("[").append(i + 1).append("] (").append(hits.get(i).source()).append(")\n")
                    .append(hits.get(i).content()).append("\n\n");

        String prompt = "Context:\n" + ctx + "\nQuestion: " + question;
        return new Answer(chatModel.chat(SystemMessage.from(SYSTEM), UserMessage.from(prompt)).aiMessage().text(), hits);
    }
}
