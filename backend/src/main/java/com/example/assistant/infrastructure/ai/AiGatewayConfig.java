package com.example.assistant.infrastructure.ai;

import com.example.assistant.application.chat.ChatModelGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiGatewayConfig {

    @Bean
    public ChatModelGateway chatModelGateway() {
        return new RoutingChatModelGateway(
                new LocalFallbackChatModelGateway(),
                new OpenAiCompatibleChatModelGateway()
        );
    }
}
