package com.ngs.analytics.fastq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.domain.FastqMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FastqQcEvaluatorTest {

    private FastqQcEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new FastqQcEvaluator(new ObjectMapper());
    }

    @Test
    void goodMetricsPassOverall() {
        FastqMetrics m = metrics(150_000, 45.0, 0.5, 15.0, 32.0, "{}", "[]");
        FastqQcEvaluator.FastqQcResult result = evaluator.evaluate(m);
        assertEquals(FastqQcStatus.PASS, result.overall());
        assertTrue(result.recommendations().isEmpty());
    }

    @Test
    void lowMeanQualityFails() {
        FastqMetrics m = metrics(150_000, 45.0, 0.5, 15.0, 15.0, "{}", "[]");
        FastqQcEvaluator.FastqQcResult result = evaluator.evaluate(m);
        assertEquals(FastqQcStatus.FAIL, result.overall());
        assertTrue(result.recommendations().stream().anyMatch(r -> r.title().contains("quality")));
    }

    @Test
    void highGcWarns() {
        FastqMetrics m = metrics(150_000, 62.0, 0.5, 15.0, 30.0, "{}", "[]");
        FastqQcEvaluator.FastqQcResult result = evaluator.evaluate(m);
        assertEquals(FastqQcStatus.WARN, result.overall());
        assertTrue(result.checks().stream().anyMatch(c -> c.id().equals("gcContent") && c.status() == FastqQcStatus.WARN));
        assertFalse(result.recommendations().isEmpty());
    }

    @Test
    void tinyReadCountWarnsButDoesNotFail() {
        FastqMetrics m = metrics(4, 50.0, 0.0, 0.0, 40.0, "{}", "[]");
        FastqQcEvaluator.FastqQcResult result = evaluator.evaluate(m);
        assertNotEquals(FastqQcStatus.FAIL, result.overall());
        assertTrue(result.checks().stream().anyMatch(c -> c.id().equals("readCount") && c.status() == FastqQcStatus.WARN));
    }

    @Test
    void perPositionDropAddsTrimRecommendation() throws Exception {
        String perPos = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "1", 35, "2", 35, "3", 18, "4", 15
        ));
        FastqMetrics m = metrics(150_000, 45.0, 0.5, 15.0, 30.0, perPos, "[]");
        FastqQcEvaluator.FastqQcResult result = evaluator.evaluate(m);
        assertTrue(result.recommendations().stream()
                .anyMatch(r -> r.detail().contains("trimming to cycle 3")));
    }

    @Test
    void overrepresentedSequenceWarns() throws Exception {
        String overrep = new ObjectMapper().writeValueAsString(java.util.List.of(
                java.util.Map.of("sequence", "ACGTACGT", "count", 4000)
        ));
        FastqMetrics m = metrics(10_000, 45.0, 0.5, 15.0, 30.0, "{}", overrep);
        FastqQcEvaluator.FastqQcResult result = evaluator.evaluate(m);
        assertTrue(result.checks().stream()
                .anyMatch(c -> c.id().equals("overrepresented") && c.status() == FastqQcStatus.WARN));
    }

    private static FastqMetrics metrics(
            long readCount,
            double gc,
            double nContent,
            double dupRate,
            double meanQ,
            String perPosJson,
            String overrepJson
    ) {
        FastqMetrics m = new FastqMetrics();
        m.setReadCount(readCount);
        m.setGcContent(gc);
        m.setAtContent(100 - gc - nContent);
        m.setNContent(nContent);
        m.setDuplicationRate(dupRate);
        m.setMeanQuality(meanQ);
        m.setAvgLength(100);
        m.setMinLength(50);
        m.setMaxLength(150);
        m.setPerPositionQualityJson(perPosJson);
        m.setOverrepresentedJson(overrepJson);
        m.setPhredSummaryJson("{\"mean\":" + meanQ + ",\"encoding\":\"Phred+33\"}");
        return m;
    }
}
