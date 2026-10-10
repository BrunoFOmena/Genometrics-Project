package com.ngs.analytics.vcf;

import com.ngs.analytics.domain.VcfMetrics;

public record VcfParseResult(
        long variantCount,
        long snpCount,
        long indelCount,
        long mnpCount,
        double meanQuality,
        double meanDepth,
        long transitions,
        long transversions,
        double tsTvRatio,
        long passCount,
        long failCount,
        String chromosomeDistributionJson,
        String alleleFrequencyJson,
        String geneDistributionJson,
        String filterDistributionJson,
        String indelLengthDistributionJson,
        String tsTvByChromosomeJson,
        String headerJson,
        String qualityHistogramJson,
        String depthHistogramJson
) {
    public static VcfParseResult fromEntity(VcfMetrics metrics) {
        return new VcfParseResult(
                metrics.getVariantCount(), metrics.getSnpCount(), metrics.getIndelCount(), metrics.getMnpCount(),
                metrics.getMeanQual(), metrics.getMeanDp(), metrics.getTransitions(), metrics.getTransversions(),
                metrics.getTsTvRatio(), metrics.getPassCount(), metrics.getFailCount(),
                metrics.getChromosomeDistributionJson(), metrics.getAlleleFrequencyJson(),
                metrics.getGeneDistributionJson(), metrics.getFilterDistributionJson(),
                metrics.getIndelLengthDistributionJson(), metrics.getTsTvByChromosomeJson(),
                metrics.getHeaderJson(), metrics.getQualHistogramJson(), metrics.getDpHistogramJson()
        );
    }

    public VcfMetrics toEntity() {
        VcfMetrics metrics = new VcfMetrics();
        metrics.setVariantCount(variantCount);
        metrics.setSnpCount(snpCount);
        metrics.setIndelCount(indelCount);
        metrics.setMnpCount(mnpCount);
        metrics.setMeanQual(meanQuality);
        metrics.setMeanDp(meanDepth);
        metrics.setTransitions(transitions);
        metrics.setTransversions(transversions);
        metrics.setTsTvRatio(tsTvRatio);
        metrics.setPassCount(passCount);
        metrics.setFailCount(failCount);
        metrics.setChromosomeDistributionJson(chromosomeDistributionJson);
        metrics.setAlleleFrequencyJson(alleleFrequencyJson);
        metrics.setGeneDistributionJson(geneDistributionJson);
        metrics.setFilterDistributionJson(filterDistributionJson);
        metrics.setIndelLengthDistributionJson(indelLengthDistributionJson);
        metrics.setTsTvByChromosomeJson(tsTvByChromosomeJson);
        metrics.setHeaderJson(headerJson);
        metrics.setQualHistogramJson(qualityHistogramJson);
        metrics.setDpHistogramJson(depthHistogramJson);
        return metrics;
    }
}
