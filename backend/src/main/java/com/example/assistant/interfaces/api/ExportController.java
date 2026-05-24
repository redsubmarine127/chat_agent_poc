package com.example.assistant.interfaces.api;

import com.example.assistant.application.export.ExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Validated
@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping(value = "/markdown", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<ResponseEntity<byte[]>> markdown(
            ServerWebExchange exchange
    ) {
        return exchange.getFormData()
                .map(formData -> response(exportService.markdown(
                        requiredValue(formData.getFirst("content"), "导出内容不能为空", 200_000),
                        requiredValue(formData.getFirst("filename"), "文件名不能为空", 160)
                )));
    }

    @PostMapping(value = "/excel", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<ResponseEntity<byte[]>> excel(
            ServerWebExchange exchange
    ) {
        return exchange.getFormData()
                .map(formData -> response(exportService.excel(
                        requiredValue(formData.getFirst("content"), "导出内容不能为空", 200_000),
                        requiredValue(formData.getFirst("filename"), "文件名不能为空", 160)
                )));
    }

    private ResponseEntity<byte[]> response(ExportService.ExportFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    private String requiredValue(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        if (value.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }
}
