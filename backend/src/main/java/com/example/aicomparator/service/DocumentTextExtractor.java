package com.example.aicomparator.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Yüklenen dosyadan düz metin çıkarır. */
@Service
public class DocumentTextExtractor {

    public String extract(String filename, String contentType, byte[] bytes) {
        String text = isPdf(filename, contentType)
                ? extractPdf(bytes)
                : extractPlainText(filename, contentType, bytes);

        String stripped = text.strip();

        if (stripped.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dosyadan metin çıkarılamadı: dosya boş görünüyor."
            );
        }

        return stripped;
    }

    private static boolean isPdf(String filename, String contentType) {
        return "application/pdf".equalsIgnoreCase(contentType)
                || lower(filename).endsWith(".pdf");
    }

    private static boolean isPlainText(String filename, String contentType) {
        String name = lower(filename);

        return name.endsWith(".txt")
                || name.endsWith(".md")
                || lower(contentType).startsWith("text/");
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);

            if (text.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Bu PDF'ten metin çıkarılamadı; taranmış bir belge "
                                + "olabilir."
                );
            }

            return text;
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PDF okunamadı: dosya bozuk olabilir."
            );
        }
    }

    private String extractPlainText(
            String filename,
            String contentType,
            byte[] bytes
    ) {
        if (!isPlainText(filename, contentType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Yalnızca PDF, .txt ve .md desteklenir."
            );
        }

        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
