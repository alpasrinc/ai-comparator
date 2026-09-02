package com.example.aicomparator.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Vektör benzerliği ve vektörlerin ikili gösterimi.
 *
 * <p>Saf fonksiyonlar: veritabanı, ağ ya da Spring bağımlılığı yok. RAG'in
 * doğru parçayı bulup bulmadığı buradaki hesaba bağlı olduğu için tek başına
 * test edilebilir olması önemli.
 */
public final class VectorMath {

    private VectorMath() {
    }

    /** Birim uzunluğa indirger; sıfır vektör kabul edilmez. */
    public static float[] normalize(float[] vector) {
        double length = Math.sqrt(dot(vector, vector));

        if (length == 0.0) {
            throw new IllegalArgumentException(
                    "Sıfır vektör normalize edilemez.");
        }

        float[] normalized = new float[vector.length];

        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / length);
        }

        return normalized;
    }

    public static double dot(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vektör boyutları uyuşmuyor: " + a.length + " ve " + b.length);
        }

        double sum = 0.0;

        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }

        return sum;
    }

    public static double cosine(float[] a, float[] b) {
        double lengths = Math.sqrt(dot(a, a)) * Math.sqrt(dot(b, b));

        if (lengths == 0.0) {
            throw new IllegalArgumentException(
                    "Sıfır vektörün kosinüs benzerliği tanımsız.");
        }

        return dot(a, b) / lengths;
    }

    /** Little-endian float dizisi; saklama biçimi budur ve değişmemelidir. */
    public static byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(vector);
        return buffer.array();
    }

    public static float[] fromBytes(byte[] bytes) {
        if (bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException(
                    "Vektör baytları 4'ün katı değil: " + bytes.length);
        }

        FloatBuffer floats = ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer();
        float[] vector = new float[floats.remaining()];
        floats.get(vector);
        return vector;
    }
}
