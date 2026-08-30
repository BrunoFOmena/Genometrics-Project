package com.ngs.analytics.vcf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.VcfMetrics;
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
public class VcfParser {

    private static final Set<String> TRANSITIONS = Set.of("AG", "GA", "CT", "TC");
    static final int INDEL_DELTA_MAX = 20;
    static final int QUAL_MAX = 100;
    static final int DP_MAX = 200;
    static final int BIN_WIDTH = 5;
    static final int MAX_CONTIGS = 50;
    static final int MAX_TAG_IDS = 40;
    static final int MAX_SAMPLES = 20;

    private final ObjectMapper objectMapper;

    public VcfParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public VcfMetrics parse(InputStream raw, boolean gzipped) {
        try (InputStream in = gzipped ? new GZIPInputStream(raw) : raw;
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return parseReader(reader);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to parse VCF: " + ex.getMessage());
        }
    }

    private VcfMetrics parseReader(BufferedReader reader) throws IOException {
        long variantCount = 0, snp = 0, indel = 0, mnp = 0;
        double qualSum = 0;
        long qualCount = 0;
        double dpSum = 0;
        long dpCount = 0;
        long transitions = 0, transversions = 0;
        long pass = 0, fail = 0;
        Map<String, Long> chromDist = new TreeMap<>();
        Map<String, Long> filterDist = new TreeMap<>();
        Map<String, Long> geneDist = new TreeMap<>();
        Map<String, long[]> tsTvByChrom = new TreeMap<>();
        List<Double> alleleFreqs = new ArrayList<>();
        long[] indelBins = new long[INDEL_DELTA_MAX * 2 + 1];
        long indelOverflowLow = 0;
        long indelOverflowHigh = 0;
        BinnedHistogram qualHist = BinnedHistogram.ofRange(0, QUAL_MAX, BIN_WIDTH);
        BinnedHistogram dpHist = BinnedHistogram.ofRange(0, DP_MAX, BIN_WIDTH);
        HeaderCapture header = new HeaderCapture();

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("##")) {
                header.acceptMeta(line);
                continue;
            }
            if (line.startsWith("#CHROM")) {
                header.acceptColumnHeader(line);
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }
            String[] cols = line.split("\t");
            if (cols.length < 8) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid VCF row: expected at least 8 columns");
            }
            String chrom = cols[0];
            String ref = cols[3].toUpperCase(Locale.ROOT);
            String altField = cols[4].toUpperCase(Locale.ROOT);
            String qualStr = cols[5];
            String filter = cols[6];
            String info = cols[7];

            variantCount++;
            chromDist.merge(chrom, 1L, Long::sum);
            filterDist.merge(filter, 1L, Long::sum);
            if ("PASS".equalsIgnoreCase(filter) || ".".equals(filter)) {
                pass++;
            } else {
                fail++;
            }

            if (!".".equals(qualStr)) {
                try {
                    double qual = Double.parseDouble(qualStr);
                    qualSum += qual;
                    qualCount++;
                    qualHist.add(qual);
                } catch (NumberFormatException ignored) {
                }
            }

            Integer dp = extractIntInfo(info, "DP");
            if (dp != null) {
                dpSum += dp;
                dpCount++;
                dpHist.add(dp);
            }
            Double af = extractFloatInfo(info, "AF");
            if (af != null) {
                alleleFreqs.add(af);
            }
            String gene = extractStringInfo(info, "GENE");
            if (gene != null) {
                geneDist.merge(gene, 1L, Long::sum);
            }

            String firstAlt = altField.split(",")[0];
            if (ref.length() == 1 && firstAlt.length() == 1) {
                snp++;
                String pair = ref + firstAlt;
                boolean ts = TRANSITIONS.contains(pair);
                if (ts) {
                    transitions++;
                } else {
                    transversions++;
                }
                long[] counts = tsTvByChrom.computeIfAbsent(chrom, k -> new long[2]);
                counts[ts ? 0 : 1]++;
            } else if (ref.length() != firstAlt.length()) {
                indel++;
                int delta = firstAlt.length() - ref.length();
                if (delta < -INDEL_DELTA_MAX) {
                    indelOverflowLow++;
                } else if (delta > INDEL_DELTA_MAX) {
                    indelOverflowHigh++;
                } else {
                    indelBins[delta + INDEL_DELTA_MAX]++;
                }
            } else {
                mnp++;
            }
        }

        if (variantCount == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VCF contains no variants");
        }

        VcfMetrics metrics = new VcfMetrics();
        metrics.setVariantCount(variantCount);
        metrics.setSnpCount(snp);
        metrics.setIndelCount(indel);
        metrics.setMnpCount(mnp);
        metrics.setMeanQual(qualCount == 0 ? 0 : qualSum / qualCount);
        metrics.setMeanDp(dpCount == 0 ? 0 : dpSum / dpCount);
        metrics.setTransitions(transitions);
        metrics.setTransversions(transversions);
        metrics.setTsTvRatio(transversions == 0 ? transitions : transitions / (double) transversions);
        metrics.setPassCount(pass);
        metrics.setFailCount(fail);
        metrics.setChromosomeDistributionJson(toJson(chromDist));
        metrics.setFilterDistributionJson(toJson(filterDist));
        metrics.setGeneDistributionJson(toJson(geneDist));
        metrics.setIndelLengthDistributionJson(toJson(indelLengthMap(indelBins, indelOverflowLow, indelOverflowHigh)));
        metrics.setTsTvByChromosomeJson(toJson(tsTvByChromMap(tsTvByChrom)));
        metrics.setHeaderJson(toJson(header.toMap()));
        metrics.setQualHistogramJson(toJson(qualHist.toMap()));
        metrics.setDpHistogramJson(toJson(dpHist.toMap()));

        Map<String, Object> afSummary = new LinkedHashMap<>();
        if (!alleleFreqs.isEmpty()) {
            double sum = alleleFreqs.stream().mapToDouble(Double::doubleValue).sum();
            afSummary.put("count", alleleFreqs.size());
            afSummary.put("mean", sum / alleleFreqs.size());
            afSummary.put("min", alleleFreqs.stream().mapToDouble(Double::doubleValue).min().orElse(0));
            afSummary.put("max", alleleFreqs.stream().mapToDouble(Double::doubleValue).max().orElse(0));
            afSummary.put("values", alleleFreqs.stream().limit(100).toList());
        } else {
            afSummary.put("count", 0);
        }
        metrics.setAlleleFrequencyJson(toJson(afSummary));
        return metrics;
    }

    private static Map<String, Object> indelLengthMap(long[] bins, long overflowLow, long overflowHigh) {
        List<String> labels = new ArrayList<>(bins.length + 2);
        List<Long> counts = new ArrayList<>(bins.length + 2);
        labels.add("≤-21");
        counts.add(overflowLow);
        for (int delta = -INDEL_DELTA_MAX; delta <= INDEL_DELTA_MAX; delta++) {
            labels.add(String.valueOf(delta));
            counts.add(bins[delta + INDEL_DELTA_MAX]);
        }
        labels.add("≥21");
        counts.add(overflowHigh);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("labels", labels);
        map.put("counts", counts);
        return map;
    }

    private static Map<String, Object> tsTvByChromMap(Map<String, long[]> tsTvByChrom) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> entry : tsTvByChrom.entrySet()) {
            long ts = entry.getValue()[0];
            long tv = entry.getValue()[1];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ts", ts);
            row.put("tv", tv);
            row.put("tsTvRatio", tv == 0 ? ts : ts / (double) tv);
            map.put(entry.getKey(), row);
        }
        return map;
    }

    private Integer extractIntInfo(String info, String key) {
        String value = extractStringInfo(info, key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.split(",")[0]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double extractFloatInfo(String info, String key) {
        String value = extractStringInfo(info, key);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.split(",")[0]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractStringInfo(String info, String key) {
        for (String part : info.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equalsIgnoreCase(key)) {
                return kv[1];
            }
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "JSON serialization failed");
        }
    }

    static final class BinnedHistogram {
        private final int maxInclusive;
        private final int binWidth;
        private final long[] bins;
        private long overflow;

        private BinnedHistogram(int maxInclusive, int binWidth, int binCount) {
            this.maxInclusive = maxInclusive;
            this.binWidth = binWidth;
            this.bins = new long[binCount];
        }

        static BinnedHistogram ofRange(int min, int maxInclusive, int binWidth) {
            int binCount = (maxInclusive - min) / binWidth;
            return new BinnedHistogram(maxInclusive, binWidth, binCount);
        }

        void add(double value) {
            if (value > maxInclusive) {
                overflow++;
                return;
            }
            if (value < 0) {
                return;
            }
            int idx = (int) (value / binWidth);
            if (idx >= bins.length) {
                idx = bins.length - 1;
            }
            bins[idx]++;
        }

        Map<String, Object> toMap() {
            List<String> labels = new ArrayList<>(bins.length + 1);
            List<Long> counts = new ArrayList<>(bins.length + 1);
            for (int i = 0; i < bins.length; i++) {
                int from = i * binWidth;
                int to = from + binWidth;
                labels.add(from + "–" + to);
                counts.add(bins[i]);
            }
            labels.add(">" + maxInclusive);
            counts.add(overflow);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("binWidth", binWidth);
            map.put("labels", labels);
            map.put("counts", counts);
            return map;
        }
    }

    static final class HeaderCapture {
        private String fileformat;
        private String reference;
        private String source;
        private final LinkedHashSet<String> contigs = new LinkedHashSet<>();
        private int contigCount;
        private final LinkedHashSet<String> infoIds = new LinkedHashSet<>();
        private int infoCount;
        private final LinkedHashSet<String> formatIds = new LinkedHashSet<>();
        private int formatCount;
        private final LinkedHashSet<String> samples = new LinkedHashSet<>();

        void acceptMeta(String line) {
            if (line.startsWith("##fileformat=")) {
                fileformat = valueAfterEquals(line);
            } else if (line.startsWith("##reference=")) {
                reference = valueAfterEquals(line);
            } else if (line.startsWith("##source=")) {
                source = valueAfterEquals(line);
            } else if (line.startsWith("##contig=")) {
                contigCount++;
                addCapped(contigs, extractAngleId(line), MAX_CONTIGS);
            } else if (line.startsWith("##INFO=")) {
                infoCount++;
                addCapped(infoIds, extractAngleId(line), MAX_TAG_IDS);
            } else if (line.startsWith("##FORMAT=")) {
                formatCount++;
                addCapped(formatIds, extractAngleId(line), MAX_TAG_IDS);
            }
        }

        void acceptColumnHeader(String line) {
            String[] cols = line.split("\t");
            for (int i = 9; i < cols.length && samples.size() < MAX_SAMPLES; i++) {
                String name = cols[i].trim();
                if (!name.isEmpty()) {
                    samples.add(name);
                }
            }
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fileformat", fileformat);
            map.put("reference", reference);
            map.put("source", source);
            map.put("contigs", List.copyOf(contigs));
            map.put("contigCount", contigCount);
            map.put("infoIds", List.copyOf(infoIds));
            map.put("infoCount", infoCount);
            map.put("formatIds", List.copyOf(formatIds));
            map.put("formatCount", formatCount);
            map.put("samples", List.copyOf(samples));
            return map;
        }

        private static String valueAfterEquals(String line) {
            int eq = line.indexOf('=');
            if (eq < 0 || eq == line.length() - 1) {
                return null;
            }
            String value = line.substring(eq + 1).trim();
            return value.isEmpty() ? null : value;
        }

        private static String extractAngleId(String line) {
            int id = line.indexOf("ID=");
            if (id < 0) {
                return null;
            }
            int start = id + 3;
            int end = start;
            while (end < line.length()) {
                char c = line.charAt(end);
                if (c == ',' || c == '>' || c == ' ') {
                    break;
                }
                end++;
            }
            return end > start ? line.substring(start, end) : null;
        }

        private static void addCapped(Set<String> set, String value, int cap) {
            if (value != null && !value.isBlank() && set.size() < cap) {
                set.add(value);
            }
        }
    }
}
