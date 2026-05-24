package com.example.assistant.application.observability;

import java.util.Map;

public interface ChatTrace {

    void event(String name, Map<String, Object> metadata);

    void complete(String output);

    void fail(String output, String errorMessage);
}
