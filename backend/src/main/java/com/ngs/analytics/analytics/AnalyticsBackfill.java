package com.ngs.analytics.analytics;

import com.ngs.analytics.domain.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsBackfill implements ApplicationRunner {

    private final AnalysisRepository analysisRepository;
    private final FastqMetricsRepository fastqMetricsRepository;
    private final VcfMetricsRepository vcfMetricsRepository;
    private final MetricFactRepository metricFactRepository;
    private final MetricFactWriter writer;

    public AnalyticsBackfill(
            AnalysisRepository analysisRepository,
            FastqMetricsRepository fastqMetricsRepository,
            VcfMetricsRepository vcfMetricsRepository,
            MetricFactRepository metricFactRepository,
            MetricFactWriter writer
    ) {
        this.analysisRepository = analysisRepository;
        this.fastqMetricsRepository = fastqMetricsRepository;
        this.vcfMetricsRepository = vcfMetricsRepository;
        this.metricFactRepository = metricFactRepository;
        this.writer = writer;
    }

    @Override
    public void run(ApplicationArguments args) {
        analysisRepository.findByStatus(AnalysisStatus.DONE).stream()
                .filter(analysis -> !metricFactRepository.existsByAnalysisId(analysis.getId()))
                .forEach(analysis -> fastqMetricsRepository.findByAnalysisId(analysis.getId())
                        .ifPresentOrElse(
                                metrics -> writer.write(analysis, metrics),
                                () -> vcfMetricsRepository.findByAnalysisId(analysis.getId())
                                        .ifPresent(metrics -> writer.write(analysis, metrics))
                        ));
    }
}
