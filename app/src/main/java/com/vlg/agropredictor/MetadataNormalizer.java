package com.vlg.agropredictor;

public class MetadataNormalizer {
    private static final float[] MEANS = {23.196046875f, 85.624296875f, 28.16296875f};
    private static final float[] SCALES = {4.951200769540933f, 12.193795501170472f, 16.566792008005756f};

    public static float[] normalizeMetadata(float temperature, float humidity, float fruitAge) {
        float normTemp = (temperature - MEANS[0]) / SCALES[0];
        float normHumidity = (humidity - MEANS[1]) / SCALES[1];
        float normAge = (fruitAge - MEANS[2]) / SCALES[2];

        return new float[]{normTemp, normHumidity, normAge};
    }
}
