package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class TextChunkerTests {

    @Test
    void shortTextBecomesASingleChunk() {
        List<TextChunker.TextChunk> chunks =
                TextChunker.chunk("kısa metin", 100, 20);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(0).content()).isEqualTo("kısa metin");
    }

    @Test
    void blankTextProducesNoChunks() {
        assertThat(TextChunker.chunk("   \n\n  ", 100, 20)).isEmpty();
        assertThat(TextChunker.chunk("", 100, 20)).isEmpty();
    }

    @Test
    void longTextIsSplitIntoIndexedChunks() {
        String text = "a".repeat(250);

        List<TextChunker.TextChunk> chunks = TextChunker.chunk(text, 100, 20);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).extracting(TextChunker.TextChunk::index)
                .containsExactlyElementsOf(
                        IntStream.range(0, chunks.size())
                                .boxed().toList());
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content().length()).isLessThanOrEqualTo(100));
    }

    @Test
    void consecutiveChunksOverlap() {
        String text = "a".repeat(250);

        List<TextChunker.TextChunk> chunks = TextChunker.chunk(text, 100, 20);

        // İkinci parça, birincinin son 20 karakterini de taşımalı: örtüşme
        // olmadan sınırda kalan cümleler hiçbir parçada tam görünmez.
        String first = chunks.get(0).content();
        String second = chunks.get(1).content();
        assertThat(second).startsWith(first.substring(first.length() - 20));
    }

    @Test
    void breaksAtParagraphBoundaryWhenOneIsAvailable() {
        String paragraph = "b".repeat(60);
        String text = paragraph + "\n\n" + "c".repeat(120);

        List<TextChunker.TextChunk> chunks = TextChunker.chunk(text, 100, 10);

        assertThat(chunks.get(0).content()).isEqualTo(paragraph);
    }

    @Test
    void windowsLineEndingsAreNormalizedBeforeFindingParagraphs() {
        String paragraph = "b".repeat(60);
        String text = paragraph + "\r\n\r\n" + "c".repeat(120);

        List<TextChunker.TextChunk> chunks = TextChunker.chunk(text, 100, 10);

        assertThat(chunks.get(0).content()).isEqualTo(paragraph);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content()).doesNotContain("\r"));
    }

    @Test
    void breaksAtSingleLineEndingWhenNoStrongerBoundaryExists() {
        String firstLine = "e".repeat(60);
        String text = firstLine + "\n" + "f".repeat(120);

        List<TextChunker.TextChunk> chunks = TextChunker.chunk(text, 100, 10);

        assertThat(chunks.get(0).content()).isEqualTo(firstLine);
    }

    @Test
    void aWordLongerThanTheChunkSizeIsHardSplit() {
        String text = "d".repeat(250);

        List<TextChunker.TextChunk> chunks = TextChunker.chunk(text, 50, 5);

        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content()).isNotBlank());
        assertThat(String.join("", chunks.stream()
                .map(TextChunker.TextChunk::content).toList()))
                .contains("d".repeat(50));
    }

    @Test
    void turkishCharactersAreCountedAsSingleCharacters() {
        String text = "ğüşiöç".repeat(30);   // 180 karakter

        List<TextChunker.TextChunk> chunks = TextChunker.chunk(text, 100, 10);

        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content().length()).isLessThanOrEqualTo(100));
    }

    @Test
    void overlapMustBeSmallerThanChunkSize() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> TextChunker.chunk("abc", 50, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlapMustStayBelowBoundarySearchRange() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> TextChunker.chunk("abc", 100, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yarısından az");
    }
}
