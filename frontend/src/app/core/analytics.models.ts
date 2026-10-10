export interface MetricDefinition {
  key: string;
  label: string;
  domain: 'FASTQ' | 'VCF';
  unit: string;
  description: string;
  aggregations: string[];
  version: number;
}

export interface AnalyticsFilters {
  projectId?: string;
  sampleIds?: string[];
  statuses?: string[];
  from?: string;
  to?: string;
}

export interface AnalyticsQuery {
  metrics: string[];
  dimensions?: string[];
  filters?: AnalyticsFilters;
  aggregation?: 'NONE' | 'AVG' | 'SUM' | 'MIN' | 'MAX';
  limit?: number;
}

export interface AnalyticsResult {
  columns: string[];
  rows: Record<string, unknown>[];
  total: number;
  truncated: boolean;
}

export interface AiCitation {
  analysisId: string;
  sampleId: string;
  metric: string;
  value: number;
  unit: string;
}

export interface AiAnswer {
  interactionId: string;
  answer: string;
  model: string;
  tools: string[];
  citations: AiCitation[];
}
