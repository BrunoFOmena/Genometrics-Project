package com.ngs.analytics.analytics;

import com.ngs.analytics.fastq.FastqQcThresholds;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MetricCatalog {

    public record Definition(
            String key,
            String label,
            String domain,
            String unit,
            String description,
            List<String> aggregations,
            int version
    ) {}

    private final Map<String, Definition> definitions = new LinkedHashMap<>();

    public MetricCatalog() {
        add("read_count", "Read count", "FASTQ", "reads", "Total reads parsed", "SUM", "AVG", "MIN", "MAX");
        add("average_length", "Average length", "FASTQ", "bp", "Average read length", "AVG", "MIN", "MAX");
        add("gc_content", "GC content", "FASTQ", "%", "Percentage of G and C bases", "AVG", "MIN", "MAX");
        add("n_content", "N content", "FASTQ", "%", "Percentage of unknown bases", "AVG", "MIN", "MAX");
        add("mean_quality", "Mean quality", "FASTQ", "Phred", "Mean Phred quality score", "AVG", "MIN", "MAX");
        add("duplication_rate", "Duplication rate", "FASTQ", "%", "Estimated duplicate read percentage", "AVG", "MIN", "MAX");
        add("variant_count", "Variant count", "VCF", "variants", "Total variants parsed", "SUM", "AVG", "MIN", "MAX");
        add("snp_count", "SNP count", "VCF", "variants", "Single nucleotide variants", "SUM", "AVG", "MIN", "MAX");
        add("indel_count", "Indel count", "VCF", "variants", "Insertion and deletion variants", "SUM", "AVG", "MIN", "MAX");
        add("mean_variant_quality", "Mean variant quality", "VCF", "Phred", "Mean VCF QUAL", "AVG", "MIN", "MAX");
        add("mean_depth", "Mean depth", "VCF", "x", "Mean sequencing depth", "AVG", "MIN", "MAX");
        add("ts_tv_ratio", "Ts/Tv ratio", "VCF", "ratio", "Transition to transversion ratio", "AVG", "MIN", "MAX");
        add("pass_count", "PASS count", "VCF", "variants", "Variants passing filters", "SUM", "AVG", "MIN", "MAX");
    }

    private void add(String key, String label, String domain, String unit, String description,
                     String... aggregations) {
        definitions.put(key, new Definition(
                key, label, domain, unit, description, List.of(aggregations), 1));
    }

    public List<Definition> all() {
        return List.copyOf(definitions.values());
    }

    public Definition require(String key) {
        Definition definition = definitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown metric: " + key);
        }
        return definition;
    }

    public boolean contains(String key) {
        return definitions.containsKey(key);
    }

    public List<String> qcKnowledge() {
        return List.of(
                "mean_quality PASS >= " + FastqQcThresholds.MEAN_Q_PASS
                        + ", WARN >= " + FastqQcThresholds.MEAN_Q_WARN + ", otherwise FAIL",
                "gc_content PASS between " + FastqQcThresholds.GC_PASS_MIN + " and "
                        + FastqQcThresholds.GC_PASS_MAX + " percent",
                "n_content PASS <= " + FastqQcThresholds.N_PASS_MAX
                        + " percent, WARN <= " + FastqQcThresholds.N_WARN_MAX + " percent",
                "duplication_rate PASS <= " + FastqQcThresholds.DUP_PASS_MAX
                        + " percent, WARN <= " + FastqQcThresholds.DUP_WARN_MAX + " percent"
        );
    }
}
