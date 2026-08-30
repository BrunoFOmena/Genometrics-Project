package com.ngs.analytics.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "fastq_metrics")
public class FastqMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.EAGER)
    private Analysis analysis;

    private long readCount;
    private double avgLength;
    private int minLength;
    private int maxLength;
    private double gcContent;
    private double atContent;
    private double nContent;
    private double meanQuality;
    private double duplicationRate;

    private boolean pairedEnd;
    private long readCountR1;
    private long readCountR2;

    @Column(columnDefinition = "TEXT")
    private String lengthDistributionJson;

    @Column(columnDefinition = "TEXT")
    private String baseCompositionJson;

    @Column(columnDefinition = "TEXT")
    private String perPositionQualityJson;

    @Column(columnDefinition = "TEXT")
    private String overrepresentedJson;

    @Column(columnDefinition = "TEXT")
    private String phredSummaryJson;

    @Column(columnDefinition = "TEXT")
    private String adapterHitsJson;

    @Column(columnDefinition = "TEXT")
    private String qualityHeatmapJson;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public void setAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }

    public long getReadCount() {
        return readCount;
    }

    public void setReadCount(long readCount) {
        this.readCount = readCount;
    }

    public double getAvgLength() {
        return avgLength;
    }

    public void setAvgLength(double avgLength) {
        this.avgLength = avgLength;
    }

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public double getGcContent() {
        return gcContent;
    }

    public void setGcContent(double gcContent) {
        this.gcContent = gcContent;
    }

    public double getAtContent() {
        return atContent;
    }

    public void setAtContent(double atContent) {
        this.atContent = atContent;
    }

    public double getNContent() {
        return nContent;
    }

    public void setNContent(double nContent) {
        this.nContent = nContent;
    }

    public double getMeanQuality() {
        return meanQuality;
    }

    public void setMeanQuality(double meanQuality) {
        this.meanQuality = meanQuality;
    }

    public double getDuplicationRate() {
        return duplicationRate;
    }

    public void setDuplicationRate(double duplicationRate) {
        this.duplicationRate = duplicationRate;
    }

    public boolean isPairedEnd() {
        return pairedEnd;
    }

    public void setPairedEnd(boolean pairedEnd) {
        this.pairedEnd = pairedEnd;
    }

    public long getReadCountR1() {
        return readCountR1;
    }

    public void setReadCountR1(long readCountR1) {
        this.readCountR1 = readCountR1;
    }

    public long getReadCountR2() {
        return readCountR2;
    }

    public void setReadCountR2(long readCountR2) {
        this.readCountR2 = readCountR2;
    }

    public String getLengthDistributionJson() {
        return lengthDistributionJson;
    }

    public void setLengthDistributionJson(String lengthDistributionJson) {
        this.lengthDistributionJson = lengthDistributionJson;
    }

    public String getBaseCompositionJson() {
        return baseCompositionJson;
    }

    public void setBaseCompositionJson(String baseCompositionJson) {
        this.baseCompositionJson = baseCompositionJson;
    }

    public String getPerPositionQualityJson() {
        return perPositionQualityJson;
    }

    public void setPerPositionQualityJson(String perPositionQualityJson) {
        this.perPositionQualityJson = perPositionQualityJson;
    }

    public String getOverrepresentedJson() {
        return overrepresentedJson;
    }

    public void setOverrepresentedJson(String overrepresentedJson) {
        this.overrepresentedJson = overrepresentedJson;
    }

    public String getPhredSummaryJson() {
        return phredSummaryJson;
    }

    public void setPhredSummaryJson(String phredSummaryJson) {
        this.phredSummaryJson = phredSummaryJson;
    }

    public String getAdapterHitsJson() {
        return adapterHitsJson;
    }

    public void setAdapterHitsJson(String adapterHitsJson) {
        this.adapterHitsJson = adapterHitsJson;
    }

    public String getQualityHeatmapJson() {
        return qualityHeatmapJson;
    }

    public void setQualityHeatmapJson(String qualityHeatmapJson) {
        this.qualityHeatmapJson = qualityHeatmapJson;
    }
}
