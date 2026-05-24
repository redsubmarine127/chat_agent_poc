package com.example.assistant.application.file;

import com.example.assistant.application.chat.BusinessException;
import com.example.assistant.application.chat.ErrorCode;
import com.example.assistant.infrastructure.config.AssistantProperties;
import com.example.assistant.infrastructure.storage.LocalStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalStorageServiceTest {

    @Test
    void storeShouldRejectUnsupportedContentType() throws Exception {
        AssistantProperties properties = new AssistantProperties(
                new AssistantProperties.Storage(Files.createTempDirectory("assistant-test").toString(), 128, List.of("text/plain")),
                List.of(new AssistantProperties.SkillConfig("general", "通用助手", "通用能力", true)),
                "local-fallback",
                List.of(new AssistantProperties.ModelConfig("local-fallback", "本地回退模型", "local", "local-fallback", "", "", true))
        );
        LocalStorageService storageService = new LocalStorageService(properties);
        FilePart filePart = mockFilePart("a.exe", MediaType.APPLICATION_OCTET_STREAM_VALUE, "hello");

        StepVerifier.create(storageService.store(filePart))
                .expectErrorMatches(throwable -> throwable instanceof BusinessException businessException
                        && businessException.errorCode() == ErrorCode.FILE_TYPE_NOT_ALLOWED)
                .verify();
    }

    @Test
    void storeShouldRejectTooLargeFile() throws Exception {
        Path storageRoot = Files.createTempDirectory("assistant-test");
        AssistantProperties properties = new AssistantProperties(
                new AssistantProperties.Storage(storageRoot.toString(), 4, List.of("text/plain")),
                List.of(new AssistantProperties.SkillConfig("general", "通用助手", "通用能力", true)),
                "local-fallback",
                List.of(new AssistantProperties.ModelConfig("local-fallback", "本地回退模型", "local", "local-fallback", "", "", true))
        );
        LocalStorageService storageService = new LocalStorageService(properties);
        FilePart filePart = mockFilePart("a.txt", MediaType.TEXT_PLAIN_VALUE, "hello");

        StepVerifier.create(storageService.store(filePart))
                .expectErrorMatches(throwable -> throwable instanceof BusinessException businessException
                        && businessException.errorCode() == ErrorCode.FILE_TOO_LARGE)
                .verify();

        try (var uploadedFiles = Files.list(storageRoot)) {
            org.assertj.core.api.Assertions.assertThat(uploadedFiles).isEmpty();
        }
    }

    @Test
    void storeShouldAcceptContentTypeWithCharset() throws Exception {
        AssistantProperties properties = new AssistantProperties(
                new AssistantProperties.Storage(Files.createTempDirectory("assistant-test").toString(), 128, List.of("text/plain")),
                List.of(new AssistantProperties.SkillConfig("general", "通用助手", "通用能力", true)),
                "local-fallback",
                List.of(new AssistantProperties.ModelConfig("local-fallback", "本地回退模型", "local", "local-fallback", "", "", true))
        );
        LocalStorageService storageService = new LocalStorageService(properties);
        FilePart filePart = mockFilePart("a.txt", "text/plain;charset=UTF-8", "hello");

        StepVerifier.create(storageService.store(filePart))
                .expectNextMatches(attachment -> "text/plain".equals(attachment.contentType()))
                .verifyComplete();
    }

    private FilePart mockFilePart(String filename, String contentType, String content) {
        FilePart filePart = mock(FilePart.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        when(filePart.filename()).thenReturn(filename);
        when(filePart.headers()).thenReturn(headers);
        when(filePart.content()).thenReturn(Flux.just(bufferFactory.wrap(content.getBytes())));
        return filePart;
    }
}
