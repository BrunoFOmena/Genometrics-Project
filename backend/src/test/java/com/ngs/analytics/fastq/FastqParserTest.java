package com.ngs.analytics.fastq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.FastqMetrics;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FastqParserTest {

    private final FastqParser parser = new FastqParser(new ObjectMapper());

    @Test
    void parsesSyntheticFastq() {
        FastqMetrics metrics = parse("""
                @r1
                ACGTNN
                +
                IIIIII
                @r2
                AAAA
                +
                !!!!
                """);
        assertEquals(2, metrics.getReadCount());
        assertEquals(4, metrics.getMinLength());
        assertEquals(6, metrics.getMaxLength());
        assertTrue(metrics.getNContent() > 0);
        assertNotNull(metrics.getBaseCompositionJson());
    }

    @Test
    void emptyFastqIsRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> parse("\n\n"));
        assertTrue(ex.getMessage().contains("no reads"));
    }

    @Test
    void truncatedRecordIsRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> parse("""
                @r1
                ACGT
                """));
        assertTrue(ex.getMessage().contains("Truncated"));
    }

    @Test
    void headerWithoutAtIsRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> parse("""
                r1
                ACGT
                +
                IIII
                """));
        assertTrue(ex.getMessage().contains("expected '@'"));
    }

    @Test
    void missingPlusLineIsRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> parse("""
                @r1
                ACGT
                IIII
                IIII
                """));
        assertTrue(ex.getMessage().contains("expected '+'"));
    }

    @Test
    void nOnlyReadHasFullNContent() {
        FastqMetrics metrics = parse("""
                @r1
                NNNN
                +
                IIII
                """);
        assertEquals(100.0, metrics.getNContent(), 0.001);
        assertEquals(0.0, metrics.getGcContent(), 0.001);
    }

    @Test
    void qualityShorterThanSequenceStillParses() {
        FastqMetrics metrics = parse("""
                @r1
                ACGT
                +
                II
                """);
        assertEquals(1, metrics.getReadCount());
        assertEquals(4, metrics.getAvgLength(), 0.001);
        assertTrue(metrics.getMeanQuality() > 0);
    }

    @Test
    void duplicateReadsRaiseDuplicationRate() {
        FastqMetrics metrics = parse("""
                @r1
                ACGT
                +
                IIII
                @r2
                ACGT
                +
                IIII
                """);
        assertEquals(2, metrics.getReadCount());
        assertEquals(50.0, metrics.getDuplicationRate(), 0.001);
    }

    @Test
    void parsesGzippedFastq() throws IOException {
        String fastq = """
                @r1
                ACGT
                +
                IIII
                """;
        FastqMetrics metrics = parser.parse(new ByteArrayInputStream(gzip(fastq)), true);
        assertEquals(1, metrics.getReadCount());
        assertEquals(4, metrics.getAvgLength(), 0.001);
    }

    private FastqMetrics parse(String fastq) {
        return parser.parse(new ByteArrayInputStream(fastq.getBytes(StandardCharsets.UTF_8)), false);
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}
