package com.ngs.analytics.vcf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.VcfMetrics;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
    void recordsIndelLengthHistogram() throws Exception {
        VcfMetrics metrics = parse("""
                ##fileformat=VCFv4.2
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr1\t10\t.\tAT\tA\t40\tPASS\tDP=5
                chr1\t20\t.\tA\tATG\t50\tPASS\tDP=6
                chr1\t30\t.\tA\tG\t90\tPASS\tDP=10
                chr1\t40\t.\tAAAAAAAAAAAAAAAAAAAAAA\tA\t30\tPASS\tDP=4
                """);
        Map<?, ?> hist = readJson(metrics.getIndelLengthDistributionJson());
        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) hist.get("labels");
        @SuppressWarnings("unchecked")
        List<Integer> counts = (List<Integer>) hist.get("counts");
        assertEquals("≤-21", labels.get(0));
        assertEquals("≥21", labels.get(labels.size() - 1));
        assertEquals(1, countAt(labels, counts, "-1"));
        assertEquals(1, countAt(labels, counts, "2"));
        assertEquals(1, countAt(labels, counts, "≤-21"));
        assertEquals(0, countAt(labels, counts, "0"));
    }

    @Test
    void recordsTsTvByChromosome() throws Exception {
        VcfMetrics metrics = parse("""
                ##fileformat=VCFv4.2
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr1\t10\t.\tA\tG\t90\tPASS\tDP=10
                chr1\t20\t.\tA\tC\t80\tPASS\tDP=10
                chr2\t10\t.\tC\tT\t70\tPASS\tDP=10
                chr2\t20\t.\tAT\tA\t40\tPASS\tDP=4
                """);
        Map<?, ?> byChrom = readJson(metrics.getTsTvByChromosomeJson());
        Map<?, ?> chr1 = (Map<?, ?>) byChrom.get("chr1");
        Map<?, ?> chr2 = (Map<?, ?>) byChrom.get("chr2");
        assertEquals(1, ((Number) chr1.get("ts")).intValue());
        assertEquals(1, ((Number) chr1.get("tv")).intValue());
        assertEquals(1.0, ((Number) chr1.get("tsTvRatio")).doubleValue(), 0.001);
        assertEquals(1, ((Number) chr2.get("ts")).intValue());
        assertEquals(0, ((Number) chr2.get("tv")).intValue());
        assertEquals(1.0, ((Number) chr2.get("tsTvRatio")).doubleValue(), 0.001);
        assertFalse(byChrom.containsKey("chrX"));
    }

    @Test
    void capturesHeaderMetadata() throws Exception {
        VcfMetrics metrics = parse("""
                ##fileformat=VCFv4.2
                ##reference=GRCh38
                ##source=GATK HaplotypeCaller
                ##contig=<ID=chr1,length=248956422>
                ##contig=<ID=chr2,length=242193529>
                ##INFO=<ID=DP,Number=1,Type=Integer,Description="Depth">
                ##INFO=<ID=AF,Number=A,Type=Float,Description="AF">
                ##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tSAMPLEA
                chr1\t100\t.\tA\tG\t99\tPASS\tDP=10;AF=0.5
                """);
        Map<?, ?> header = readJson(metrics.getHeaderJson());
        assertEquals("VCFv4.2", header.get("fileformat"));
        assertEquals("GRCh38", header.get("reference"));
        assertEquals("GATK HaplotypeCaller", header.get("source"));
        assertEquals(2, ((Number) header.get("contigCount")).intValue());
        assertEquals(List.of("chr1", "chr2"), header.get("contigs"));
        assertEquals(List.of("DP", "AF"), header.get("infoIds"));
        assertEquals(List.of("GT"), header.get("formatIds"));
        assertEquals(List.of("SAMPLEA"), header.get("samples"));
    }

    @Test
    void recordsQualAndDpHistograms() throws Exception {
        VcfMetrics metrics = parse("""
                ##fileformat=VCFv4.2
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr1\t10\t.\tA\tG\t12\tPASS\tDP=3
                chr1\t20\t.\tA\tC\t97\tPASS\tDP=198
                chr1\t30\t.\tA\tT\t.\tPASS\t.
                chr1\t40\t.\tA\tG\t110\tPASS\tDP=250
                """);
        Map<?, ?> qual = readJson(metrics.getQualHistogramJson());
        Map<?, ?> dp = readJson(metrics.getDpHistogramJson());
        @SuppressWarnings("unchecked")
        List<String> qualLabels = (List<String>) qual.get("labels");
        @SuppressWarnings("unchecked")
        List<Integer> qualCounts = (List<Integer>) qual.get("counts");
        @SuppressWarnings("unchecked")
        List<String> dpLabels = (List<String>) dp.get("labels");
        @SuppressWarnings("unchecked")
        List<Integer> dpCounts = (List<Integer>) dp.get("counts");
        assertEquals(1, countAt(qualLabels, qualCounts, "10–15"));
        assertEquals(1, countAt(qualLabels, qualCounts, "95–100"));
        assertEquals(1, countAt(qualLabels, qualCounts, ">100"));
        assertEquals(1, countAt(dpLabels, dpCounts, "0–5"));
        assertEquals(1, countAt(dpLabels, dpCounts, "195–200"));
        assertEquals(1, countAt(dpLabels, dpCounts, ">200"));
        assertEquals(4, metrics.getPassCount());
        assertEquals((12 + 97 + 110) / 3.0, metrics.getMeanQual(), 0.01);
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

    private Map<?, ?> readJson(String json) throws Exception {
        return new ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    private static int countAt(List<String> labels, List<Integer> counts, String label) {
        int idx = labels.indexOf(label);
        assertTrue(idx >= 0, "missing bin " + label);
        return counts.get(idx);
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}
