package com.ngs.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ngs.analytics.analytics.MetricFactWriter;
import com.ngs.analytics.domain.Analysis;
import com.ngs.analytics.domain.FastqMetrics;
import com.ngs.analytics.domain.MetricFact;
import com.ngs.analytics.domain.MetricFactRepository;
import com.ngs.analytics.domain.QcCheckFact;
import com.ngs.analytics.domain.QcCheckFactRepository;
import com.ngs.analytics.domain.VcfMetrics;
import com.ngs.analytics.fastq.FastqMetricsMapper;
import com.ngs.analytics.fastq.FastqParser;
import com.ngs.analytics.fastq.FastqQcEvaluator;
import com.ngs.analytics.vcf.VcfParseResult;
import com.ngs.analytics.vcf.VcfParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Freezes what the Java backend produces for the datasets/ fixtures (ADR 0001, migration step 1).
 * The golden files in datasets/golden/ are the acceptance tests of the Python port, so this test fails when the
 * backend output drifts from them. To change them on purpose:
 * {@code mvn test -Dtest=GoldenContractTest -Dgolden.update=true}, then review the diff.
 */
class GoldenContractTest {

    private static final Path DATASETS = Path.of("..", "datasets");
    private static final Path GOLDEN = DATASETS.resolve("golden");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final FastqQcEvaluator qcEvaluator = new FastqQcEvaluator(objectMapper);
    private final FastqParser fastqParser = new FastqParser(objectMapper);
    private final FastqMetricsMapper fastqMapper = new FastqMetricsMapper(objectMapper, qcEvaluator);
    private final VcfParser vcfParser = new VcfParser(objectMapper);

    @Test
    void fastqSingleEnd() throws Exception {
        try (InputStream in = Files.newInputStream(DATASETS.resolve("sample.fastq"))) {
            assertMatchesGolden("sample.fastq.json", fastq(fastqParser.parseResult(in, false).toEntity(), "sample.fastq"));
        }
    }

    @Test
    void fastqPairedEnd() throws Exception {
        try (InputStream r1 = Files.newInputStream(DATASETS.resolve("sample_R1.fastq"));
             InputStream r2 = Files.newInputStream(DATASETS.resolve("sample_R2.fastq"))) {
            assertMatchesGolden("sample_R1_R2.fastq.json",
                    fastq(fastqParser.parsePairedResult(r1, r2, false, false).toEntity(), "sample_R1.fastq", "sample_R2.fastq"));
        }
    }

    @Test
    void vcf() throws Exception {
        try (InputStream in = Files.newInputStream(DATASETS.resolve("sample.vcf"))) {
            VcfParseResult result = vcfParser.parseResult(in, false);
            Map<String, Object> golden = new LinkedHashMap<>();
            golden.put("inputs", List.of("sample.vcf"));
            golden.put("kind", "vcf");
            golden.put("metrics", nested(objectMapper.valueToTree(result)));
            golden.put("metricFacts", metricFacts(null, result.toEntity()));
            assertMatchesGolden("sample.vcf.json", golden);
        }
    }

    /** API view of a FASTQ analysis: metrics, QC verdict, recommendations and the facts the MCP tools query. */
    private Map<String, Object> fastq(FastqMetrics metrics, String... inputs) {
        Analysis analysis = mock(Analysis.class);
        when(analysis.getId()).thenReturn(new UUID(0, 0));
        metrics.setAnalysis(analysis);
        Map<String, Object> api = new LinkedHashMap<>(fastqMapper.toApiMap(metrics));
        api.remove("analysisId");

        Map<String, Object> golden = new LinkedHashMap<>();
        golden.put("inputs", List.of(inputs));
        golden.put("kind", "fastq");
        golden.put("qc", api.remove("qc"));
        golden.put("recommendations", api.remove("recommendations"));
        golden.put("metrics", api);
        golden.put("metricFacts", metricFacts(metrics, null));
        golden.put("qcChecks", qcChecks(metrics));
        return golden;
    }

    /** Runs the real MetricFactWriter against mocked repositories and returns what it would store. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> metricFacts(FastqMetrics fastq, VcfMetrics vcf) {
        MetricFactRepository facts = mock(MetricFactRepository.class);
        MetricFactWriter writer = new MetricFactWriter(facts, mock(QcCheckFactRepository.class), qcEvaluator);
        Analysis analysis = mock(Analysis.class);
        if (fastq != null) {
            writer.write(analysis, fastq);
        } else {
            writer.write(analysis, vcf);
        }
        ArgumentCaptor<List<MetricFact>> saved = ArgumentCaptor.forClass(List.class);
        verify(facts).saveAll(saved.capture());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MetricFact fact : saved.getValue()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", fact.getMetricKey());
            row.put("value", fact.getNumericValue());
            row.put("unit", fact.getUnit());
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> qcChecks(FastqMetrics metrics) {
        QcCheckFactRepository checks = mock(QcCheckFactRepository.class);
        new MetricFactWriter(mock(MetricFactRepository.class), checks, qcEvaluator).write(mock(Analysis.class), metrics);
        ArgumentCaptor<List<QcCheckFact>> saved = ArgumentCaptor.forClass(List.class);
        verify(checks).saveAll(saved.capture());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (QcCheckFact check : saved.getValue()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", check.getCheckKey());
            row.put("status", check.getStatus());
            row.put("value", check.getNumericValue());
            row.put("message", check.getMessage());
            rows.add(row);
        }
        return rows;
    }

    /** Parse result records keep some fields as JSON strings ("fooJson"); expose them as nested "foo". */
    private JsonNode nested(JsonNode record) throws Exception {
        ObjectNode out = objectMapper.createObjectNode();
        for (var field : record.properties()) {
            String name = field.getKey();
            if (name.endsWith("Json")) {
                String json = field.getValue().asText("");
                out.set(name.substring(0, name.length() - 4), json.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(json));
            } else {
                out.set(name, field.getValue());
            }
        }
        return out;
    }

    private void assertMatchesGolden(String fileName, Object actual) throws Exception {
        String json = objectMapper.writeValueAsString(objectMapper.readTree(objectMapper.writeValueAsString(actual))) + "\n";
        Path file = GOLDEN.resolve(fileName);
        if (Boolean.getBoolean("golden.update")) {
            Files.createDirectories(GOLDEN);
            Files.writeString(file, json, StandardCharsets.UTF_8);
            return;
        }
        assertThat(file).as("golden file missing; run with -Dgolden.update=true").exists();
        assertThat(objectMapper.readTree(json))
                .as("backend output drifted from %s; if intended, rerun with -Dgolden.update=true and review the diff", fileName)
                .isEqualTo(objectMapper.readTree(Files.readString(file, StandardCharsets.UTF_8)));
    }
}
