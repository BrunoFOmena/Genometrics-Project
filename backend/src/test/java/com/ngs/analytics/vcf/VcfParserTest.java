package com.ngs.analytics.vcf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.VcfMetrics;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class VcfParserTest {

    private final VcfParser parser = new VcfParser(new ObjectMapper());

    @Test
    void parsesSyntheticVcf() {
        VcfMetrics metrics = parse("""
                ##fileformat=VCFv4.2
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr1\t100\t.\tA\tG\t99\tPASS\tDP=10;AF=0.5;GENE=BRCA1
                chr1\t200\t.\tAT\tA\t40\tLowQual\tDP=5;AF=0.1;GENE=TP53
                """);
        assertEquals(2, metrics.getVariantCount());
        assertEquals(1, metrics.getSnpCount());
        assertEquals(1, metrics.getIndelCount());
        assertEquals(1, metrics.getPassCount());
        assertEquals(1, metrics.getFailCount());
        assertTrue(metrics.getTsTvRatio() >= 0);
    }

    @Test
    void headersOnlyVcfIsRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> parse("""
                ##fileformat=VCFv4.2
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                """));
        assertTrue(ex.getMessage().contains("no variants"));
    }

    @Test
    void shortRowIsRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> parse("""
                ##fileformat=VCFv4.2
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr1\t100\t.\tA
                """));
        assertTrue(ex.getMessage().contains("8 columns"));
    }

    @Test
    void classifiesMnp() {
        VcfMetrics metrics = parse("""
                ##fileformat=VCFv4.2
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr2\t10\t.\tAT\tGC\t30\tPASS\tDP=8
                """);
        assertEquals(1, metrics.getVariantCount());
        assertEquals(1, metrics.getMnpCount());
        assertEquals(0, metrics.getSnpCount());
        assertEquals(0, metrics.getIndelCount());
    }

    @Test
    void missingQualAndDotFilterCountAsPass() {
        VcfMetrics metrics = parse("""
                ##fileformat=VCFv4.2
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr1\t1\t.\tA\tG\t.\t.\tDP=4
                """);
        assertEquals(1, metrics.getPassCount());
        assertEquals(0, metrics.getFailCount());
        assertEquals(0.0, metrics.getMeanQual(), 0.001);
        assertEquals(4.0, metrics.getMeanDp(), 0.001);
    }

    @Test
    void multiAltUsesFirstAlleleOnly() {
        VcfMetrics metrics = parse("""
                ##fileformat=VCFv4.2
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr1\t50\t.\tA\tG,T\t80\tPASS\tDP=12
                """);
        assertEquals(1, metrics.getSnpCount());
        assertEquals(0, metrics.getIndelCount());
        assertEquals(1, metrics.getTransitions());
    }

    @Test
    void parsesGzippedVcf() throws IOException {
        String vcf = """
                ##fileformat=VCFv4.2
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chrX\t9\t.\tC\tT\t20\tPASS\tDP=3
                """;
        VcfMetrics metrics = parser.parse(new ByteArrayInputStream(gzip(vcf)), true);
        assertEquals(1, metrics.getVariantCount());
        assertEquals(1, metrics.getSnpCount());
    }

    private VcfMetrics parse(String vcf) {
        return parser.parse(new ByteArrayInputStream(vcf.getBytes(StandardCharsets.UTF_8)), false);
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}
