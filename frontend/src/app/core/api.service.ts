import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import {
  AiAnswer,
  AnalyticsQuery,
  AnalyticsResult,
  MetricDefinition
} from './analytics.models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly base = environment.apiUrl;

  constructor(private http: HttpClient) {}

  projects() {
    return this.http.get<any[]>(`${this.base}/projects`);
  }

  createProject(name: string, description: string) {
    return this.http.post<any>(`${this.base}/projects`, { name, description });
  }

  project(id: string) {
    return this.http.get<any>(`${this.base}/projects/${id}`);
  }

  samples(projectId: string) {
    return this.http.get<any[]>(`${this.base}/projects/${projectId}/samples`);
  }

  createSample(projectId: string, name: string, notes: string, fastaReferenceName?: string) {
    return this.http.post<any>(`${this.base}/projects/${projectId}/samples`, {
      name, notes, fastaReferenceName
    });
  }

  allSamples() {
    return this.http.get<any[]>(`${this.base}/samples`);
  }

  upload(sampleId: string, file: File) {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<any>(`${this.base}/samples/${sampleId}/files`, form);
  }

  files(sampleId: string) {
    return this.http.get<any[]>(`${this.base}/samples/${sampleId}/files`);
  }

  analyses(sampleId: string) {
    return this.http.get<any[]>(`${this.base}/samples/${sampleId}/analyses`);
  }

  analysis(id: string) {
    return this.http.get<any>(`${this.base}/analyses/${id}`);
  }

  history() {
    return this.http.get<any[]>(`${this.base}/analyses`);
  }

  fastqMetrics(sampleId: string) {
    return this.http.get<any>(`${this.base}/samples/${sampleId}/metrics/fastq`);
  }

  fastqMetricsByAnalysis(analysisId: string) {
    return this.http.get<any>(`${this.base}/analyses/${analysisId}/metrics/fastq`);
  }

  vcfMetrics(sampleId: string) {
    return this.http.get<any>(`${this.base}/samples/${sampleId}/metrics/vcf`);
  }

  vcfMetricsByAnalysis(analysisId: string) {
    return this.http.get<any>(`${this.base}/analyses/${analysisId}/metrics/vcf`);
  }

  compare(a: string, b: string) {
    return this.http.get<any>(`${this.base}/samples/compare`, { params: { a, b } });
  }

  reportJson(sampleId: string) {
    return this.http.get<any>(`${this.base}/reports/${sampleId}`);
  }

  reportCsvUrl(sampleId: string): string {
    return `${this.base}/reports/${sampleId}/csv`;
  }

  reportPdfUrl(sampleId: string): string {
    return `${this.base}/reports/${sampleId}/pdf`;
  }

  fastqReportHtmlUrl(sampleId: string): string {
    return `${this.base}/reports/${sampleId}/fastq/html`;
  }

  fastqReportPdfUrl(sampleId: string): string {
    return `${this.base}/reports/${sampleId}/fastq/pdf`;
  }

  metricCatalog() {
    return this.http.get<MetricDefinition[]>(`${this.base}/analytics/metrics`);
  }

  analyticsQuery(query: AnalyticsQuery) {
    return this.http.post<AnalyticsResult>(`${this.base}/analytics/query`, query);
  }

  askAnalyst(request: {
    question: string;
    projectId?: string;
    sampleIds?: string[];
    metrics?: string[];
  }) {
    return this.http.post<AiAnswer>(`${this.base}/ai/ask`, request);
  }
}
