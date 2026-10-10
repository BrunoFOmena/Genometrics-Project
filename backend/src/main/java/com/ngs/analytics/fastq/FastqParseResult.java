package com.ngs.analytics.fastq;

import com.ngs.analytics.domain.FastqMetrics;

public record FastqParseResult(
        long readCount,
        double averageLength,
        int minimumLength,
        int maximumLength,
        double gcContent,
        double atContent,
        double nContent,
        double meanQuality,
        double duplicationRate,
        boolean pairedEnd,
        long readCountR1,
        long readCountR2,
        String lengthDistributionJson,
        String baseCompositionJson,
        String perPositionQualityJson,
        String overrepresentedJson,
        String phredSummaryJson,
        String adapterHitsJson,
        String qualityHeatmapJson
) {
    public static FastqParseResult fromEntity(FastqMetrics metrics) {
        return new FastqParseResult(
                metrics.getReadCount(), metrics.getAvgLength(), metrics.getMinLength(), metrics.getMaxLength(),
                metrics.getGcContent(), metrics.getAtContent(), metrics.getNContent(), metrics.getMeanQuality(),
                metrics.getDuplicationRate(), metrics.isPairedEnd(), metrics.getReadCountR1(), metrics.getReadCountR2(),
                metrics.getLengthDistributionJson(), metrics.getBaseCompositionJson(),
                metrics.getPerPositionQualityJson(), metrics.getOverrepresentedJson(), metrics.getPhredSummaryJson(),
                metrics.getAdapterHitsJson(), metrics.getQualityHeatmapJson()
        );
    }

    public FastqMetrics toEntity() {
        FastqMetrics metrics = new FastqMetrics();
        metrics.setReadCount(readCount);
        metrics.setAvgLength(averageLength);
        metrics.setMinLength(minimumLength);
        metrics.setMaxLength(maximumLength);
        metrics.setGcContent(gcContent);
        metrics.setAtContent(atContent);
        metrics.setNContent(nContent);
        metrics.setMeanQuality(meanQuality);
        metrics.setDuplicationRate(duplicationRate);
        metrics.setPairedEnd(pairedEnd);
        metrics.setReadCountR1(readCountR1);
        metrics.setReadCountR2(readCountR2);
        metrics.setLengthDistributionJson(lengthDistributionJson);
        metrics.setBaseCompositionJson(baseCompositionJson);
        metrics.setPerPositionQualityJson(perPositionQualityJson);
        metrics.setOverrepresentedJson(overrepresentedJson);
        metrics.setPhredSummaryJson(phredSummaryJson);
        metrics.setAdapterHitsJson(adapterHitsJson);
        metrics.setQualityHeatmapJson(qualityHeatmapJson);
        return metrics;
    }
}
