import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import { BarChart } from 'echarts/charts';
import { GridComponent, MarkLineComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { EChartsOption } from 'echarts';
import { TagModule } from 'primeng/tag';
import { forkJoin, of, Subscription } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../core/api.service';

echarts.use([BarChart, GridComponent, MarkLineComponent, TooltipComponent, CanvasRenderer]);

interface NamedMetric {
  key: string;
  title: string;
  unit: string;
  marks?: number[];
}

interface PanelChart {
  title: string;
  unit: string;
  figure: string;
  option: EChartsOption;
}

const FASTQ_METRICS: NamedMetric[] = [
  { key: 'meanQuality', title: 'Mean quality', unit: 'Phred', marks: [28] },
  { key: 'gcContent', title: 'GC content', unit: '%', marks: [40, 60] },
  { key: 'nContent', title: 'N content', unit: '%', marks: [1] },
  { key: 'duplicationRate', title: 'Duplication rate', unit: '%', marks: [20] },
  { key: 'readCount', title: 'Read count', unit: 'reads' },
  { key: 'avgLength', title: 'Average length', unit: 'bp' }
];

const VCF_METRICS: NamedMetric[] = [
  { key: 'variantCount', title: 'Total variants', unit: 'variants' },
  { key: 'meanQual', title: 'Mean variant quality', unit: 'Phred' },
  { key: 'meanDp', title: 'Mean depth', unit: 'x' },
  { key: 'tsTvRatio', title: 'Ts/Tv ratio', unit: 'ratio', marks: [2] },
  { key: 'passCount', title: 'PASS variants', unit: 'variants' }
];

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, NgxEchartsDirective, TagModule],
  providers: [provideEchartsCore({ echarts })],
  template: `
    <section>
      <header class="page-head">
        <div>
          <h1>Panel</h1>
          <p>Named charts for the metrics this workspace already calculates.</p>
        </div>
      </header>

      <div class="panel-controls">
        <label>
          Sample
          <select [(ngModel)]="sampleA" (ngModelChange)="loadMetrics()">
            <option value="">Select a sample</option>
            <option *ngFor="let sample of samples" [value]="sample.id">{{ sample.name }}</option>
          </select>
        </label>
        <label>
          Compare with
          <select [(ngModel)]="sampleB" (ngModelChange)="loadMetrics()">
            <option value="">None</option>
            <option *ngFor="let sample of samples" [value]="sample.id" [disabled]="sample.id === sampleA">{{ sample.name }}</option>
          </select>
        </label>
        <a class="panel-link" routerLink="/projects">Add a file in Projects</a>
      </div>

      <p class="verdict" *ngIf="verdict" [class.pass]="verdictTone === 'pass'" [class.warn]="verdictTone === 'warn'" [class.fail]="verdictTone === 'fail'">
        {{ verdict }}
      </p>
      <p class="muted" *ngIf="!sampleA">Select a sample to open its charts.</p>
      <p class="muted" *ngIf="sampleA && !loading && !fastqCharts.length && !vcfCharts.length">
        This sample has no completed metrics yet.
      </p>

      <ng-container *ngIf="sampleA && !loading">
        <ng-container *ngIf="fastqCharts.length">
          <h2>Sequencing file</h2>
          <div class="chart-grid">
            <article class="chart-card" *ngFor="let chart of fastqCharts">
              <h3>{{ chart.title }}</h3>
              <strong class="figure">{{ chart.figure }}</strong>
              <div echarts [options]="chart.option" class="mini-chart"></div>
            </article>
          </div>
        </ng-container>

        <ng-container *ngIf="vcfCharts.length">
          <h2>Variants</h2>
          <div class="chart-grid">
            <article class="chart-card" *ngFor="let chart of vcfCharts">
              <h3>{{ chart.title }}</h3>
              <strong class="figure">{{ chart.figure }}</strong>
              <div echarts [options]="chart.option" class="mini-chart"></div>
            </article>
          </div>
        </ng-container>
      </ng-container>

      <h2>Jobs</h2>
      <div class="job-strip" *ngIf="jobs.length; else noJobs">
        <button type="button" class="job" *ngFor="let job of jobs" (click)="selectJob(job)" [class.selected]="job.sampleId === sampleA">
          <strong>{{ sampleName(job.sampleId) }}</strong>
          <p-tag [value]="job.status" [severity]="statusSeverity(job.status)" />
          <span>{{ job.engine }}</span>
        </button>
      </div>
      <ng-template #noJobs>
        <p class="muted">No analysis jobs yet. Upload a FASTQ or VCF from Projects.</p>
      </ng-template>
    </section>
  `,
  styles: [`
    .panel-controls { display: flex; flex-wrap: wrap; gap: 1rem; align-items: end; margin-bottom: 1.5rem; }
    .panel-controls label { display: grid; gap: 0.35rem; min-width: 220px; font-weight: 600; }
    .panel-link { align-self: center; font-weight: 600; }
    h2 { margin-top: 1.5rem; }
    .job-strip { display: flex; gap: 0.75rem; overflow-x: auto; padding-bottom: 0.25rem; }
    .verdict { margin: 0 0 1.25rem; padding: 0.9rem 1rem; background: #fff; border: 1px solid var(--line); border-left: 4px solid #177148; }
    .verdict.pass { border-left-color: #177148; }
    .verdict.warn { border-left-color: #a65a28; }
    .verdict.fail { border-left-color: #a53838; }
    .job {
      display: grid;
      gap: 0.35rem;
      min-width: 180px;
      padding: 0.75rem 0.9rem;
      background: #fff;
      border: 1px solid var(--line);
      text-align: left;
      color: inherit;
      font-weight: 500;
      cursor: pointer;
    }
    .job.selected { border-color: #177148; }
    .job span { color: var(--muted); font-size: 0.8rem; }
    .chart-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1rem; }
    .chart-card { background: #fff; border: 1px solid var(--line); padding: 0.85rem 0.9rem 0.2rem; }
    .chart-card h3 { font-family: var(--font); font-size: 1rem; margin: 0; }
    .figure { display: block; margin-top: 0.2rem; font-size: 1.35rem; }
    .mini-chart { height: 160px; }
  `]
})
export class OverviewComponent implements OnInit, OnDestroy {
  samples: any[] = [];
  jobs: any[] = [];
  sampleA = '';
  sampleB = '';
  loading = false;
  verdict = '';
  verdictTone: 'pass' | 'warn' | 'fail' | 'info' = 'info';
  fastqCharts: PanelChart[] = [];
  vcfCharts: PanelChart[] = [];

