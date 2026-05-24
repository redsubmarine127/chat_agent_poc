package com.example.assistant.application.observability;

public interface ChatObservability {

    ChatTrace startChatTrace(ChatTraceContext context);
}
