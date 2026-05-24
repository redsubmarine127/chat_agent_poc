package com.example.assistant.infrastructure.storage;

import com.example.assistant.application.chat.BusinessException;
import com.example.assistant.application.chat.ErrorCode;
import com.example.assistant.application.file.StorageService;
import com.example.assistant.domain.file.Attachment;
import com.example.assistant.infrastructure.config.AssistantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LocalStorageService implements StorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalStorageService.class);
    private static final int MAX_SAFE_FILENAME_LENGTH = 180;

    private final Path rootPath;
    private final long maxFileSize;
    private final Set<String> allowedContentTypes;

    public LocalStorageService(AssistantProperties properties) {
        this.rootPath = properties.storage().rootPathValue();
        this.maxFileSize = properties.storage().maxFileSize();
        this.allowedContentTypes = Set.copyOf(properties.storage().allowedContentTypes());
    }

    @Override
    public Mono<Attachment> store(FilePart filePart) {
        String originalFilename = sanitizeFilename(filePart.filename());
        String contentType = resolveContentType(filePart);
        if (!allowedContentTypes.contains(contentType)) {
            return Mono.error(new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED));
        }

        UUID attachmentId = UUID.randomUUID();
        String storageKey = attachmentId + "-" + originalFilename;
        Path targetPath = rootPath.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootPath)) {
            return Mono.error(new BusinessException(ErrorCode.INVALID_FILE_NAME));
        }

        AtomicLong totalBytes = new AtomicLong();
        Mono<Path> ensureDirectoryMono = Mono.fromCallable(() -> Files.createDirectories(rootPath))
                .subscribeOn(Schedulers.boundedElastic());

        return ensureDirectoryMono.then(
                        DataBufferUtils.write(
                                filePart.content().doOnNext(dataBuffer -> countBytes(dataBuffer, totalBytes)),
                                targetPath,
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE
                        )
                )
                .thenReturn(new Attachment(
                        attachmentId,
                        originalFilename,
                        contentType,
                        storageKey,
                        totalBytes.get(),
                        Instant.now()
                ))
                .doOnError(throwable -> deleteQuietly(targetPath, originalFilename))
                .onErrorMap(throwable -> {
                    if (throwable instanceof BusinessException businessException) {
                        return businessException;
                    }
                    LOGGER.error("store file failed, filename={}, contentType={}", originalFilename, contentType, throwable);
                    return new BusinessException(ErrorCode.STORAGE_FAILED, throwable);
                });
    }

    @Override
    public Mono<Void> delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..") || storageKey.contains("/") || storageKey.contains("\\")) {
            return Mono.error(new BusinessException(ErrorCode.INVALID_FILE_NAME));
        }
        Path targetPath = rootPath.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootPath)) {
            return Mono.error(new BusinessException(ErrorCode.INVALID_FILE_NAME));
        }
        return Mono.fromRunnable(() -> deleteQuietly(targetPath, storageKey))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void countBytes(DataBuffer dataBuffer, AtomicLong totalBytes) {
        long currentTotal = totalBytes.addAndGet(dataBuffer.readableByteCount());
        if (currentTotal > maxFileSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    private String resolveContentType(FilePart filePart) {
        MediaType mediaType = filePart.headers().getContentType();
        if (mediaType == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return mediaType.getType() + "/" + mediaType.getSubtype();
    }

    private String sanitizeFilename(String filename) {
        String trimmedFilename = filename == null ? "" : filename.strip();
        if (trimmedFilename.isBlank() || trimmedFilename.contains("..") || trimmedFilename.contains("/") || trimmedFilename.contains("\\")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_NAME);
        }
        String safeFilename = trimmedFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeFilename.length() > MAX_SAFE_FILENAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_FILE_NAME);
        }
        return safeFilename;
    }

    private void deleteQuietly(Path targetPath, String fileContext) {
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException exception) {
            LOGGER.warn("delete local file failed, fileContext={}, path={}", fileContext, targetPath, exception);
        }
    }
}
