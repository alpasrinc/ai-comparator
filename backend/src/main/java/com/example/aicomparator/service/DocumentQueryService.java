package com.example.aicomparator.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aicomparator.dto.DocumentResponse;
import com.example.aicomparator.entity.Document;
import com.example.aicomparator.repository.DocumentRepository;

@Service
public class DocumentQueryService {

    private final DocumentRepository documentRepository;

    public DocumentQueryService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(Long conversationId) {
        return documentRepository
                .findByConversation_IdOrderByIdAsc(conversationId)
                .stream()
                .map(document -> new DocumentResponse(
                        document.getId(),
                        document.getFilename(),
                        document.getSizeBytes(),
                        document.getChunkCount(),
                        document.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public void deleteDocument(Long conversationId, Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Belge bulunamadı."));

        if (!document.getConversation().getId().equals(conversationId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Belge bu konuşmaya ait değil.");
        }

        documentRepository.delete(document);
    }
}