  private metricsSub?: Subscription;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    forkJoin({
      samples: this.api.allSamples(),
      jobs: this.api.history()
    }).subscribe(({ samples, jobs }) => {
      this.samples = samples;
      this.jobs = jobs.slice(0, 8);
      const done = jobs.find(job => job.status === 'DONE');
      this.sampleA = done?.sampleId || samples[0]?.id || '';
      this.loadMetrics();
    });
  }

  ngOnDestroy(): void {
    this.metricsSub?.unsubscribe();
  }

  sampleName(id: string): string {
    return this.samples.find(sample => sample.id === id)?.name || 'Sample';
  }

  statusSeverity(status: string): 'success' | 'danger' | 'info' | 'secondary' {
    switch (status) {
      case 'DONE': return 'success';
      case 'FAILED': return 'danger';
      case 'RUNNING': return 'info';
      default: return 'secondary';
    }
  }

  selectJob(job: { sampleId: string }): void {
    this.sampleA = job.sampleId;
    if (this.sampleB === this.sampleA) {
      this.sampleB = '';
    }
    this.loadMetrics();
  }

  loadMetrics(): void {
    this.metricsSub?.unsubscribe();
    this.fastqCharts = [];
    this.vcfCharts = [];
    this.verdict = '';
    if (!this.sampleA) {
      return;
    }
    if (this.sampleB === this.sampleA) {
      this.sampleB = '';
    }
    this.loading = true;
    const calls: Record<string, any> = {
      fastqA: this.api.fastqMetrics(this.sampleA).pipe(catchError(() => of(null))),
      vcfA: this.api.vcfMetrics(this.sampleA).pipe(catchError(() => of(null)))
    };
    if (this.sampleB) {
      calls['fastqB'] = this.api.fastqMetrics(this.sampleB).pipe(catchError(() => of(null)));
      calls['vcfB'] = this.api.vcfMetrics(this.sampleB).pipe(catchError(() => of(null)));
    }
    this.metricsSub = forkJoin(calls).subscribe(result => {
      const nameA = this.sampleName(this.sampleA);
      const nameB = this.sampleB ? this.sampleName(this.sampleB) : '';
      this.verdict = this.buildVerdict(nameA, result['fastqA'], result['vcfA'], nameB);
      this.verdictTone = this.tone(result['fastqA']);
      this.fastqCharts = this.metricCharts(FASTQ_METRICS, result['fastqA'], result['fastqB'], nameA, nameB);
      this.vcfCharts = this.metricCharts(VCF_METRICS, result['vcfA'], result['vcfB'], nameA, nameB);
      const composition = this.compositionChart(result['vcfA'], result['vcfB'], nameA, nameB);
      if (composition) {
        this.vcfCharts.splice(1, 0, composition);
      }
      this.loading = false;
    });
  }

  private metricCharts(defs: NamedMetric[], a: any, b: any, nameA: string, nameB: string): PanelChart[] {
    return defs
      .map(def => this.barChart(def, [
        { name: nameA, value: this.number(a?.[def.key]) },
        { name: nameB, value: nameB ? this.number(b?.[def.key]) : null }
      ]))
      .filter((chart): chart is PanelChart => !!chart);
  }

  private compositionChart(a: any, b: any, nameA: string, nameB: string): PanelChart | null {
    const snpA = this.number(a?.snpCount);
    const indelA = this.number(a?.indelCount);
    if (snpA == null && indelA == null && !nameB) {
      return null;
    }
    const snpB = nameB ? this.number(b?.snpCount) : null;
    const indelB = nameB ? this.number(b?.indelCount) : null;
    if (snpA == null && indelA == null && snpB == null && indelB == null) {
      return null;
    }
    const series = [
      { name: nameA, type: 'bar' as const, data: [snpA, indelA], itemStyle: { color: '#177148' } }
    ];
    if (nameB) {
      series.push({ name: nameB, type: 'bar' as const, data: [snpB, indelB], itemStyle: { color: '#073b2a' } });
    }
    const snpFigure = this.pairFigure(snpA, nameB ? snpB : null);
    const indelFigure = this.pairFigure(indelA, nameB ? indelB : null);
    return {
      title: 'SNPs vs indels',
      unit: 'variants',
      figure: `${snpFigure} SNP · ${indelFigure} indel`,
      option: {
        color: ['#177148', '#073b2a'],
        tooltip: { trigger: 'axis' },
        grid: { left: 8, right: 12, top: 16, bottom: 8, containLabel: true },
        xAxis: { type: 'category', data: ['SNP', 'Indel'] },
        yAxis: { type: 'value' },
        series
      }
    };
  }

  private barChart(def: NamedMetric, points: { name: string; value: number | null }[]): PanelChart | null {
    const present = points.filter(point => point.name && point.value != null);
    if (!present.length) {
      return null;
    }
    const markLine = def.marks?.length
      ? {
          symbol: 'none',
          lineStyle: { color: '#a65a28', type: 'dashed' as const },
          label: { formatter: '{c}' },
          data: def.marks.map(mark => ({ yAxis: mark }))
        }
      : undefined;
    return {
      title: def.title,
      unit: def.unit,
      figure: this.pairFigure(present[0]?.value ?? null, present[1]?.value ?? null) + ' ' + def.unit,
      option: {
        tooltip: { trigger: 'axis' },
        grid: { left: 8, right: 12, top: 16, bottom: 8, containLabel: true },
        xAxis: { type: 'category', data: present.map(point => point.name) },
        yAxis: { type: 'value' },
        series: [{
          type: 'bar',
          markLine,
          data: present.map((point, index) => ({
            value: point.value,
            itemStyle: { color: index === 0 ? '#177148' : '#073b2a' }
          }))
        }]
      }
    };
  }

  private buildVerdict(name: string, fastq: any, vcf: any, comparedWith: string): string {
    const parts: string[] = [];
    if (fastq) {
      const overall = fastq.qc?.overall ? `: ${fastq.qc.overall}` : '';
      parts.push(
        `${name} sequencing file${overall}. Mean quality ${this.format(fastq.meanQuality)} Phred, GC ${this.format(fastq.gcContent)}%, N content ${this.format(fastq.nContent)}%, duplication ${this.format(fastq.duplicationRate)}%.`
      );
    }
    if (vcf) {
      const total = this.number(vcf.variantCount);
      const pass = this.number(vcf.passCount);
      const passPct = total && pass != null ? (pass / total) * 100 : null;
      const ts = this.number(vcf.tsTvRatio);
      const typical = ts != null
        ? ' A germline whole-genome Ts/Tv near 2.0–2.1 is typical.'
        : '';
      parts.push(
        `${name} variant callset. ${this.format(total)} variants, ${this.format(passPct)}% PASS, Ts/Tv ${this.format(ts)}, mean QUAL ${this.format(vcf.meanQual)}, mean depth ${this.format(vcf.meanDp)}×.${typical}`
      );
    }
    if (comparedWith) {
      parts.push(`Compared with ${comparedWith}.`);
    }
    return parts.join(' ');
  }

  private tone(fastq: any): 'pass' | 'warn' | 'fail' | 'info' {
    switch (fastq?.qc?.overall) {
      case 'PASS': return 'pass';
      case 'WARN': return 'warn';
      case 'FAIL': return 'fail';
      default: return 'info';
    }
  }

  private pairFigure(a: number | null, b: number | null): string {
    if (a != null && b != null) {
      return `${this.format(a)} vs ${this.format(b)}`;
    }
    return this.format(a ?? b);
  }

  private format(value: unknown): string {
    const n = this.number(value);
    if (n == null) {
      return '—';
    }
    const digits = Math.abs(n) >= 100 ? 0 : Math.abs(n) >= 10 ? 1 : 2;
    return n.toLocaleString('en-US', { maximumFractionDigits: digits });
  }

  private number(value: unknown): number | null {
    return typeof value === 'number' && Number.isFinite(value) ? value : null;
  }
}
