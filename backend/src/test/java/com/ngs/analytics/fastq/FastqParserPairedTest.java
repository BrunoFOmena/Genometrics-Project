package com.ngs.analytics.fastq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.FastqMetrics;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FastqParserPairedTest {

    private final FastqParser parser = new FastqParser(new ObjectMapper());

    @Test
    void parsePairedMergesMetrics() {
        String r1 = """
                @r1
                ACGT
                +
                IIII
                @r2
                AAAA
                +
                !!!!
                """;
        String r2 = """
                @r1
                TGCA
                +
                IIII
                @r2
                CCCC
                +
                !!!!
                """;
        FastqMetrics m = parser.parsePaired(
                stream(r1), stream(r2), false, false);
        assertTrue(m.isPairedEnd());
        assertEquals(2, m.getReadCountR1());
        assertEquals(2, m.getReadCountR2());
        assertEquals(4, m.getReadCount());
    }

    @Test
    void mismatchedPairCountFails() {
        String r1 = """
                @r1
                ACGT
                +
                IIII
                """;
        String r2 = """
                @r1
                TGCA
                +
                IIII
                @r2
                CCCC
                +
                !!!!
                """;
        ApiException ex = assertThrows(ApiException.class, () ->
                parser.parsePaired(stream(r1), stream(r2), false, false));
        assertTrue(ex.getMessage().contains("mismatch"));
    }

    @Test
    void adapterPrefixProducesHits() throws Exception {
        FastqMetrics m = parser.parse(new ByteArrayInputStream("""
                @r1
                AGATCGGAAGAGACGT
                +
                IIIIIIIIIIIIIIII
                """.getBytes(StandardCharsets.UTF_8)), false);
        Map<?, ?> hits = new ObjectMapper().readValue(m.getAdapterHitsJson(), Map.class);
        assertTrue(((Number) hits.get("readsWithHit")).longValue() > 0);
    }

    @Test
    void producesQualityHeatmap() throws Exception {
        FastqMetrics m = parser.parse(new ByteArrayInputStream("""
                @r1
                ACGT
                +
                IIII
                """.getBytes(StandardCharsets.UTF_8)), false);
        Map<?, ?> heatmap = new ObjectMapper().readValue(m.getQualityHeatmapJson(), Map.class);
        assertTrue(((Number) heatmap.get("maxPosition")).intValue() > 0);
        assertFalse(((Iterable<?>) heatmap.get("data")).iterator().hasNext() == false);
    }

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
