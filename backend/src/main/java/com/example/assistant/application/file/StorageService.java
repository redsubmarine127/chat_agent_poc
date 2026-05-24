package com.example.assistant.application.file;

import com.example.assistant.domain.file.Attachment;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface StorageService {

    Mono<Attachment> store(FilePart filePart);

    Mono<Void> delete(String storageKey);
}
