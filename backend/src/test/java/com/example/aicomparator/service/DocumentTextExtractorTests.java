package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DocumentTextExtractorTests {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void extractsPlainText() {
        byte[] bytes = "merhaba dünya".getBytes(StandardCharsets.UTF_8);

        assertThat(extractor.extract("not.txt", "text/plain", bytes))
                .isEqualTo("merhaba dünya");
    }

    @Test
    void extractsMarkdownByExtensionEvenWithGenericContentType() {
        byte[] bytes = "# Baslik".getBytes(StandardCharsets.UTF_8);

        assertThat(extractor.extract(
                "notlar.md", "application/octet-stream", bytes))
                .isEqualTo("# Baslik");
    }

    @Test
    void extractsPdfText() throws Exception {
        byte[] pdf = onePagePdf("Fenerbahce 1907");

        assertThat(extractor.extract("belge.pdf", "application/pdf", pdf))
                .contains("Fenerbahce 1907");
    }

    @Test
    void pdfWithNoExtractableTextIsRejected() throws Exception {
        byte[] pdf = emptyPdf();

        assertThatThrownBy(() ->
                extractor.extract("taranmis.pdf", "application/pdf", pdf))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("taranmış");
    }

    @Test
    void unsupportedTypeIsRejected() {
        assertThatThrownBy(() -> extractor.extract(
                "resim.png", "image/png", new byte[] {1, 2, 3}))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void blankTextIsRejected() {
        byte[] bytes = "   \n  ".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() ->
                extractor.extract("bos.txt", "text/plain", bytes))
                .isInstanceOf(ResponseStatusException.class);
    }

    private static byte[] onePagePdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content =
                         new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(
                        Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(100, 700);
                content.showText(text);
                content.endText();
            }

            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] emptyPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }
}
