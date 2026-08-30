package com.ngs.analytics.fastq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.domain.FastqMetrics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FastqQcEvaluator {

    private static final int OVERREP_SAMPLE_LIMIT = 50_000;

    private final ObjectMapper objectMapper;

    public FastqQcEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FastqQcResult evaluate(FastqMetrics metrics) {
        List<FastqQcCheck> checks = new ArrayList<>();
        checks.add(checkMeanQuality(metrics.getMeanQuality()));
        checks.add(checkGc(metrics.getGcContent()));
        checks.add(checkNContent(metrics.getNContent()));
        checks.add(checkDuplication(metrics.getDuplicationRate()));
        checks.add(checkReadCount(metrics.getReadCount()));

        Map<Integer, Double> perPos = readPerPosition(metrics.getPerPositionQualityJson());
        Integer trimCycle = findTrimCycle(perPos);
        checks.add(checkPerPositionQuality(perPos));

        List<Map<String, Object>> overrep = readOverrepresented(metrics.getOverrepresentedJson());
        checks.add(checkOverrepresented(overrep, metrics.getReadCount()));
        checks.add(checkAdapterContent(metrics.getAdapterHitsJson()));

        FastqQcStatus overall = worst(checks.stream().map(FastqQcCheck::status).toList());
        List<FastqQcRecommendation> recommendations = buildRecommendations(checks, trimCycle, overrep);

        return new FastqQcResult(overall, checks, recommendations);
    }

    public Map<String, Object> toApiMap(FastqQcResult result) {
        Map<String, Object> qc = new LinkedHashMap<>();
        qc.put("overall", result.overall().name());
        qc.put("checks", result.checks().stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.id());
            row.put("status", c.status().name());
            row.put("value", c.value());
            row.put("message", c.message());
            return row;
        }).toList());
        return qc;
    }

    public List<Map<String, Object>> recommendationsToApi(FastqQcResult result) {
        return result.recommendations().stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("severity", r.severity());
            row.put("title", r.title());
            row.put("detail", r.detail());
            return row;
        }).toList();
    }

    private FastqQcCheck checkMeanQuality(double meanQ) {
        FastqQcStatus status;
        String message;
        if (meanQ >= FastqQcThresholds.MEAN_Q_PASS) {
            status = FastqQcStatus.PASS;
            message = String.format("Mean quality %.1f is acceptable", meanQ);
        } else if (meanQ >= FastqQcThresholds.MEAN_Q_WARN) {
            status = FastqQcStatus.WARN;
            message = String.format("Mean quality %.1f below %.0f", meanQ, FastqQcThresholds.MEAN_Q_PASS);
        } else {
            status = FastqQcStatus.FAIL;
            message = String.format("Mean quality %.1f below %.0f", meanQ, FastqQcThresholds.MEAN_Q_WARN);
        }
        return new FastqQcCheck("meanQuality", status, meanQ, message);
    }

    private FastqQcCheck checkGc(double gc) {
        FastqQcStatus status;
        String message;
        if (gc >= FastqQcThresholds.GC_PASS_MIN && gc <= FastqQcThresholds.GC_PASS_MAX) {
            status = FastqQcStatus.PASS;
            message = String.format("GC %.1f%% within 40–60%%", gc);
        } else if (gc >= FastqQcThresholds.GC_WARN_MIN && gc <= FastqQcThresholds.GC_WARN_MAX) {
            status = FastqQcStatus.WARN;
            message = String.format("GC %.1f%% outside 40–60%%", gc);
        } else {
            status = FastqQcStatus.FAIL;
            message = String.format("GC %.1f%% outside 35–65%%", gc);
        }
        return new FastqQcCheck("gcContent", status, gc, message);
    }

    private FastqQcCheck checkNContent(double nPct) {
        FastqQcStatus status;
        String message;
        if (nPct <= FastqQcThresholds.N_PASS_MAX) {
            status = FastqQcStatus.PASS;
            message = String.format("N content %.2f%% is low", nPct);
        } else if (nPct <= FastqQcThresholds.N_WARN_MAX) {
            status = FastqQcStatus.WARN;
            message = String.format("N content %.2f%% elevated", nPct);
        } else {
            status = FastqQcStatus.FAIL;
            message = String.format("N content %.2f%% above %.0f%%", nPct, FastqQcThresholds.N_WARN_MAX);
        }
        return new FastqQcCheck("nContent", status, nPct, message);
    }

    private FastqQcCheck checkDuplication(double dupRate) {
        FastqQcStatus status;
        String message;
        if (dupRate <= FastqQcThresholds.DUP_PASS_MAX) {
            status = FastqQcStatus.PASS;
            message = String.format("Duplication %.1f%% is acceptable", dupRate);
        } else if (dupRate <= FastqQcThresholds.DUP_WARN_MAX) {
            status = FastqQcStatus.WARN;
            message = String.format("Duplication %.1f%% elevated", dupRate);
        } else {
            status = FastqQcStatus.FAIL;
            message = String.format("Duplication %.1f%% above %.0f%%", dupRate, FastqQcThresholds.DUP_WARN_MAX);
        }
        return new FastqQcCheck("duplicationRate", status, dupRate, message);
    }

    private FastqQcCheck checkReadCount(long readCount) {
        FastqQcStatus status;
        String message;
        if (readCount >= FastqQcThresholds.READ_COUNT_PASS) {
            status = FastqQcStatus.PASS;
            message = String.format("Read count %,d is sufficient", readCount);
        } else if (readCount >= FastqQcThresholds.READ_COUNT_WARN) {
            status = FastqQcStatus.WARN;
            message = String.format("Read count %,d below 100k", readCount);
        } else {
            status = FastqQcStatus.WARN;
            message = String.format("Read count %,d is very low (fixture or failed run?)", readCount);
        }
        return new FastqQcCheck("readCount", status, readCount, message);
    }

    private FastqQcCheck checkPerPositionQuality(Map<Integer, Double> perPos) {
        if (perPos.isEmpty()) {
            return new FastqQcCheck("perPositionQuality", FastqQcStatus.PASS, 0,
                    "No per-position quality data");
        }
        double minQ = perPos.values().stream().min(Double::compare).orElse(0.0);
        FastqQcStatus status = FastqQcStatus.PASS;
        String message = "Per-position quality stable";

        if (perPos.values().stream().anyMatch(q -> q < FastqQcThresholds.PER_POSITION_Q_WARN)) {
            status = FastqQcStatus.WARN;
            message = String.format("Quality drops below %.0f at some positions", FastqQcThresholds.PER_POSITION_Q_WARN);
        }

        List<Integer> positions = perPos.keySet().stream().sorted().toList();
        int n = positions.size();
        if (n >= 5) {
            int tailStart = (int) Math.floor(n * 0.8);
            double headAvg = positions.subList(0, tailStart).stream()
                    .mapToDouble(perPos::get).average().orElse(0);
            double tailAvg = positions.subList(tailStart, n).stream()
                    .mapToDouble(perPos::get).average().orElse(0);
            if (headAvg - tailAvg > FastqQcThresholds.TRAILING_DROP_DELTA) {
                status = FastqQcStatus.WARN;
                message = String.format("Trailing cycles avg Q %.1f vs head %.1f", tailAvg, headAvg);
            }
        }

        return new FastqQcCheck("perPositionQuality", status, minQ, message);
    }

    private FastqQcCheck checkOverrepresented(List<Map<String, Object>> overrep, long readCount) {
        if (overrep.isEmpty()) {
            return new FastqQcCheck("overrepresented", FastqQcStatus.PASS, 0, "No overrepresented sequences");
        }
        long topCount = overrep.stream()
                .mapToLong(row -> ((Number) row.getOrDefault("count", 0)).longValue())
                .max().orElse(0);
        long sampleSize = Math.min(OVERREP_SAMPLE_LIMIT, readCount);
        double fraction = sampleSize == 0 ? 0 : (double) topCount / sampleSize;
        if (fraction > FastqQcThresholds.OVERREP_FRACTION_WARN) {
            return new FastqQcCheck("overrepresented", FastqQcStatus.WARN, fraction * 100,
                    String.format("Top sequence appears in %.1f%% of sampled reads", fraction * 100));
        }
        return new FastqQcCheck("overrepresented", FastqQcStatus.PASS, fraction * 100,
                "Overrepresented sequences within normal range");
    }

    private FastqQcCheck checkAdapterContent(String adapterHitsJson) {
        Map<String, Object> hits = readAdapterHits(adapterHitsJson);
        double fraction = ((Number) hits.getOrDefault("fraction", 0.0)).doubleValue();
        long readsWithHit = ((Number) hits.getOrDefault("readsWithHit", 0L)).longValue();
        if (fraction > FastqQcThresholds.ADAPTER_FRACTION_WARN) {
            return new FastqQcCheck("adapterContent", FastqQcStatus.WARN, fraction,
                    String.format("Adapter hits in %.1f%% of sampled reads (%d hits)", fraction, readsWithHit));
        }
        return new FastqQcCheck("adapterContent", FastqQcStatus.PASS, fraction,
                "Adapter contamination within normal range");
    }

    private List<FastqQcRecommendation> buildRecommendations(
            List<FastqQcCheck> checks,
            Integer trimCycle,
            List<Map<String, Object>> overrep
    ) {
        List<FastqQcRecommendation> recs = new ArrayList<>();
        for (FastqQcCheck check : checks) {
            if (check.status() == FastqQcStatus.PASS) {
                continue;
            }
            switch (check.id()) {
                case "duplicationRate" -> recs.add(new FastqQcRecommendation(
                        check.status().name(),
                        "High duplication",
                        "Review PCR cycles and library prep; consider deduplication before alignment."));
                case "meanQuality" -> recs.add(new FastqQcRecommendation(
                        check.status().name(),
                        "Low mean quality",
                        "Consider quality trimming; inspect the per-position quality chart."));
                case "perPositionQuality" -> {
                    if (trimCycle != null) {
                        recs.add(new FastqQcRecommendation(
                                check.status().name(),
                                "Quality drop at 3' end",
                                "Consider 3' trimming to cycle " + trimCycle + " where quality falls below "
                                        + (int) FastqQcThresholds.PER_POSITION_Q_WARN + "."));
                    } else {
                        recs.add(new FastqQcRecommendation(
                                check.status().name(),
                                "Per-position quality issue",
                                "Inspect the quality-by-position chart for cycles to trim."));
                    }
                }
                case "nContent" -> recs.add(new FastqQcRecommendation(
                        check.status().name(),
                        "Elevated N bases",
                        "Check instrument cycles or sample degradation."));
                case "gcContent" -> recs.add(new FastqQcRecommendation(
                        check.status().name(),
                        "Unusual GC content",
                        "Verify species reference and library prep; compare with expected genome GC%."));
                case "readCount" -> recs.add(new FastqQcRecommendation(
                        check.status().name(),
                        "Low read count",
                        "Confirm sequencing yield; low counts may indicate failed run or test fixture."));
                case "overrepresented" -> recs.add(new FastqQcRecommendation(
                        check.status().name(),
                        "Possible adapter contamination",
                        "Inspect the Overrepresented sequences table; consider adapter trimming."));
                case "adapterContent" -> recs.add(new FastqQcRecommendation(
                        check.status().name(),
                        "Adapter contamination detected",
                        "Review adapter hits in detailed stats; trim adapters before alignment."));
                default -> {
                }
            }
        }
        if (recs.isEmpty() && checks.stream().anyMatch(c -> c.status() != FastqQcStatus.PASS)) {
            recs.add(new FastqQcRecommendation("WARN", "Review QC flags", "Inspect flagged metrics above."));
        }
        return recs;
    }

    private Integer findTrimCycle(Map<Integer, Double> perPos) {
        if (perPos.isEmpty()) {
            return null;
        }
        return perPos.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(e -> e.getValue() < FastqQcThresholds.PER_POSITION_Q_WARN)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private Map<Integer, Double> readPerPosition(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<Integer, Double> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                result.put(Integer.parseInt(e.getKey()), ((Number) e.getValue()).doubleValue());
            }
            return result;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readOverrepresented(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(row -> (Map<String, Object>) row)
                        .sorted(Comparator.comparingLong(row ->
                                -((Number) row.getOrDefault("count", 0)).longValue()))
                        .toList();
            }
            return List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> readAdapterHits(String json) {
        if (json == null || json.isBlank()) {
            return Map.of("fraction", 0.0, "readsWithHit", 0L);
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            return raw;
        } catch (Exception ex) {
            return Map.of("fraction", 0.0, "readsWithHit", 0L);
        }
    }

    private FastqQcStatus worst(List<FastqQcStatus> statuses) {
        if (statuses.contains(FastqQcStatus.FAIL)) {
            return FastqQcStatus.FAIL;
        }
        if (statuses.contains(FastqQcStatus.WARN)) {
            return FastqQcStatus.WARN;
        }
        return FastqQcStatus.PASS;
    }

    public record FastqQcCheck(String id, FastqQcStatus status, double value, String message) {
    }

    public record FastqQcRecommendation(String severity, String title, String detail) {
    }

    public record FastqQcResult(
            FastqQcStatus overall,
            List<FastqQcCheck> checks,
            List<FastqQcRecommendation> recommendations
    ) {
    }
}
