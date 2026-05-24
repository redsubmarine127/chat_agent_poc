package com.example.assistant.infrastructure.observability;

import com.example.assistant.application.observability.ChatObservability;
import com.example.assistant.application.observability.ChatTrace;
import com.example.assistant.application.observability.ChatTraceContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NoopChatObservability implements ChatObservability {

    private static final ChatTrace NOOP_TRACE = new ChatTrace() {
        @Override
        public void event(String name, Map<String, Object> metadata) {
            // Noop by design.
        }

        @Override
        public void complete(String output) {
            // Noop by design.
        }

        @Override
        public void fail(String output, String errorMessage) {
            // Noop by design.
        }
    };

    @Override
    public ChatTrace startChatTrace(ChatTraceContext context) {
        return NOOP_TRACE;
    }
}
