package com.songs.config;

import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public MessageWindowChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(10) // Keep the last 10 messages in memory
                .build();
    }
}