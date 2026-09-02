package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VectorMathTests {

    private static final double TOLERANCE = 1e-6;

    @Test
    void cosineOfIdenticalVectorsIsOne() {
        float[] vector = {1f, 2f, 3f};

        assertThat(VectorMath.cosine(vector, vector)).isCloseTo(1.0, within());
    }

    @Test
    void cosineOfOrthogonalVectorsIsZero() {
        assertThat(VectorMath.cosine(new float[] {1f, 0f}, new float[] {0f, 1f}))
                .isCloseTo(0.0, within());
    }

    @Test
    void cosineOfOppositeVectorsIsMinusOne() {
        assertThat(VectorMath.cosine(new float[] {1f, 0f}, new float[] {-1f, 0f}))
                .isCloseTo(-1.0, within());
    }

    @Test
    void normalizeProducesUnitLength() {
        float[] normalized = VectorMath.normalize(new float[] {3f, 4f});

        assertThat(VectorMath.dot(normalized, normalized))
                .isCloseTo(1.0, within());
        assertThat(normalized[0]).isCloseTo(0.6f, within(1e-6f));
        assertThat(normalized[1]).isCloseTo(0.8f, within(1e-6f));
    }

    @Test
    void dotOfNormalizedVectorsEqualsCosine() {
        float[] a = {1f, 2f, 3f};
        float[] b = {4f, -5f, 6f};

        assertThat(VectorMath.dot(VectorMath.normalize(a), VectorMath.normalize(b)))
                .isCloseTo(VectorMath.cosine(a, b), within());
    }

    @Test
    void byteRoundTripIsLossless() {
        float[] original = {0f, 1.5f, -2.25f, 1e-8f, 12345.678f};

        assertThat(VectorMath.fromBytes(VectorMath.toBytes(original)))
                .containsExactly(original);
    }

    @Test
    void mismatchedLengthsAreRejected() {
        assertThatThrownBy(() ->
                VectorMath.dot(new float[] {1f}, new float[] {1f, 2f}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroVectorCannotBeNormalized() {
        assertThatThrownBy(() -> VectorMath.normalize(new float[] {0f, 0f}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cosineOfZeroVectorThrows() {
        assertThatThrownBy(() ->
                VectorMath.cosine(new float[] {0f, 0f}, new float[] {1f, 0f}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromBytesRejectsTruncatedInput() {
        assertThatThrownBy(() -> VectorMath.fromBytes(new byte[] {1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonFiniteVectorsCannotBeNormalized() {
        assertThatThrownBy(() ->
                VectorMath.normalize(new float[] {Float.NaN, 1f}))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                VectorMath.normalize(new float[] {Float.POSITIVE_INFINITY, 1f}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void byteRoundTripHandlesProductionScaleVectors() {
        float[] original = new float[1536];

        for (int i = 0; i < original.length; i++) {
            original[i] = (float) Math.sin(i) * (i - 768);
        }

        byte[] bytes = VectorMath.toBytes(original);

        assertThat(bytes).hasSize(1536 * 4);
        assertThat(VectorMath.fromBytes(bytes)).containsExactly(original);
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(TOLERANCE);
    }

    private static org.assertj.core.data.Offset<Float> within(float tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
