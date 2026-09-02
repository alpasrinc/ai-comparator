package com.example.aicomparator.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptPartsTests {

    @Test
    void joinsPrefixAndSuffix() {
        PromptParts parts = new PromptParts("STABIL\n", "DEGISKEN");

        assertThat(parts.joined()).isEqualTo("STABIL\nDEGISKEN");
        assertThat(parts.hasCacheablePrefix()).isTrue();
    }

    @Test
    void volatileOnlyHasNoCacheablePrefix() {
        PromptParts parts = PromptParts.volatileOnly("hepsi degisken");

        assertThat(parts.cacheablePrefix()).isEmpty();
        assertThat(parts.hasCacheablePrefix()).isFalse();
        assertThat(parts.joined()).isEqualTo("hepsi degisken");
    }

    @Test
    void blankPrefixIsNotCacheable() {
        PromptParts parts = new PromptParts("   ", "soru");

        assertThat(parts.hasCacheablePrefix()).isFalse();
    }

    @Test
    void intensityDirectiveGoesIntoTheVolatilePart() {
        String suffix = ResponseIntensity.LOW.applyTo("USER: soru\n\nASSISTANT:");

        assertThat(suffix).startsWith("Kısa ve öz");
        assertThat(suffix).endsWith("USER: soru\n\nASSISTANT:");
    }

    @Test
    void mediumIntensityAddsNothing() {
        String suffix = ResponseIntensity.MEDIUM.applyTo("USER: soru");

        assertThat(suffix).isEqualTo("USER: soru");
    }
}
