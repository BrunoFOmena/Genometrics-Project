package com.ngs.analytics.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "vcf_metrics")
public class VcfMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.EAGER)
    private Analysis analysis;

    private long variantCount;
    private long snpCount;
    private long indelCount;
    private long mnpCount;
    private double meanQual;
    private double meanDp;
    private long transitions;
    private long transversions;
    private double tsTvRatio;
    private long passCount;
    private long failCount;

    @Column(columnDefinition = "TEXT")
    private String chromosomeDistributionJson;

    @Column(columnDefinition = "TEXT")
    private String alleleFrequencyJson;

    @Column(columnDefinition = "TEXT")
    private String geneDistributionJson;

    @Column(columnDefinition = "TEXT")
    private String filterDistributionJson;

    @Column(columnDefinition = "TEXT")
    private String indelLengthDistributionJson;

    @Column(columnDefinition = "TEXT")
    private String tsTvByChromosomeJson;

    @Column(columnDefinition = "TEXT")
    private String headerJson;

    @Column(columnDefinition = "TEXT")
    private String qualHistogramJson;

    @Column(columnDefinition = "TEXT")
    private String dpHistogramJson;

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

    public long getVariantCount() {
        return variantCount;
    }

    public void setVariantCount(long variantCount) {
        this.variantCount = variantCount;
    }

    public long getSnpCount() {
        return snpCount;
    }

    public void setSnpCount(long snpCount) {
        this.snpCount = snpCount;
    }

    public long getIndelCount() {
        return indelCount;
    }

    public void setIndelCount(long indelCount) {
        this.indelCount = indelCount;
    }

    public long getMnpCount() {
        return mnpCount;
    }

    public void setMnpCount(long mnpCount) {
        this.mnpCount = mnpCount;
    }

    public double getMeanQual() {
        return meanQual;
    }

    public void setMeanQual(double meanQual) {
        this.meanQual = meanQual;
    }

    public double getMeanDp() {
        return meanDp;
    }

    public void setMeanDp(double meanDp) {
        this.meanDp = meanDp;
    }

    public long getTransitions() {
        return transitions;
    }

    public void setTransitions(long transitions) {
        this.transitions = transitions;
    }

    public long getTransversions() {
        return transversions;
    }

    public void setTransversions(long transversions) {
        this.transversions = transversions;
    }

    public double getTsTvRatio() {
        return tsTvRatio;
    }

    public void setTsTvRatio(double tsTvRatio) {
        this.tsTvRatio = tsTvRatio;
    }

    public long getPassCount() {
        return passCount;
    }

    public void setPassCount(long passCount) {
        this.passCount = passCount;
    }

    public long getFailCount() {
        return failCount;
    }

    public void setFailCount(long failCount) {
        this.failCount = failCount;
    }

    public String getChromosomeDistributionJson() {
        return chromosomeDistributionJson;
    }

    public void setChromosomeDistributionJson(String chromosomeDistributionJson) {
        this.chromosomeDistributionJson = chromosomeDistributionJson;
    }

    public String getAlleleFrequencyJson() {
        return alleleFrequencyJson;
    }

    public void setAlleleFrequencyJson(String alleleFrequencyJson) {
        this.alleleFrequencyJson = alleleFrequencyJson;
    }

    public String getGeneDistributionJson() {
        return geneDistributionJson;
    }

    public void setGeneDistributionJson(String geneDistributionJson) {
        this.geneDistributionJson = geneDistributionJson;
    }

    public String getFilterDistributionJson() {
        return filterDistributionJson;
    }

    public void setFilterDistributionJson(String filterDistributionJson) {
        this.filterDistributionJson = filterDistributionJson;
    }

    public String getIndelLengthDistributionJson() {
        return indelLengthDistributionJson;
    }

    public void setIndelLengthDistributionJson(String indelLengthDistributionJson) {
        this.indelLengthDistributionJson = indelLengthDistributionJson;
    }

    public String getTsTvByChromosomeJson() {
        return tsTvByChromosomeJson;
    }

    public void setTsTvByChromosomeJson(String tsTvByChromosomeJson) {
        this.tsTvByChromosomeJson = tsTvByChromosomeJson;
    }

    public String getHeaderJson() {
        return headerJson;
    }

    public void setHeaderJson(String headerJson) {
        this.headerJson = headerJson;
    }

    public String getQualHistogramJson() {
        return qualHistogramJson;
    }

    public void setQualHistogramJson(String qualHistogramJson) {
        this.qualHistogramJson = qualHistogramJson;
    }

    public String getDpHistogramJson() {
        return dpHistogramJson;
    }

    public void setDpHistogramJson(String dpHistogramJson) {
        this.dpHistogramJson = dpHistogramJson;
    }
}
