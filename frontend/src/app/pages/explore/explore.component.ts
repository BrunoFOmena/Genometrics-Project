import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { ApiService } from '../../core/api.service';
import {
  AiAnswer,
  AnalyticsResult,
  MetricDefinition
} from '../../core/analytics.models';

@Component({
  selector: 'app-explore',
  standalone: true,
  imports: [CommonModule, FormsModule, CardModule, TableModule],
  template: `
    <section>
      <header class="page-head">
        <div>
          <h1>Explore</h1>
          <p>Query catalogued sequencing metrics and ask grounded questions.</p>
        </div>
      </header>

      <div class="explore-grid">
        <p-card header="Query">
          <label class="field-label" for="metrics">Metrics</label>
          <select id="metrics" class="native-select multi" multiple [(ngModel)]="selectedMetrics"
                  (change)="ensureAggregation()">
            <option *ngFor="let metric of catalog" [value]="metric.key">
              {{ metric.label }} ({{ metric.unit }})
            </option>
          </select>

          <label class="field-label" for="aggregation">Aggregation</label>
          <select id="aggregation" class="native-select" [(ngModel)]="aggregation">
            <option value="NONE">No aggregation</option>
            <option *ngFor="let option of availableAggregations" [value]="option">
              {{ option }} by sample and metric
            </option>
          </select>

          <label class="field-label" for="project">Project</label>
          <select id="project" class="native-select" [(ngModel)]="projectId">
            <option value="">All projects</option>
            <option *ngFor="let project of projects" [value]="project.id">{{ project.name }}</option>
          </select>

          <button class="primary-btn" type="button" (click)="runQuery()"
                  [disabled]="loading || !selectedMetrics.length">
            {{ loading ? 'Running…' : 'Run query' }}
          </button>
          <p class="error-text" *ngIf="error">{{ error }}</p>
        </p-card>

        <p-card header="Analyst">
          <label class="field-label" for="question">Question</label>
          <textarea id="question" rows="5" [(ngModel)]="question"
                    placeholder="Why did these samples fail quality control?"></textarea>
          <button class="primary-btn" type="button" (click)="ask()"
                  [disabled]="asking || !question.trim()">
            {{ asking ? 'Analysing…' : 'Ask with current context' }}
          </button>
          <div class="analyst-answer" *ngIf="answer">
            <p>{{ answer.answer }}</p>
            <small>Model: {{ answer.model }} · {{ answer.citations.length }} grounded facts</small>
          </div>
        </p-card>
      </div>

      <p-card header="Results" *ngIf="result">
        <p class="muted">{{ result.total }} rows<span *ngIf="result.truncated"> (limited)</span></p>
        <div class="result-bars" *ngIf="chartRows.length">
          <div class="result-bar-row" *ngFor="let item of chartRows">
            <span>{{ item.label }}</span>
            <div class="result-bar-track">
              <div class="result-bar" [style.width.%]="item.width"></div>
            </div>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <p-table [value]="result.rows" [scrollable]="true" scrollHeight="440px">
          <ng-template pTemplate="header">
            <tr><th *ngFor="let column of result.columns">{{ column }}</th></tr>
          </ng-template>
          <ng-template pTemplate="body" let-row>
            <tr><td *ngFor="let column of result.columns">{{ row[column] }}</td></tr>
          </ng-template>
        </p-table>
      </p-card>
    </section>
  `,
  styles: [`
    .explore-grid { display: grid; grid-template-columns: minmax(260px, 1fr) minmax(320px, 2fr); gap: 1rem; margin-bottom: 1rem; }
    .field-label { display: block; margin: .85rem 0 .35rem; font-weight: 600; }
    .native-select, textarea { width: 100%; padding: .65rem; border: 1px solid var(--border); border-radius: 0; background: var(--surface); color: inherit; }
    .native-select.multi { min-height: 12rem; }
    .primary-btn { margin-top: 1rem; padding: .65rem 1rem; border: 0; border-radius: 0; cursor: pointer; }
    .primary-btn:disabled { opacity: .55; cursor: default; }
    .analyst-answer { margin-top: 1rem; padding: .8rem; border-left: 3px solid var(--primary); white-space: pre-wrap; }
    .result-bars { display: grid; gap: .45rem; margin: 1rem 0 1.5rem; }
    .result-bar-row { display: grid; grid-template-columns: minmax(120px, 1fr) 3fr 80px; gap: .75rem; align-items: center; }
    .result-bar-track { height: .65rem; border-radius: 0; overflow: hidden; background: rgba(128, 128, 128, .18); }
    .result-bar { height: 100%; min-width: 2px; background: var(--primary-color); }
    .error-text { color: #b42318; }
    @media (max-width: 850px) { .explore-grid { grid-template-columns: 1fr; } }
  `]
})
export class ExploreComponent implements OnInit {
  catalog: MetricDefinition[] = [];
  projects: any[] = [];
  selectedMetrics: string[] = [];
  aggregation: 'NONE' | 'AVG' | 'SUM' | 'MIN' | 'MAX' = 'NONE';
  projectId = '';
  question = '';
  result?: AnalyticsResult;
  answer?: AiAnswer;
  loading = false;
  asking = false;
  error = '';

  constructor(private api: ApiService) {}

  get availableAggregations(): string[] {
    const selected = this.catalog.filter(metric => this.selectedMetrics.includes(metric.key));
    if (!selected.length) return [];
    return ['AVG', 'SUM', 'MIN', 'MAX'].filter(aggregation =>
      selected.every(metric => metric.aggregations.includes(aggregation)));
  }

  get chartRows(): { label: string; value: number; width: number }[] {
    const rows = (this.result?.rows ?? [])
      .map(row => ({
        label: `${row['sample'] ?? 'Sample'} · ${row['metric'] ?? 'metric'}`,
        value: Number(row['value'])
      }))
      .filter(row => Number.isFinite(row.value))
      .slice(0, 12);
    const max = Math.max(...rows.map(row => Math.abs(row.value)), 0);
    return rows.map(row => ({ ...row, width: max === 0 ? 0 : Math.abs(row.value) * 100 / max }));
  }

  ngOnInit(): void {
    this.api.metricCatalog().subscribe(metrics => {
      this.catalog = metrics;
      this.selectedMetrics = metrics.slice(0, 3).map(metric => metric.key);
    });
    this.api.projects().subscribe(projects => this.projects = projects);
  }

  runQuery(): void {
    this.loading = true;
    this.error = '';
    this.api.analyticsQuery({
      metrics: this.selectedMetrics,
      dimensions: this.aggregation === 'NONE' ? [] : ['sample', 'metric'],
      filters: {
        projectId: this.projectId || undefined,
        statuses: ['DONE']
      },
      aggregation: this.aggregation,
      limit: 500
    }).subscribe({
      next: result => {
        this.result = result;
        this.loading = false;
      },
      error: () => {
        this.error = 'The analytics query could not be completed.';
        this.loading = false;
      }
    });
  }

  ensureAggregation(): void {
    if (this.aggregation !== 'NONE' && !this.availableAggregations.includes(this.aggregation)) {
      this.aggregation = 'NONE';
    }
  }

  ask(): void {
    this.asking = true;
    this.answer = undefined;
    this.api.askAnalyst({
      question: this.question,
      projectId: this.projectId || undefined,
      metrics: this.selectedMetrics
    }).subscribe({
      next: answer => {
        this.answer = answer;
        this.asking = false;
      },
      error: () => {
        this.error = 'The local analyst is unavailable.';
        this.asking = false;
      }
    });
  }
}
