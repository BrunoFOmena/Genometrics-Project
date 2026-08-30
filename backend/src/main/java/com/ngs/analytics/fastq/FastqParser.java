package com.ngs.analytics.fastq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.FastqMetrics;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

@Component
public class FastqParser {

    private static final int OVERREP_SAMPLE_LIMIT = 50_000;
    private static final int HEATMAP_MAX_POSITION = 150;
    private static final int HEATMAP_Q_BINS = 41;

    private final ObjectMapper objectMapper;

    public FastqParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FastqMetrics parse(InputStream raw, boolean gzipped) {
        try (InputStream in = gzipped ? new GZIPInputStream(raw) : raw;
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            FastqParseAccumulator acc = new FastqParseAccumulator();
            readAll(reader, acc);
            return acc.toMetrics(objectMapper, false, 0, 0);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to parse FASTQ: " + ex.getMessage());
        }
    }

    public FastqMetrics parsePaired(InputStream r1Raw, InputStream r2Raw, boolean r1Gz, boolean r2Gz) {
        try (InputStream in1 = r1Gz ? new GZIPInputStream(r1Raw) : r1Raw;
             InputStream in2 = r2Gz ? new GZIPInputStream(r2Raw) : r2Raw;
             BufferedReader r1 = new BufferedReader(new InputStreamReader(in1, StandardCharsets.UTF_8));
             BufferedReader r2 = new BufferedReader(new InputStreamReader(in2, StandardCharsets.UTF_8))) {
            FastqParseAccumulator acc = new FastqParseAccumulator();
            long r1Count = 0;
            long r2Count = 0;
            while (true) {
                String[] rec1 = readRecord(r1);
                String[] rec2 = readRecord(r2);
                if (rec1 == null && rec2 == null) {
                    break;
                }
                if (rec1 == null || rec2 == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Paired FASTQ read count mismatch: R1=" + r1Count + ", R2=" + r2Count);
                }
                acc.processRead(rec1[0], rec1[1]);
                r1Count++;
                acc.processRead(rec2[0], rec2[1]);
                r2Count++;
            }
            if (r1Count == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FASTQ contains no reads");
            }
            if (r1Count != r2Count) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Paired FASTQ read count mismatch: R1=" + r1Count + ", R2=" + r2Count);
            }
            return acc.toMetrics(objectMapper, true, r1Count, r2Count);
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to parse paired FASTQ: " + ex.getMessage());
        }
    }

    private void readAll(BufferedReader reader, FastqParseAccumulator acc) throws IOException {
        while (true) {
            String[] rec = readRecord(reader);
            if (rec == null) {
                break;
            }
            acc.processRead(rec[0], rec[1]);
        }
        if (acc.readCount == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FASTQ contains no reads");
        }
    }

    /** @return [sequence, quality] or null at EOF */
    private String[] readRecord(BufferedReader reader) throws IOException {
        String header;
        while ((header = reader.readLine()) != null) {
            if (header.isBlank()) {
                continue;
            }
            if (!header.startsWith("@")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid FASTQ: expected '@' header");
            }
            String seq = reader.readLine();
            String plus = reader.readLine();
            String qual = reader.readLine();
            if (seq == null || plus == null || qual == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Truncated FASTQ record");
            }
            if (!plus.startsWith("+")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid FASTQ: expected '+' line");
            }
            return new String[]{seq, qual};
        }
        return null;
    }

    static final class FastqParseAccumulator {
        long readCount;
        long totalLength;
        int minLength = Integer.MAX_VALUE;
        int maxLength;
        long basesA, basesT, basesC, basesG, basesN, totalBases;
        double qualitySum;
        long qualityCount;
        Map<Integer, Long> lengthDist = new TreeMap<>();
        Map<Integer, Double> posQualitySum = new HashMap<>();
        Map<Integer, Long> posQualityCount = new HashMap<>();
        Map<String, Long> sequenceCounts = new HashMap<>();
        Set<String> seen = new HashSet<>();
        long duplicates;
        long sampled;
        long adapterSampled;
        long readsWithAdapterHit;
        Map<String, Long> adapterHitCounts = new LinkedHashMap<>();
        Map<String, Map<Integer, Long>> adapterPositionHits = new LinkedHashMap<>();
        long[][] heatmap = new long[HEATMAP_MAX_POSITION][HEATMAP_Q_BINS];

        void processRead(String seq, String qual) {
            int len = seq.length();
            readCount++;
            totalLength += len;
            minLength = Math.min(minLength, len);
            maxLength = Math.max(maxLength, len);
            lengthDist.merge(len, 1L, Long::sum);

            for (int i = 0; i < len; i++) {
                char b = Character.toUpperCase(seq.charAt(i));
                totalBases++;
                switch (b) {
                    case 'A' -> basesA++;
                    case 'T' -> basesT++;
                    case 'C' -> basesC++;
                    case 'G' -> basesG++;
                    default -> basesN++;
                }
            }

            int qLen = Math.min(len, qual.length());
            for (int i = 0; i < qLen; i++) {
                int phred = qual.charAt(i) - 33;
                qualitySum += phred;
                qualityCount++;
                int pos = i + 1;
                posQualitySum.merge(pos, (double) phred, Double::sum);
                posQualityCount.merge(pos, 1L, Long::sum);
                if (pos <= HEATMAP_MAX_POSITION) {
                    int bin = Math.max(0, Math.min(HEATMAP_Q_BINS - 1, phred));
                    heatmap[pos - 1][bin]++;
                }
            }

            if (sampled < OVERREP_SAMPLE_LIMIT) {
                sampled++;
                if (!seen.add(seq)) {
                    duplicates++;
                }
                sequenceCounts.merge(seq, 1L, Long::sum);
            }

            if (adapterSampled < OVERREP_SAMPLE_LIMIT) {
                adapterSampled++;
                detectAdapters(seq);
            }
        }

        private void detectAdapters(String seq) {
            String upper = seq.toUpperCase(Locale.ROOT);
            boolean hit = false;
            for (AdapterCatalog.Adapter adapter : AdapterCatalog.all()) {
                int idx = upper.indexOf(adapter.sequence());
                if (idx >= 0) {
                    hit = true;
                    adapterHitCounts.merge(adapter.name(), 1L, Long::sum);
                    adapterPositionHits
                            .computeIfAbsent(adapter.name(), k -> new TreeMap<>())
                            .merge(idx + 1, 1L, Long::sum);
                }
            }
            if (hit) {
                readsWithAdapterHit++;
            }
        }

        FastqMetrics toMetrics(ObjectMapper mapper, boolean pairedEnd, long readCountR1, long readCountR2) {
            FastqMetrics metrics = new FastqMetrics();
            metrics.setPairedEnd(pairedEnd);
            metrics.setReadCountR1(readCountR1);
            metrics.setReadCountR2(readCountR2);
            metrics.setReadCount(readCount);
            metrics.setAvgLength(readCount == 0 ? 0 : totalLength / (double) readCount);
            metrics.setMinLength(minLength == Integer.MAX_VALUE ? 0 : minLength);
            metrics.setMaxLength(maxLength);
            metrics.setGcContent(totalBases == 0 ? 0 : (basesG + basesC) * 100.0 / totalBases);
            metrics.setAtContent(totalBases == 0 ? 0 : (basesA + basesT) * 100.0 / totalBases);
            metrics.setNContent(totalBases == 0 ? 0 : basesN * 100.0 / totalBases);
            metrics.setMeanQuality(qualityCount == 0 ? 0 : qualitySum / qualityCount);
            metrics.setDuplicationRate(sampled == 0 ? 0 : duplicates * 100.0 / sampled);
            metrics.setLengthDistributionJson(toJson(mapper, lengthDist));
            metrics.setBaseCompositionJson(toJson(mapper, Map.of(
                    "A", basesA, "T", basesT, "C", basesC, "G", basesG, "N", basesN
            )));

            Map<Integer, Double> perPos = new TreeMap<>();
            for (Integer pos : posQualitySum.keySet()) {
                perPos.put(pos, posQualitySum.get(pos) / posQualityCount.get(pos));
            }
            metrics.setPerPositionQualityJson(toJson(mapper, perPos));

            List<Map<String, Object>> overrep = sequenceCounts.entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(10)
                    .map(e -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("sequence", e.getKey());
                        row.put("count", e.getValue());
                        return row;
                    })
                    .toList();
            metrics.setOverrepresentedJson(toJson(mapper, overrep));
            metrics.setPhredSummaryJson(toJson(mapper, Map.of(
                    "mean", metrics.getMeanQuality(),
                    "encoding", "Phred+33"
            )));
            metrics.setAdapterHitsJson(toJson(mapper, buildAdapterHits()));
            metrics.setQualityHeatmapJson(toJson(mapper, buildHeatmap()));
            return metrics;
        }

        private Map<String, Object> buildAdapterHits() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("readsSampled", adapterSampled);
            result.put("readsWithHit", readsWithAdapterHit);
            result.put("fraction", adapterSampled == 0 ? 0 : readsWithAdapterHit * 100.0 / adapterSampled);
            List<Map<String, Object>> adapters = new ArrayList<>();
            for (AdapterCatalog.Adapter adapter : AdapterCatalog.all()) {
                long count = adapterHitCounts.getOrDefault(adapter.name(), 0L);
                if (count == 0) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", adapter.name());
                row.put("sequence", adapter.sequence());
                row.put("count", count);
                row.put("positions", adapterPositionHits.getOrDefault(adapter.name(), Map.of()));
                adapters.add(row);
            }
            result.put("adapters", adapters);
            return result;
        }

        private Map<String, Object> buildHeatmap() {
            int maxPos = 0;
            List<List<Number>> data = new ArrayList<>();
            for (int p = 0; p < HEATMAP_MAX_POSITION; p++) {
                for (int q = 0; q < HEATMAP_Q_BINS; q++) {
                    if (heatmap[p][q] > 0) {
                        maxPos = Math.max(maxPos, p + 1);
                        data.add(List.of(p + 1, q, heatmap[p][q]));
                    }
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("maxPosition", maxPos);
            result.put("bins", HEATMAP_Q_BINS);
            result.put("data", data);
            return result;
        }

        private static String toJson(ObjectMapper mapper, Object value) {
            try {
                return mapper.writeValueAsString(value);
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "JSON serialization failed");
            }
        }
    }
}
