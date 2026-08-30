package com.ngs.analytics.fastq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.domain.FastqMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FastqMetricsMapper {

    private final ObjectMapper objectMapper;
    private final FastqQcEvaluator fastqQcEvaluator;

    public FastqMetricsMapper(ObjectMapper objectMapper, FastqQcEvaluator fastqQcEvaluator) {
        this.objectMapper = objectMapper;
        this.fastqQcEvaluator = fastqQcEvaluator;
    }

    public Map<String, Object> toApiMap(FastqMetrics m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("analysisId", m.getAnalysis().getId().toString());
        map.put("readCount", m.getReadCount());
        map.put("avgLength", m.getAvgLength());
        map.put("minLength", m.getMinLength());
        map.put("maxLength", m.getMaxLength());
        map.put("gcContent", m.getGcContent());
        map.put("atContent", m.getAtContent());
        map.put("nContent", m.getNContent());
        map.put("meanQuality", m.getMeanQuality());
        map.put("duplicationRate", m.getDuplicationRate());
        map.put("pairedEnd", m.isPairedEnd());
        map.put("readCountR1", m.getReadCountR1());
        map.put("readCountR2", m.getReadCountR2());
        map.put("lengthDistribution", readJson(m.getLengthDistributionJson()));
        map.put("baseComposition", readJson(m.getBaseCompositionJson()));
        map.put("perPositionQuality", readJson(m.getPerPositionQualityJson()));
        map.put("overrepresented", readJson(m.getOverrepresentedJson()));
        map.put("phredSummary", readJson(m.getPhredSummaryJson()));
        map.put("adapterHits", readJson(m.getAdapterHitsJson()));
        map.put("qualityHeatmap", readJson(m.getQualityHeatmapJson()));
        FastqQcEvaluator.FastqQcResult qcResult = fastqQcEvaluator.evaluate(m);
        map.put("qc", fastqQcEvaluator.toApiMap(qcResult));
        map.put("recommendations", fastqQcEvaluator.recommendationsToApi(qcResult));
        return map;
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return json;
        }
    }
}
