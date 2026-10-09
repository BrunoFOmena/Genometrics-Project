package com.ngs.analytics.analytics;

import com.ngs.analytics.domain.*;
import com.ngs.analytics.fastq.FastqQcEvaluator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class MetricFactWriter {

    private final MetricFactRepository metricFactRepository;
    private final QcCheckFactRepository qcCheckFactRepository;
    private final FastqQcEvaluator qcEvaluator;

    public MetricFactWriter(
            MetricFactRepository metricFactRepository,
            QcCheckFactRepository qcCheckFactRepository,
            FastqQcEvaluator qcEvaluator
    ) {
        this.metricFactRepository = metricFactRepository;
        this.qcCheckFactRepository = qcCheckFactRepository;
        this.qcEvaluator = qcEvaluator;
    }

    @Transactional
    public void write(Analysis analysis, FastqMetrics metrics) {
        metricFactRepository.deleteByAnalysisId(analysis.getId());
        qcCheckFactRepository.deleteByAnalysisId(analysis.getId());
        metricFactRepository.saveAll(List.of(
                fact(analysis, "read_count", metrics.getReadCount(), "reads"),
                fact(analysis, "average_length", metrics.getAvgLength(), "bp"),
                fact(analysis, "gc_content", metrics.getGcContent(), "%"),
                fact(analysis, "n_content", metrics.getNContent(), "%"),
                fact(analysis, "mean_quality", metrics.getMeanQuality(), "Phred"),
                fact(analysis, "duplication_rate", metrics.getDuplicationRate(), "%")
        ));

        FastqQcEvaluator.FastqQcResult result = qcEvaluator.evaluate(metrics);
        List<QcCheckFact> checks = new ArrayList<>();
        checks.add(qc(analysis, "overall", result.overall().name(), null,
                "Overall FASTQ quality control"));
        result.checks().forEach(check -> checks.add(qc(
                analysis, check.id(), check.status().name(), check.value(), check.message())));
        qcCheckFactRepository.saveAll(checks);
    }

    @Transactional
    public void write(Analysis analysis, VcfMetrics metrics) {
        metricFactRepository.deleteByAnalysisId(analysis.getId());
        metricFactRepository.saveAll(List.of(
                fact(analysis, "variant_count", metrics.getVariantCount(), "variants"),
                fact(analysis, "snp_count", metrics.getSnpCount(), "variants"),
                fact(analysis, "indel_count", metrics.getIndelCount(), "variants"),
                fact(analysis, "mean_variant_quality", metrics.getMeanQual(), "Phred"),
                fact(analysis, "mean_depth", metrics.getMeanDp(), "x"),
                fact(analysis, "ts_tv_ratio", metrics.getTsTvRatio(), "ratio"),
                fact(analysis, "pass_count", metrics.getPassCount(), "variants")
        ));
    }

    private MetricFact fact(Analysis analysis, String key, Number value, String unit) {
        MetricFact fact = new MetricFact();
        fact.setAnalysis(analysis);
        fact.setMetricKey(key);
        fact.setNumericValue(value.doubleValue());
        fact.setUnit(unit);
        return fact;
    }

    private QcCheckFact qc(Analysis analysis, String key, String status, Double value, String message) {
        QcCheckFact fact = new QcCheckFact();
        fact.setAnalysis(analysis);
        fact.setCheckKey(key);
        fact.setStatus(status);
        fact.setNumericValue(value);
        fact.setMessage(message);
        return fact;
    }
}
