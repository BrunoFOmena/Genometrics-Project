CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS samples (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    name VARCHAR(255) NOT NULL,
    notes VARCHAR(2000),
    fasta_reference_name VARCHAR(255),
    fasta_storage_path VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS file_assets (
    id UUID PRIMARY KEY,
    sample_id UUID NOT NULL REFERENCES samples(id),
    original_filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(255) NOT NULL,
    read_end VARCHAR(255) NOT NULL,
    storage_path VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS analyses (
    id UUID PRIMARY KEY,
    sample_id UUID NOT NULL REFERENCES samples(id),
    file_asset_id UUID NOT NULL REFERENCES file_assets(id),
    mate_file_asset_id UUID REFERENCES file_assets(id),
    status VARCHAR(255) NOT NULL,
    error_message VARCHAR(4000),
    engine VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS fastq_metrics (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL UNIQUE REFERENCES analyses(id),
    read_count BIGINT NOT NULL,
    avg_length DOUBLE PRECISION NOT NULL,
    min_length INTEGER NOT NULL,
    max_length INTEGER NOT NULL,
    gc_content DOUBLE PRECISION NOT NULL,
    at_content DOUBLE PRECISION NOT NULL,
    n_content DOUBLE PRECISION NOT NULL,
    mean_quality DOUBLE PRECISION NOT NULL,
    duplication_rate DOUBLE PRECISION NOT NULL,
    paired_end BOOLEAN NOT NULL,
    read_count_r1 BIGINT NOT NULL,
    read_count_r2 BIGINT NOT NULL,
    length_distribution_json TEXT,
    base_composition_json TEXT,
    per_position_quality_json TEXT,
    overrepresented_json TEXT,
    phred_summary_json TEXT,
    adapter_hits_json TEXT,
    quality_heatmap_json TEXT
);

CREATE TABLE IF NOT EXISTS vcf_metrics (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL UNIQUE REFERENCES analyses(id),
    variant_count BIGINT NOT NULL,
    snp_count BIGINT NOT NULL,
    indel_count BIGINT NOT NULL,
    mnp_count BIGINT NOT NULL,
    mean_qual DOUBLE PRECISION NOT NULL,
    mean_dp DOUBLE PRECISION NOT NULL,
    transitions BIGINT NOT NULL,
    transversions BIGINT NOT NULL,
    ts_tv_ratio DOUBLE PRECISION NOT NULL,
    pass_count BIGINT NOT NULL,
    fail_count BIGINT NOT NULL,
    chromosome_distribution_json TEXT,
    allele_frequency_json TEXT,
    gene_distribution_json TEXT,
    filter_distribution_json TEXT,
    indel_length_distribution_json TEXT,
    ts_tv_by_chromosome_json TEXT,
    header_json TEXT,
    qual_histogram_json TEXT,
    dp_histogram_json TEXT
);

CREATE TABLE IF NOT EXISTS metric_facts (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    metric_key VARCHAR(100) NOT NULL,
    numeric_value DOUBLE PRECISION,
    text_value VARCHAR(2000),
    unit VARCHAR(40),
    metric_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_metric_fact_analysis_key UNIQUE (analysis_id, metric_key)
);

CREATE INDEX IF NOT EXISTS idx_metric_facts_analysis ON metric_facts(analysis_id);
CREATE INDEX IF NOT EXISTS idx_metric_facts_key ON metric_facts(metric_key);

CREATE TABLE IF NOT EXISTS qc_check_facts (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    check_key VARCHAR(100) NOT NULL,
    status VARCHAR(10) NOT NULL,
    numeric_value DOUBLE PRECISION,
    message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_qc_check_analysis_key UNIQUE (analysis_id, check_key)
);

CREATE INDEX IF NOT EXISTS idx_qc_checks_analysis ON qc_check_facts(analysis_id);

CREATE TABLE IF NOT EXISTS ai_interactions (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    question VARCHAR(4000) NOT NULL,
    answer TEXT NOT NULL,
    model VARCHAR(120) NOT NULL,
    tool_trace TEXT,
    citations TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
