package com.example.aicomparator.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aicomparator.dto.DocumentResponse;
import com.example.aicomparator.service.DocumentIngestionService;
import com.example.aicomparator.service.DocumentQueryService;

@RestController
@RequestMapping("/api/conversations/{conversationId}/documents")
@CrossOrigin(origins = "*")
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final DocumentQueryService queryService;
    private final boolean ragEnabled;
    private final long maxFileSizeBytes;

    public DocumentController(
            DocumentIngestionService ingestionService,
            DocumentQueryService queryService,
            @Value("${rag.enabled}") boolean ragEnabled,
            @Value("${rag.max-file-size-bytes}") long maxFileSizeBytes
    ) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
        this.ragEnabled = ragEnabled;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @PostMapping
    public DocumentResponse upload(
            @PathVariable Long conversationId,
            @RequestParam("file") MultipartFile file
    ) {
        requireEnabled();

        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Dosya boş.");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Dosya çok büyük (en fazla "
                            + (maxFileSizeBytes / (1024 * 1024)) + " MB)."
            );
        }

        try {
            return ingestionService.ingest(
                    conversationId,
                    file.getOriginalFilename() == null
                            ? "belge" : file.getOriginalFilename(),
                    file.getContentType() == null
                            ? "application/octet-stream" : file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Dosya okunamadı.");
        }
    }

    @GetMapping
    public List<DocumentResponse> list(@PathVariable Long conversationId) {
        requireEnabled();
        return queryService.listDocuments(conversationId);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long conversationId,
            @PathVariable Long documentId
    ) {
        requireEnabled();
        queryService.deleteDocument(conversationId, documentId);
    }

    private void requireEnabled() {
        if (!ragEnabled) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Belge desteği kapalı."
            );
        }
    }
}
