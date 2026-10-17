package org.example.ragdemo.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory replacement for the Ollama {@link ChatModel}: replies with {@link #ANSWER}
 * and records the system and user prompt of every call.
 */
public class FakeChatModel implements ChatModel {
    public static final String ANSWER = "Fake answer from the test LLM [1]";

    public record Chat(String system, String user) {}

    private final List<Chat> calls = new CopyOnWriteArrayList<>();

    @Override
    public ChatResponse doChat(ChatRequest request) {
        String system = null, user = null;
        for (var message : request.messages()) {
            if (message instanceof SystemMessage m) system = m.text();
            if (message instanceof UserMessage m) user = m.singleText();
        }
        calls.add(new Chat(system, user));
        return ChatResponse.builder().aiMessage(AiMessage.from(ANSWER)).build();
    }

    public List<Chat> calls() { return calls; }

    public void reset() {
        calls.clear();
    }
}
