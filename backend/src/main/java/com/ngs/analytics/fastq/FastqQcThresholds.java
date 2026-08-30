package com.ngs.analytics.fastq;

/**
 * Default FASTQ QC bounds (v1). Feature 15 will externalize per-lab profiles.
 */
public final class FastqQcThresholds {

    public static final double MEAN_Q_PASS = 28.0;
    public static final double MEAN_Q_WARN = 20.0;

    public static final double GC_PASS_MIN = 40.0;
    public static final double GC_PASS_MAX = 60.0;
    public static final double GC_WARN_MIN = 35.0;
    public static final double GC_WARN_MAX = 65.0;

    public static final double N_PASS_MAX = 1.0;
    public static final double N_WARN_MAX = 5.0;

    public static final double DUP_PASS_MAX = 20.0;
    public static final double DUP_WARN_MAX = 50.0;

    public static final long READ_COUNT_PASS = 100_000L;
    public static final long READ_COUNT_WARN = 10_000L;

    public static final double PER_POSITION_Q_WARN = 20.0;
    public static final double TRAILING_DROP_DELTA = 10.0;
    public static final double OVERREP_FRACTION_WARN = 0.05;
    public static final double ADAPTER_FRACTION_WARN = 5.0;

    private FastqQcThresholds() {
    }
}
