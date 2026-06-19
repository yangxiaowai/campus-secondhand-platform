package com.campus.search.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * float[] 与 Redis VECTOR / RediSearch KNN 所需的 FLOAT32 字节序互转
 */
public final class EmbeddingVectorCodec {

    private EmbeddingVectorCodec() {
    }

    public static byte[] toFloat32Bytes(float[] vector) {
        if (vector == null || vector.length == 0) {
            return new byte[0];
        }
        ByteBuffer buf = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buf.putFloat(v);
        }
        return buf.array();
    }

    public static float[] fromFloat32Bytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % 4 != 0) {
            return new float[0];
        }
        int len = bytes.length / 4;
        float[] out = new float[len];
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < len; i++) {
            out[i] = buf.getFloat();
        }
        return out;
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static float[] normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return vector;
        }
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm < 1e-12) {
            return vector;
        }
        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            out[i] = (float) (vector[i] / norm);
        }
        return out;
    }
}
