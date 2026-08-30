import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import { BarChart, HeatmapChart, LineChart, PieChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  MarkAreaComponent,
  TitleComponent,
  TooltipComponent,
  VisualMapComponent
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { EChartsOption } from 'echarts';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { ProgressBarModule } from 'primeng/progressbar';
import { TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import {
  LucideCloudUpload,
  LucideUpload,
  LucideDownload,
  LucideFileText,
  LucideFileCode,
  LucideMicroscope,
  LucideArchive,
  LucideInfo,
  LucideCircleAlert,
  LucideTriangleAlert,
  LucideCopy,
  LucideExternalLink,
  LucideChevronDown
} from '@lucide/angular';
import { ApiService } from '../../core/api.service';
import { HelpHintComponent } from '../../core/help-hint.component';
import { NotifyService } from '../../core/notify.service';
import { fadeIn, slideUp } from '../../core/animations';

echarts.use([
  BarChart, LineChart, PieChart, HeatmapChart,
  GridComponent, LegendComponent, MarkAreaComponent, TitleComponent, TooltipComponent, VisualMapComponent,
  CanvasRenderer
]);

const BRAND_COLORS = ['#0f6a4d', '#c45c26', '#4d6358', '#74bfa1', '#b7c9be'];
const BRAND_FONT = { fontFamily: 'IBM Plex Sans, sans-serif' };

type FileAnalysisRow = {
  file: { id: string; originalFilename: string; fileType: string; sizeBytes: number };
  analysis: {
    id: string;
    fileAssetId: string;
    mateFileAssetId?: string | null;
    status: string;
    engine: string;
    errorMessage?: string;
    finishedAt?: string;
    createdAt?: string;
  } | null;
};

@Component({
  selector: 'app-sample-detail',
  standalone: true,
  imports: [
    CommonModule,
    NgxEchartsDirective,
    HelpHintComponent,
    CardModule,
    ButtonModule,
    TagModule,
    ProgressBarModule,
    TableModule,
    TabsModule,
    LucideCloudUpload,
    LucideUpload,
    LucideDownload,
    LucideFileText,
    LucideFileCode,
    LucideMicroscope,
    LucideArchive,
    LucideInfo,
    LucideCircleAlert,
    LucideTriangleAlert,
    LucideCopy,
    LucideExternalLink,
    LucideChevronDown
  ],
  providers: [provideEchartsCore({ echarts })],
  animations: [fadeIn, slideUp],
  template: `
    <section>
      <header class="page-head">
        <div>
          <h1 class="head-row">
            Sample
            <app-help-hint text="Upload sequencing files here. FASTQ and VCF are parsed automatically; FASTA is stored as reference metadata." />
          </h1>
          <p>Upload FASTQ/VCF/FASTA and inspect metrics.</p>
        </div>
      </header>

      <p-card styleClass="sample-upload-card">
        <div class="label-row">
          File
          <app-help-hint text="Supported: .fastq/.fq, .vcf, .fasta (+ .gz). Max 2 GB per file. Upload _R1 and _R2 files to the same sample for paired-end." />
        </div>
        <div class="sample-upload-actions">
          <input #fileInput class="sample-file-input" type="file" (change)="onFile($event)" />
          <button pButton type="button" severity="secondary" [outlined]="true" (click)="fileInput.click()">
            <svg lucideUpload size="16"></svg>
            Choose file
          </button>
          <span class="muted" *ngIf="selected">{{ selected.name }}</span>
          <button pButton type="button" (click)="upload()" [disabled]="!selected || uploading">
            <svg lucideCloudUpload size="16"></svg>
            Upload &amp; analyze
          </button>
        </div>
        <p-progressBar *ngIf="uploading || analysisInProgress" mode="indeterminate" [style]="{ height: '5px', marginTop: '0.75rem' }" />
      </p-card>

      <div *ngIf="fileAnalysisRows.length" class="file-analysis-section">
        <div class="section-head collapsible" role="button" tabindex="0"
             (click)="togglePanel('files')" (keydown.enter)="togglePanel('files')"
             [attr.aria-expanded]="isOpen('files')">
          <svg lucideChevronDown size="18" class="collapse-chevron" [class.closed]="!isOpen('files')"></svg>
          <h2>Files &amp; analyses</h2>
          <app-help-hint text="Click a row to view metrics for that file's analysis. Paired-end R2 files link to the joint R1+R2 analysis." />
        </div>
        <ng-container *ngIf="isOpen('files')">
          <ul class="list compact analysis-picker">
            <li *ngFor="let row of fileAnalysisRows"
                class="file-analysis-row"
                role="button"
                tabindex="0"
                (click)="selectRow(row)"
                (keydown.enter)="selectRow(row)"
                [class.selected]="selectedAnalysisId === row.analysis?.id"
                [class.pending]="row.analysis && row.analysis.status !== 'DONE'">
              <span class="row-file label-row" [ngSwitch]="fileIcon(row.file.fileType)">
                <svg *ngSwitchCase="'fastq'" lucideFileText size="17" class="row-icon"></svg>
                <svg *ngSwitchCase="'vcf'" lucideMicroscope size="17" class="row-icon"></svg>
                <svg *ngSwitchDefault lucideArchive size="17" class="row-icon"></svg>
                {{ row.file.originalFilename }} · {{ row.file.fileType }} · {{ row.file.sizeBytes }} B
              </span>
              <span class="row-status" *ngIf="row.analysis">
                <p-tag [value]="row.analysis.status" [severity]="statusSeverity(row.analysis.status)" />
                {{ row.analysis.engine }}
                <span *ngIf="row.analysis.errorMessage" class="error"> — {{ row.analysis.errorMessage }}</span>
              </span>
              <span class="row-status muted" *ngIf="!row.analysis">No analysis yet</span>
            </li>
          </ul>
          <p class="muted selection-hint" *ngIf="selectionStatus">{{ selectionStatus }}</p>
        </ng-container>
      </div>

      <div class="metrics-actions" *ngIf="sampleId">
        <button pButton type="button" severity="secondary" [outlined]="true" (click)="download('csv')">
          <svg lucideDownload size="16"></svg>
          Download CSV
        </button>
        <button pButton type="button" severity="secondary" [outlined]="true" (click)="download('pdf')">
          <svg lucideFileText size="16"></svg>
          Download PDF
        </button>
      </div>

      <div class="metrics metrics-panel" *ngIf="fastq" @slideUp>
        <div class="section-head collapsible" role="button" tabindex="0"
             (click)="togglePanel('fastq')" (keydown.enter)="togglePanel('fastq')"
             [attr.aria-expanded]="isOpen('fastq')">
          <svg lucideChevronDown size="18" class="collapse-chevron" [class.closed]="!isOpen('fastq')"></svg>
          <h2>Sequencing (FASTQ)</h2>
          <p-tag *ngIf="fastq.pairedEnd" value="Paired-end" severity="info" />
          <p-tag [value]="'Overall QC: ' + (fastq.qc?.overall || 'PASS')" [severity]="qcSeverity()" />
          <app-help-hint text="Quality metrics from raw reads with FastQC-style pass/warn/fail flags." />
        </div>

        <ng-container *ngIf="isOpen('fastq')">
        <div class="stat-chips">
          <span class="stat-chip" [ngClass]="statClass('readCount')">
            Reads {{ fastq.readCount }}
            <span *ngIf="fastq.pairedEnd"> (R1 {{ fastq.readCountR1 }} / R2 {{ fastq.readCountR2 }})</span>
          </span>
          <span class="stat-chip" [ngClass]="statClass('gcContent')">GC {{ fastq.gcContent | number:'1.1-1' }}%</span>
          <span class="stat-chip" [ngClass]="statClass('meanQuality')">Mean Q {{ fastq.meanQuality | number:'1.1-1' }}</span>
          <span class="stat-chip">Avg len {{ fastq.avgLength | number:'1.0-0' }}</span>
        </div>

        <div class="qc-recommendations" *ngIf="fastq.recommendations?.length">
          <div class="section-head collapsible" role="button" tabindex="0"
               (click)="togglePanel('recs'); $event.stopPropagation()"
               (keydown.enter)="togglePanel('recs'); $event.stopPropagation()"
               [attr.aria-expanded]="isOpen('recs')">
            <svg lucideChevronDown size="18" class="collapse-chevron" [class.closed]="!isOpen('recs')"></svg>
            <h3>Suggested actions</h3>
          </div>
          <ul class="rec-items" *ngIf="isOpen('recs')">
            <li *ngFor="let r of fastq.recommendations" [ngSwitch]="recIcon(r.severity)">
              <svg *ngSwitchCase="'fail'" lucideCircleAlert size="18" class="rec-icon rec-icon-fail"></svg>
              <svg *ngSwitchCase="'warn'" lucideTriangleAlert size="18" class="rec-icon rec-icon-warn"></svg>
              <svg *ngSwitchDefault lucideInfo size="18" class="rec-icon rec-icon-info"></svg>
              <span class="rec-body">
                <strong>{{ r.title }}</strong>
                <span class="muted">{{ r.detail }}</span>
              </span>
            </li>
          </ul>
        </div>

        <div class="metrics-actions">
          <button pButton type="button" severity="secondary" [outlined]="true" (click)="downloadFastq('html')">
            <svg lucideFileCode size="16"></svg>
            FASTQ HTML report
          </button>
          <button pButton type="button" severity="secondary" [outlined]="true" (click)="downloadFastq('pdf')">
            <svg lucideFileText size="16"></svg>
            FASTQ PDF report
          </button>
        </div>

        <p-tabs [(value)]="fastqTabIndex">
          <p-tablist>
            <p-tab [value]="0">Charts</p-tab>
            <p-tab [value]="1">Detailed stats</p-tab>
            <p-tab [value]="2">Overrepresented</p-tab>
          </p-tablist>
          <p-tabpanels>
            <p-tabpanel [value]="0">
              <div class="fastq-tab-content" @fadeIn>
                <div echarts [options]="gcChart" class="chart"></div>
                <div echarts [options]="lengthChart" class="chart"></div>
                <div echarts [options]="qualityChart" class="chart"></div>
                <div echarts [options]="heatmapChart" class="chart" *ngIf="hasHeatmap"></div>
              </div>
            </p-tabpanel>
            <p-tabpanel [value]="1">
              <div class="fastq-tab-content detail-stats" @fadeIn>
                <dl class="detail-grid">
                  <dt>Duplication rate</dt>
                  <dd [ngClass]="statClass('duplicationRate')">{{ fastq.duplicationRate | number:'1.1-1' }}%</dd>
                  <dt>AT content</dt>
                  <dd>{{ fastq.atContent | number:'1.1-1' }}%</dd>
                  <dt>N content</dt>
                  <dd [ngClass]="statClass('nContent')">{{ fastq.nContent | number:'1.2-2' }}%</dd>
                  <dt>Min length</dt>
                  <dd>{{ fastq.minLength }}</dd>
                  <dt>Max length</dt>
                  <dd>{{ fastq.maxLength }}</dd>
                  <dt>Phred encoding</dt>
                  <dd>{{ fastq.phredSummary?.encoding || 'Phred+33' }}</dd>
                  <dt>Phred mean</dt>
                  <dd>{{ fastq.phredSummary?.mean | number:'1.1-1' }}</dd>
                </dl>
                <div class="adapter-panel" *ngIf="adapterRows.length">
                  <h3>Adapters</h3>
                  <p class="muted">Hits in {{ fastq.adapterHits?.fraction | number:'1.1-1' }}% of sampled reads.</p>
                  <p-table [value]="adapterRows" styleClass="overrep-table">
                    <ng-template pTemplate="header">
                      <tr>
                        <th>Adapter</th>
                        <th>Count</th>
                      </tr>
                    </ng-template>
                    <ng-template pTemplate="body" let-row>
                      <tr>
                        <td>{{ row.name }}</td>
                        <td>{{ row.count }}</td>
                      </tr>
                    </ng-template>
                  </p-table>
                </div>
                <ul class="qc-check-list" *ngIf="fastq.qc?.checks?.length">
                  <li *ngFor="let c of fastq.qc.checks" [ngClass]="'qc-' + (c.status | lowercase)">
                    {{ c.message }}
                  </li>
                </ul>
              </div>
            </p-tabpanel>
            <p-tabpanel [value]="2">
              <div class="fastq-tab-content" @fadeIn>
                <p *ngIf="!overrepresentedRows.length" class="muted">No overrepresented sequences detected.</p>
                <p-table [value]="overrepresentedRows" styleClass="overrep-table" *ngIf="overrepresentedRows.length">
                  <ng-template pTemplate="header">
                    <tr>
                      <th>Sequence</th>
                      <th>Count</th>
                      <th>Actions</th>
                    </tr>
                  </ng-template>
                  <ng-template pTemplate="body" let-row>
                    <tr>
                      <td class="seq-cell" [title]="row.sequence">{{ truncate(row.sequence) }}</td>
                      <td>{{ row.count }}</td>
                      <td>
                        <span class="action-cell">
                          <button pButton type="button" severity="secondary" [outlined]="true" size="small" (click)="copySequence(row.sequence)">
                            <svg lucideCopy size="14"></svg>
                            Copy
                          </button>
                          <a class="btn-link" [href]="blastUrl" target="_blank" rel="noopener">
                            BLAST
                            <svg lucideExternalLink size="13"></svg>
                          </a>
                        </span>
                      </td>
                    </tr>
                  </ng-template>
                </p-table>
              </div>
            </p-tabpanel>
          </p-tabpanels>
        </p-tabs>
        </ng-container>
      </div>

      <div class="metrics metrics-panel" *ngIf="vcf" @slideUp>
        <div class="section-head collapsible" role="button" tabindex="0"
             (click)="togglePanel('vcf')" (keydown.enter)="togglePanel('vcf')"
             [attr.aria-expanded]="isOpen('vcf')">
          <svg lucideChevronDown size="18" class="collapse-chevron" [class.closed]="!isOpen('vcf')"></svg>
          <h2>Variants (VCF)</h2>
            <app-help-hint text="Variant calling summary: type and chromosome counts, filter status, indel length, Ts/Tv by chromosome, QUAL/DP histograms, and VCF header provenance." />
        </div>
        <ng-container *ngIf="isOpen('vcf')">
        <div class="stat-chips">
          <span class="stat-chip">Variants {{ vcf.variantCount }}</span>
          <span class="stat-chip">SNPs {{ vcf.snpCount }}</span>
          <span class="stat-chip">INDELs {{ vcf.indelCount }}</span>
          <span class="stat-chip">Ts/Tv {{ vcf.tsTvRatio | number:'1.2-2' }}</span>
        </div>
        <div class="vcf-header-meta" *ngIf="vcf.header">
          <div class="section-head collapsible" role="button" tabindex="0"
               (click)="togglePanel('vcfHeader'); $event.stopPropagation()"
               (keydown.enter)="togglePanel('vcfHeader'); $event.stopPropagation()"
               [attr.aria-expanded]="isOpen('vcfHeader')">
            <svg lucideChevronDown size="18" class="collapse-chevron" [class.closed]="!isOpen('vcfHeader')"></svg>
            <h3>File provenance</h3>
            <app-help-hint text="Metadata from the VCF header: file format, reference, caller source, contig/INFO/FORMAT tags, and sample column names. Use this to confirm the file matches the expected genome build and pipeline." />
          </div>
          <div class="stat-chips" *ngIf="isOpen('vcfHeader')">
            <span class="stat-chip" *ngIf="vcf.header.fileformat">{{ vcf.header.fileformat }}</span>
            <span class="stat-chip" *ngIf="vcf.header.reference">Ref {{ vcf.header.reference }}</span>
            <span class="stat-chip" *ngIf="vcf.header.source">{{ vcf.header.source }}</span>
            <span class="stat-chip" *ngIf="vcf.header.samples?.length">Samples {{ vcf.header.samples.join(', ') }}</span>
            <span class="stat-chip" *ngIf="vcf.header.contigCount">Contigs {{ vcf.header.contigCount }}</span>
            <span class="stat-chip" *ngIf="vcf.header.infoCount">INFO {{ vcf.header.infoCount }}</span>
            <span class="stat-chip" *ngIf="vcf.header.formatCount">FORMAT {{ vcf.header.formatCount }}</span>
          </div>
        </div>
        <div echarts [options]="chromChart" class="chart"></div>
        <div echarts [options]="typeChart" class="chart"></div>
        <div echarts [options]="filterChart" class="chart"></div>
        <div echarts [options]="indelLengthChart" class="chart" *ngIf="indelLengthChart.series"></div>
        <div echarts [options]="tsTvChromChart" class="chart" *ngIf="tsTvChromChart.series"></div>
        <div echarts [options]="qualHistChart" class="chart" *ngIf="qualHistChart.series"></div>
        <div echarts [options]="dpHistChart" class="chart" *ngIf="dpHistChart.series"></div>
        </ng-container>
      </div>
    </section>
  `
})
export class SampleDetailComponent implements OnInit, OnDestroy {
  sampleId = '';
  selected: File | null = null;
  uploading = false;
  files: any[] = [];
  analyses: any[] = [];
  fileAnalysisRows: FileAnalysisRow[] = [];
  selectedAnalysisId: string | null = null;
  selectionStatus = '';
  fastq: any;
  vcf: any;
  fastqTabIndex = 0;
  overrepresentedRows: { sequence: string; count: number }[] = [];
  adapterRows: { name: string; count: number }[] = [];
  hasHeatmap = false;
  readonly blastUrl = 'https://blast.ncbi.nlm.nih.gov/Blast.cgi';
  gcChart: EChartsOption = {};
  lengthChart: EChartsOption = {};
  qualityChart: EChartsOption = {};
  heatmapChart: EChartsOption = {};
  chromChart: EChartsOption = {};
  typeChart: EChartsOption = {};
  filterChart: EChartsOption = {};
  indelLengthChart: EChartsOption = {};
  tsTvChromChart: EChartsOption = {};
  qualHistChart: EChartsOption = {};
  dpHistChart: EChartsOption = {};
  private timer?: ReturnType<typeof setInterval>;
  collapsed: Record<string, boolean> = {};

  isOpen(key: string): boolean {
    return !this.collapsed[key];
  }

  togglePanel(key: string): void {
    this.collapsed[key] = !this.collapsed[key];
  }

  get analysisInProgress(): boolean {
    if (this.uploading) return true;
    const selected = this.analyses.find(a => a.id === this.selectedAnalysisId);
    if (selected && (selected.status === 'QUEUED' || selected.status === 'RUNNING')) {
      return true;
    }
    return this.analyses.some(a => a.status === 'QUEUED' || a.status === 'RUNNING');
  }

  constructor(
    private route: ActivatedRoute,
    private api: ApiService,
    private http: HttpClient,
    private notify: NotifyService
  ) {}

  ngOnInit(): void {
    this.sampleId = this.route.snapshot.paramMap.get('id') || '';
    this.refresh();
    this.timer = setInterval(() => this.refresh(), 3000);
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selected = input.files?.[0] || null;
  }

  upload(): void {
    if (!this.selected) return;
    this.uploading = true;
    this.api.upload(this.sampleId, this.selected).subscribe({
      next: () => {
        this.uploading = false;
        this.notify.success('File uploaded — analysis queued');
        this.selected = null;
        this.selectedAnalysisId = null;
        this.fastq = undefined;
        this.vcf = undefined;
        this.refresh();
      },
      error: (err) => {
        this.uploading = false;
        this.notify.error(err?.error?.message || 'Upload failed');
      }
    });
  }

  fileIcon(fileType: string): string {
    const t = fileType.toUpperCase();
    if (t.startsWith('FASTQ')) return 'fastq';
    if (t.startsWith('VCF')) return 'vcf';
    return 'other';
  }

  recIcon(severity: string): string {
    const s = (severity || '').toUpperCase();
    if (s === 'FAIL') return 'fail';
    if (s === 'WARN') return 'warn';
    return 'info';
  }

  qcSeverity(): 'success' | 'warn' | 'danger' {
    const overall = (this.fastq?.qc?.overall || 'PASS').toUpperCase();
    if (overall === 'FAIL') return 'danger';
    if (overall === 'WARN') return 'warn';
    return 'success';
  }

  statusSeverity(status: string): 'success' | 'danger' | 'info' | 'secondary' {
    switch (status) {
      case 'DONE': return 'success';
      case 'FAILED': return 'danger';
      case 'RUNNING': return 'info';
      default: return 'secondary';
    }
  }

  statClass(metricId: string): string {
    const check = (this.fastq?.qc?.checks || []).find((c: any) => c.id === metricId);
    if (!check) return '';
    return 'qc-' + String(check.status).toLowerCase();
  }

  truncate(seq: string, max = 48): string {
    return seq.length <= max ? seq : seq.slice(0, max) + '…';
  }

  copySequence(seq: string): void {
    navigator.clipboard?.writeText(seq).catch(() => {});
    this.notify.info('Sequence copied to clipboard');
  }

  selectRow(row: FileAnalysisRow): void {
    if (!row.analysis) {
      this.selectedAnalysisId = null;
      this.fastq = undefined;
      this.vcf = undefined;
      this.selectionStatus = 'No analysis for this file yet.';
      return;
    }
    this.selectedAnalysisId = row.analysis.id;
    this.selectionStatus = '';
    if (row.analysis.status !== 'DONE') {
      this.fastq = undefined;
      this.vcf = undefined;
      this.selectionStatus = `Analysis is ${row.analysis.status.toLowerCase()}…`;
      return;
    }
    this.loadMetricsForAnalysis(row.analysis, row.file.fileType);
    document.querySelector('.metrics-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  private refresh(): void {
    this.api.files(this.sampleId).subscribe(files => {
      this.files = files;
      this.syncRowsAndMetrics();
    });
    this.api.analyses(this.sampleId).subscribe(analyses => {
      this.analyses = analyses;
      this.syncRowsAndMetrics();
    });
  }

  private syncRowsAndMetrics(): void {
    if (!this.files.length) {
      this.fileAnalysisRows = [];
      return;
    }
    this.fileAnalysisRows = this.files.map(file => ({
      file,
      analysis: this.findAnalysisForFile(file.id)
    }));
    if (!this.selectedAnalysisId) {
      const auto = this.pickDefaultAnalysis();
      if (auto) {
        this.selectedAnalysisId = auto.id;
        const row = this.fileAnalysisRows.find(r => r.analysis?.id === auto.id);
        if (row && auto.status === 'DONE') {
          this.loadMetricsForAnalysis(auto, row.file.fileType);
        }
      }
      return;
    }
    const selected = this.analyses.find(a => a.id === this.selectedAnalysisId);
    const row = this.fileAnalysisRows.find(r => r.analysis?.id === this.selectedAnalysisId);
    if (selected && row) {
      if (selected.status === 'DONE') {
        this.loadMetricsForAnalysis(selected, row.file.fileType);
      } else {
        this.fastq = undefined;
        this.vcf = undefined;
        this.selectionStatus = `Analysis is ${selected.status.toLowerCase()}…`;
      }
    }
  }

  private findAnalysisForFile(fileId: string): FileAnalysisRow['analysis'] {
    const matches = this.analyses.filter(a =>
      a.fileAssetId === fileId || a.mateFileAssetId === fileId);
    if (!matches.length) {
      return null;
    }
    return matches.sort((a, b) =>
      this.analysisSortKey(b) - this.analysisSortKey(a))[0];
  }

  private analysisSortKey(a: { finishedAt?: string; createdAt?: string }): number {
    const ts = a.finishedAt || a.createdAt;
    return ts ? new Date(ts).getTime() : 0;
  }

  private pickDefaultAnalysis(): FileAnalysisRow['analysis'] {
    const done = this.analyses
      .filter(a => a.status === 'DONE')
      .sort((a, b) => this.analysisSortKey(b) - this.analysisSortKey(a));
    return done[0] ?? null;
  }

  private loadMetricsForAnalysis(analysis: NonNullable<FileAnalysisRow['analysis']>, fileType: string): void {
    this.selectionStatus = '';
    const type = fileType.toUpperCase();
    if (type.startsWith('FASTQ')) {
      this.vcf = undefined;
      this.api.fastqMetricsByAnalysis(analysis.id).subscribe({
        next: (m) => this.applyFastq(m),
        error: () => {
          this.fastq = undefined;
          this.selectionStatus = 'FASTQ metrics not available for this analysis.';
        }
      });
      return;
    }
    if (type.startsWith('VCF')) {
      this.fastq = undefined;
      this.api.vcfMetricsByAnalysis(analysis.id).subscribe({
        next: (m) => this.applyVcf(m),
        error: () => {
          this.vcf = undefined;
          this.selectionStatus = 'VCF metrics not available for this analysis.';
        }
      });
      return;
    }
    this.fastq = undefined;
    this.vcf = undefined;
    this.selectionStatus = 'Reference metadata only — no sequencing metrics for FASTA.';
  }

  private applyFastq(m: any): void {
    this.fastq = m;
    this.overrepresentedRows = Array.isArray(m.overrepresented) ? m.overrepresented : [];
    this.adapterRows = Array.isArray(m.adapterHits?.adapters) ? m.adapterHits.adapters : [];
    this.gcChart = {
      color: BRAND_COLORS,
      textStyle: BRAND_FONT,
      title: { text: 'Base composition' },
      tooltip: {},
      series: [{
        type: 'pie',
        radius: '60%',
        data: Object.entries(m.baseComposition || {}).map(([name, value]) => ({
          name,
          value: Number(value)
        }))
      }]
    };
    const lengths = m.lengthDistribution || {};
    this.lengthChart = {
      color: BRAND_COLORS,
      textStyle: BRAND_FONT,
      title: { text: 'Read length distribution' },
      xAxis: { type: 'category', data: Object.keys(lengths) },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: Object.values(lengths).map(v => Number(v)), itemStyle: { borderRadius: [4, 4, 0, 0] } }]
    };
    const pq = m.perPositionQuality || {};
    this.qualityChart = {
      color: BRAND_COLORS,
      textStyle: BRAND_FONT,
      title: { text: 'Quality by position' },
      tooltip: {},
      xAxis: { type: 'category', data: Object.keys(pq) },
      yAxis: { type: 'value', min: 0, max: 41 },
      series: [{
        type: 'line',
        data: Object.values(pq).map(v => Number(v)),
        areaStyle: {},
        markArea: {
          silent: true,
          data: [
            [{ yAxis: 0, itemStyle: { color: 'rgba(155, 44, 44, 0.12)' } }, { yAxis: 20 }],
            [{ yAxis: 20, itemStyle: { color: 'rgba(196, 92, 38, 0.12)' } }, { yAxis: 28 }],
            [{ yAxis: 28, itemStyle: { color: 'rgba(15, 106, 77, 0.12)' } }, { yAxis: 41 }]
          ]
        }
      }]
    };
    const hm = m.qualityHeatmap;
    const hmData = Array.isArray(hm?.data) ? hm.data : [];
    this.hasHeatmap = hmData.length > 0;
    if (this.hasHeatmap) {
      const maxCount = hmData.reduce((max: number, row: number[]) => Math.max(max, Number(row[2])), 1);
      this.heatmapChart = {
        textStyle: BRAND_FONT,
        title: { text: 'Quality heatmap' },
        tooltip: { position: 'top' },
        grid: { height: '60%', top: '12%' },
        xAxis: { type: 'category', name: 'Position', data: Array.from({ length: hm.maxPosition || 1 }, (_, i) => i + 1) },
        yAxis: { type: 'category', name: 'Q bin', data: Array.from({ length: hm.bins || 41 }, (_, i) => String(i)) },
        visualMap: {
          min: 0,
          max: maxCount,
          calculable: true,
          orient: 'horizontal',
          left: 'center',
          bottom: '2%',
          inRange: { color: ['#eef3f0', '#74bfa1', '#0f6a4d'] }
        },
        series: [{
          type: 'heatmap',
          data: hmData.map((row: number[]) => [row[0] - 1, row[1], row[2]]),
          emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.5)' } }
        }]
      };
    } else {
      this.heatmapChart = {};
    }
  }

  private applyVcf(m: any): void {
    this.vcf = m;
    const chrom = m.chromosomeDistribution || {};
    this.chromChart = {
      color: BRAND_COLORS,
      textStyle: BRAND_FONT,
      title: { text: 'Variants by chromosome' },
      xAxis: { type: 'category', data: Object.keys(chrom) },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: Object.values(chrom).map(v => Number(v)), itemStyle: { borderRadius: [4, 4, 0, 0] } }]
    };
    this.typeChart = {
      color: BRAND_COLORS,
      textStyle: BRAND_FONT,
      title: { text: 'Variant types' },
      tooltip: {},
      series: [{
        type: 'pie',
        radius: ['35%', '65%'],
        data: [
          { name: 'SNP', value: Number(m.snpCount) },
          { name: 'INDEL', value: Number(m.indelCount) },
          { name: 'MNP', value: Number(m.mnpCount) }
        ]
      }]
    };
    const filters = m.filterDistribution || {};
    this.filterChart = {
      color: BRAND_COLORS,
      textStyle: BRAND_FONT,
      title: { text: 'Filter distribution' },
      xAxis: { type: 'category', data: Object.keys(filters) },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: Object.values(filters).map(v => Number(v)), itemStyle: { borderRadius: [4, 4, 0, 0] } }]
    };
    this.indelLengthChart = this.histogramChart('Indel length (ALT − REF)', m.indelLengthDistribution, true);
    this.qualHistChart = this.histogramChart('QUAL distribution', m.qualHistogram, true);
    this.dpHistChart = this.histogramChart('DP distribution', m.dpHistogram, true);
    const tsTv = m.tsTvByChromosome || {};
    const chroms = Object.keys(tsTv);
    this.tsTvChromChart = chroms.length ? {
      color: BRAND_COLORS,
      textStyle: BRAND_FONT,
      title: { text: 'Ts/Tv by chromosome' },
      tooltip: { trigger: 'axis' },
      legend: { data: ['Transitions', 'Transversions'] },
      xAxis: { type: 'category', data: chroms },
      yAxis: { type: 'value' },
      series: [
        { name: 'Transitions', type: 'bar', data: chroms.map(c => Number(tsTv[c]?.ts || 0)), itemStyle: { borderRadius: [4, 4, 0, 0] } },
        { name: 'Transversions', type: 'bar', data: chroms.map(c => Number(tsTv[c]?.tv || 0)), itemStyle: { borderRadius: [4, 4, 0, 0] } }
      ]
    } : {};
  }

  private histogramChart(title: string, hist: { labels?: string[]; counts?: number[] } | undefined, rotate: boolean): EChartsOption {
    const labels = hist?.labels || [];
    const counts = hist?.counts || [];
    if (!labels.length) {
      return {};
    }
    return {
      color: BRAND_COLORS,
      textStyle: BRAND_FONT,
      title: { text: title },
      tooltip: {},
      xAxis: { type: 'category', data: labels, axisLabel: rotate ? { rotate: 45, interval: 4 } : {} },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: counts.map(v => Number(v)), itemStyle: { borderRadius: [4, 4, 0, 0] } }]
    };
  }

  download(kind: 'csv' | 'pdf'): void {
    const url = kind === 'csv'
      ? this.api.reportCsvUrl(this.sampleId)
      : this.api.reportPdfUrl(this.sampleId);
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `report-${this.sampleId}.${kind}`;
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: () => this.notify.error('Report not available yet')
    });
  }

  downloadFastq(kind: 'html' | 'pdf'): void {
    const url = kind === 'html'
      ? this.api.fastqReportHtmlUrl(this.sampleId)
      : this.api.fastqReportPdfUrl(this.sampleId);
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `fastq-${this.sampleId}.${kind}`;
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: () => this.notify.error('FASTQ report not available yet')
    });
  }
}
