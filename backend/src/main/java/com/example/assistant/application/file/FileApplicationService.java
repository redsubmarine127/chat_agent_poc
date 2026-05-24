package com.example.assistant.application.file;

import com.example.assistant.domain.file.Attachment;
import com.example.assistant.infrastructure.persistence.AttachmentEntity;
import com.example.assistant.infrastructure.persistence.AttachmentRepository;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Service
public class FileApplicationService {

    private final StorageService storageService;
    private final AttachmentRepository attachmentRepository;
    private final TransactionalOperator transactionalOperator;

    public FileApplicationService(
            StorageService storageService,
            AttachmentRepository attachmentRepository,
            TransactionalOperator transactionalOperator
    ) {
        this.storageService = storageService;
        this.attachmentRepository = attachmentRepository;
        this.transactionalOperator = transactionalOperator;
    }

    public Mono<Attachment> upload(FilePart filePart) {
        return storageService.store(filePart)
                .flatMap(attachment -> attachmentRepository.save(AttachmentEntity.newFromDomain(attachment))
                        .onErrorResume(throwable -> storageService.delete(attachment.storageKey())
                                .onErrorResume(cleanupError -> Mono.empty())
                                .then(Mono.error(throwable))))
                .map(AttachmentEntity::toDomain)
                .as(transactionalOperator::transactional);
    }
}
