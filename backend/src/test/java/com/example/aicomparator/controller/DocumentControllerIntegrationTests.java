package com.example.aicomparator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.aicomparator.ai.EmbeddingProvider;
import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.CompareResponse;
import com.example.aicomparator.repository.DocumentRepository;
import com.example.aicomparator.service.ConversationService;

@SpringBootTest
class DocumentControllerIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private DocumentRepository documentRepository;

    @MockitoBean
    private EmbeddingProvider embeddingProvider;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void uploadsListsAndDeletesADocument() throws Exception {
        when(embeddingProvider.modelName()).thenReturn("test-model");
        when(embeddingProvider.embedBatch(anyList()))
                .thenAnswer(invocation -> {
                    List<String> texts = invocation.getArgument(0);
                    return texts.stream()
                            .map(text -> new float[] {1f, 0f, 0f}).toList();
                });

        Long conversationId = newConversation();
        MockMvc mvc = mockMvc();

        MockMultipartFile file = new MockMultipartFile(
                "file", "not.txt", MediaType.TEXT_PLAIN_VALUE,
                "Fenerbahce 1907 yilinda kuruldu."
                        .getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/conversations/{id}/documents", conversationId)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("not.txt"))
                .andExpect(jsonPath("$.chunkCount").value(1));

        mvc.perform(get("/api/conversations/{id}/documents", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        Long documentId = documentRepository
                .findByConversation_IdOrderByIdAsc(conversationId)
                .get(0).getId();

        mvc.perform(delete("/api/conversations/{id}/documents/{docId}",
                        conversationId, documentId))
                .andExpect(status().isNoContent());

        assertThat(documentRepository
                .findByConversation_IdOrderByIdAsc(conversationId)).isEmpty();
    }

    @Test
    void rejectsUnsupportedFileType() throws Exception {
        Long conversationId = newConversation();

        MockMultipartFile file = new MockMultipartFile(
                "file", "resim.png", MediaType.IMAGE_PNG_VALUE,
                new byte[] {1, 2, 3});

        mockMvc().perform(
                        multipart("/api/conversations/{id}/documents",
                                conversationId).file(file))
                .andExpect(status().isBadRequest())
                // Sebep istemciye ulaşmalı; aksi hâlde kullanıcı neyi
                // düzelteceğini bilemez (spring.mvc.problemdetails.enabled).
                .andExpect(jsonPath("$.detail")
                        .value("Yalnızca PDF, .txt ve .md desteklenir."));
    }

    private Long newConversation() {
        CompareResponse comparison = conversationService.saveComparison(
                "belge testi",
                List.of(new AiResponse(null, "OPENAI", "cevap"))
        );
        return comparison.conversationId();
    }
}
