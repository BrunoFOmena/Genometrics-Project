package com.ngs.analytics.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "metric_facts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_metric_fact_analysis_key", columnNames = {"analysis_id", "metric_key"})
})
public class MetricFact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Column(name = "metric_key", nullable = false, length = 100)
    private String metricKey;

    private Double numericValue;

    @Column(length = 2000)
    private String textValue;

    @Column(length = 40)
    private String unit;

    @Column(nullable = false)
    private int metricVersion = 1;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public Analysis getAnalysis() { return analysis; }
    public void setAnalysis(Analysis analysis) { this.analysis = analysis; }
    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }
    public Double getNumericValue() { return numericValue; }
    public void setNumericValue(Double numericValue) { this.numericValue = numericValue; }
    public String getTextValue() { return textValue; }
    public void setTextValue(String textValue) { this.textValue = textValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public int getMetricVersion() { return metricVersion; }
    public void setMetricVersion(int metricVersion) { this.metricVersion = metricVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
