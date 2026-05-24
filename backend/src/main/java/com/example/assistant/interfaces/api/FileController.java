package com.example.assistant.interfaces.api;

import com.example.assistant.application.file.FileApplicationService;
import com.example.assistant.interfaces.api.response.AttachmentResponse;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileApplicationService fileApplicationService;

    public FileController(FileApplicationService fileApplicationService) {
        this.fileApplicationService = fileApplicationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<AttachmentResponse> upload(@RequestPart("file") FilePart filePart) {
        return fileApplicationService.upload(filePart).map(AttachmentResponse::from);
    }
}
