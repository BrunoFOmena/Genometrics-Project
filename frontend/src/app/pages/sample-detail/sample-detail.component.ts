import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { ProgressBarModule } from 'primeng/progressbar';
import {
  LucideCloudUpload,
  LucideUpload,
  LucideFileText,
  LucideMicroscope,
  LucideArchive,
  LucideChevronDown,
  LucideMaximize2,
  LucideMinimize2
} from '@lucide/angular';
import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { fadeIn, slideUp } from '../../core/animations';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { EChartsOption } from 'echarts';

echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer]);

const BRAND_FONT = { fontFamily: 'IBM Plex Sans, sans-serif' };

type CoverageAssayType = 'WGS' | 'WES' | 'PANEL' | 'UNKNOWN';

type TargetCoveragePoint = {
  depth: number;
  label: string;
  percent: number;
};

const COVERAGE_PROFILES: Record<CoverageAssayType, { target: number; max: number; assayLabel: string }> = {
  WGS: { target: 30, max: 60, assayLabel: 'WGS' },
  WES: { target: 100, max: 200, assayLabel: 'WES' },
  PANEL: { target: 200, max: 400, assayLabel: 'Targeted panel' },
  UNKNOWN: { target: 30, max: 60, assayLabel: 'WES/panel default' }
};

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
    CardModule,
    ButtonModule,
    TagModule,
    ProgressBarModule,
    LucideCloudUpload,
    LucideUpload,
    LucideFileText,
    LucideMicroscope,
    LucideArchive,
    LucideChevronDown,
    LucideMaximize2,
    LucideMinimize2
  ],
  providers: [provideEchartsCore({ echarts })],
  animations: [fadeIn, slideUp],
  template: `
    <section>
      <p-card styleClass="sample-block-card">
        <h1 class="sample-block-title">Sample</h1>

        <div class="sample-file-section">
          <div class="label-row">File</div>
          <div class="sample-upload-actions">
            <input #fileInput class="sample-file-input" type="file" (change)="onFile($event)" />
            <button pButton type="button" severity="secondary" [outlined]="true" (click)="fileInput.click()">
              <svg lucideUpload size="16"></svg>
              Choose file
            </button>
            <span class="muted" *ngIf="selected">{{ selected.name }}</span>
            <button pButton type="button" (click)="upload()" [disabled]="!selected || uploading">
              <svg lucideCloudUpload size="16"></svg>
              Upload
            </button>
          </div>
          <p-progressBar *ngIf="uploading || analysisInProgress" mode="indeterminate" [style]="{ height: '5px', marginTop: '0.75rem' }" />
        </div>

        <div *ngIf="fileAnalysisRows.length" class="files-analyses-section">
          <div class="files-analyses-head collapsible" role="button" tabindex="0"
               (click)="togglePanel('files')" (keydown.enter)="togglePanel('files')"
               [attr.aria-expanded]="isOpen('files')">
            <h2 class="files-analyses-title">Files &amp; analyses</h2>
            <svg lucideChevronDown size="18" class="collapse-chevron" [class.closed]="!isOpen('files')"></svg>
          </div>
          <div class="files-analyses-body" *ngIf="isOpen('files')">
            <ul class="list compact analysis-picker" [class.analysis-picker-focused]="guideOpen">
              <li *ngFor="let row of visibleFileRows"
                  class="file-analysis-row"
                  role="button"
                  tabindex="0"
                  (click)="selectRow(row, $event)"
                  (keydown.enter)="selectRow(row, $event)"
                  [class.selected]="selectedAnalysisId === row.analysis?.id && guideOpen"
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
                <button *ngIf="row.analysis?.status === 'DONE' && selectedFileId === row.file.id && guideOpen"
                        type="button"
                        class="row-expand-btn"
                        pButton
                        severity="secondary"
                        [outlined]="true"
                        size="small"
                        aria-label="Open full screen"
                        (click)="openFullscreen($event)">
                  <svg lucideMaximize2 size="16"></svg>
                </button>
              </li>
            </ul>
          </div>
        </div>
      </p-card>

      <div class="pbi-dashboard pbi-dashboard-fullscreen" *ngIf="fullscreenOpen" @slideUp>
        <div class="pbi-fullscreen-bar">
          <div class="pbi-fullscreen-title">
            <span class="file-guide-label">Dashboard</span>
            <strong>{{ selectedFileLabel }}</strong>
          </div>
          <button type="button" pButton severity="secondary" [outlined]="true" (click)="closeFullscreen()">
            <svg lucideMinimize2 size="16"></svg>
            Exit full screen
          </button>
        </div>


        <p class="muted file-guide-loading" *ngIf="metricsLoading">{{ selectionStatus || 'Loading metrics…' }}</p>
        <p class="muted file-guide-loading" *ngIf="!metricsLoading && selectionStatus && !fastq && !vcf">{{ selectionStatus }}</p>

        <ng-container *ngIf="(fastq || vcf) && !metricsLoading">
          <div class="quality-metrics-panel metric-box metric-box-panel">
            <div class="metric-box-head collapsible" role="button" tabindex="0"
                 (click)="toggleMetric('seq-quality-panel')" (keydown.enter)="toggleMetric('seq-quality-panel')"
                 [attr.aria-expanded]="isMetricOpen('seq-quality-panel')">
              <span class="metric-box-title metric-box-title-lg">Sequencing quality metrics</span>
              <svg lucideChevronDown size="18" class="collapse-chevron" [class.closed]="!isMetricOpen('seq-quality-panel')"></svg>
            </div>
            <div class="metric-box-panel-body" *ngIf="isMetricOpen('seq-quality-panel')">
              <ul class="metric-box-list metric-box-list-nested">
                <li class="metric-box" *ngFor="let field of sequencingQualityFields">
                  <div class="metric-box-head collapsible" role="button" tabindex="0"
                       (click)="toggleMetric(field.key)" (keydown.enter)="toggleMetric(field.key)"
                       [attr.aria-expanded]="isMetricOpen(field.key)">
                    <span class="metric-box-title">{{ field.label }}</span>
                    <svg lucideChevronDown size="18" class="collapse-chevron" [class.closed]="!isMetricOpen(field.key)"></svg>
                  </div>
                  <div class="metric-box-body" *ngIf="isMetricOpen(field.key)">
                    <p class="metric-box-desc">{{ field.description }}</p>
                    <ng-container [ngSwitch]="field.key">
                      <ng-container *ngSwitchCase="'seq-q-mean-depth'">
                        <div class="metric-bullet-wrap" *ngIf="meanCoverageDepth != null; else meanCoveragePending">
                          <div class="metric-kpi-head">
                            <div class="metric-box-kpi" [ngClass]="meanCoverageStatusClass()">
                              {{ meanCoverageDepth | number:'1.1-1' }}<span class="metric-kpi-unit">×</span>
                            </div>
                            <span class="metric-kpi-status" [ngClass]="meanCoverageStatusClass()">{{ meanCoverageStatusLabel() }}</span>
                          </div>
                          <p class="metric-box-sub">{{ meanCoverageMetaLine() }}</p>
                          <div class="metric-bullet-chart-wrap">
                            <div class="metric-bullet-chart" role="img" [attr.aria-label]="meanCoverageAriaLabel()">
                              <div class="metric-bullet-ranges" aria-hidden="true">
                                <span class="metric-bullet-range metric-bullet-range-low"
                                      [style.width.%]="meanCoverageLowBandPercent()"></span>
                                <span class="metric-bullet-range metric-bullet-range-mid"
                                      [style.width.%]="meanCoverageMidBandPercent()"></span>
                                <span class="metric-bullet-range metric-bullet-range-high"
                                      [style.width.%]="meanCoverageHighBandPercent()"></span>
                              </div>
                              <div class="metric-bullet-value" [ngClass]="meanCoverageStatusClass()"
                                   [style.width.%]="meanCoveragePercent()"></div>
                              <div class="metric-bullet-target" [style.left.%]="meanCoverageTargetPercent()"
                                   [attr.title]="'Target ' + meanCoverageTarget + '×'"></div>
                            </div>
                            <div class="metric-bullet-target-label" [style.left.%]="meanCoverageTargetPercent()">
                              {{ meanCoverageTarget }}×
                            </div>
                          </div>
                          <div class="metric-bullet-axis">
                            <span>0×</span>
                            <span>{{ meanCoverageMax }}×</span>
                          </div>
                        </div>
                        <ng-template #meanCoveragePending>
                          <p class="metric-box-empty">Mean coverage depth is not available for this analysis yet.</p>
                        </ng-template>
                      </ng-container>
                      <ng-container *ngSwitchCase="'seq-q-target-thresholds'">
                        <div class="metric-threshold-wrap" *ngIf="targetCoverageThresholds?.length; else targetCoveragePending">
                          <div class="metric-box-chart metric-box-chart-tall">
                            <div echarts [options]="targetCoverageChart" class="pbi-chart pbi-chart-tall"></div>
                          </div>
                        </div>
                        <ng-template #targetCoveragePending>
                          <p class="metric-box-empty">Target coverage thresholds are not available for this analysis yet.</p>
                        </ng-template>
                      </ng-container>
                      <div *ngSwitchDefault class="metric-box-slot" [attr.aria-label]="field.label + ' metrics placeholder'"></div>
                    </ng-container>
                  </div>
                </li>
              </ul>
            </div>
          </div>

          <div class="quality-metrics-panel metric-box metric-box-panel" *ngIf="vcf">
            <div class="metric-box-head collapsible" role="button" tabindex="0"
                 (click)="toggleMetric('vcf-quality-panel')" (keydown.enter)="toggleMetric('vcf-quality-panel')"
                 [attr.aria-expanded]="isMetricOpen('vcf-quality-panel')">
              <span class="metric-box-title metric-box-title-lg">Variant quality metrics</span>
              <svg lucideChevronDown size="18" class="collapse-chevron" [class.closed]="!isMetricOpen('vcf-quality-panel')"></svg>
            </div>
            <div class="metric-box-panel-body" *ngIf="isMetricOpen('vcf-quality-panel')">
              <ul class="metric-box-list metric-box-list-nested">
                <li class="metric-box" *ngFor="let field of vcfQualityFields">
                  <div class="metric-box-head collapsible" role="button" tabindex="0"
                       (click)="toggleMetric(field.key)" (keydown.enter)="toggleMetric(field.key)"
                       [attr.aria-expanded]="isMetricOpen(field.key)">
                    <span class="metric-box-title">{{ field.label }}</span>
                    <svg lucideChevronDown size="18" class="collapse-chevron" [class.closed]="!isMetricOpen(field.key)"></svg>
                  </div>
                  <div class="metric-box-body" *ngIf="isMetricOpen(field.key)">
                    <p class="metric-box-desc">{{ field.description }}</p>
                    <div class="metric-box-slot" [attr.aria-label]="field.label + ' metrics placeholder'"></div>
                  </div>
                </li>
              </ul>
            </div>
          </div>

          <div class="quality-metrics-panel metric-box metric-box-panel" *ngIf="vcf">
            <div class="metric-box-head collapsible" role="button" tabindex="0"
                 (click)="toggleMetric('vcf-composition-panel')" (keydown.enter)="toggleMetric('vcf-composition-panel')"
                 [attr.aria-expanded]="isMetricOpen('vcf-composition-panel')">
              <span class="metric-box-title metric-box-title-lg">VCF composition metrics</span>
              <svg lucideChevronDown size="18" class="collapse-chevron" [class.closed]="!isMetricOpen('vcf-composition-panel')"></svg>
            </div>
            <div class="metric-box-panel-body" *ngIf="isMetricOpen('vcf-composition-panel')">
              <ul class="metric-box-list metric-box-list-nested">
                <li class="metric-box" *ngFor="let field of vcfCompositionFields">
                  <div class="metric-box-head collapsible" role="button" tabindex="0"
                       (click)="toggleMetric(field.key)" (keydown.enter)="toggleMetric(field.key)"
                       [attr.aria-expanded]="isMetricOpen(field.key)">
                    <span class="metric-box-title">{{ field.label }}</span>
                    <svg lucideChevronDown size="18" class="collapse-chevron" [class.closed]="!isMetricOpen(field.key)"></svg>
                  </div>
                  <div class="metric-box-body" *ngIf="isMetricOpen(field.key)">
                    <p class="metric-box-desc">{{ field.description }}</p>
                    <div class="metric-box-slot" [attr.aria-label]="field.label + ' metrics placeholder'"></div>
                  </div>
                </li>
              </ul>
            </div>
          </div>
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
  guideOpen = false;
  selectedFileId: string | null = null;
  selectedFileLabel = '';
  metricsLoading = false;
  fullscreenOpen = false;
  fastq: any;
  vcf: any;
  meanCoverageDepth: number | null = null;
  coverageAssayType: CoverageAssayType = 'UNKNOWN';
  targetCoverageThresholds: TargetCoveragePoint[] | null = null;
  targetCoverageChart: EChartsOption = {};
  readonly sequencingQualityFields = [
    {
      key: 'seq-q-mean-depth',
      label: 'Mean coverage depth',
      description: 'Mean sequencing depth across the target region or genome.'
    },
    {
      key: 'seq-q-target-thresholds',
      label: 'Target coverage thresholds',
      description: 'Percentage of the target meeting each sequencing depth threshold.'
    },
    {
      key: 'seq-q-uniformity',
      label: 'Coverage uniformity',
      description: 'How evenly reads are distributed across targeted regions.'
    },
    {
      key: 'seq-q-duplication',
      label: 'Duplication rate',
      description: 'Fraction of reads that are PCR or optical duplicates.'
    },
    {
      key: 'seq-q-base-quality',
      label: 'Base quality (Q20, Q30)',
      description: 'Percentage of bases meeting Phred quality thresholds Q20 and Q30.'
    },
    {
      key: 'seq-q-alignment',
      label: 'Alignment rate',
      description: 'Percentage of reads that mapped successfully to the reference.'
    },
    {
      key: 'seq-q-clinical',
      label: 'Clinically relevant region coverage',
      description: 'Coverage depth and breadth over regions of clinical interest.'
    },
    {
      key: 'seq-q-on-target',
      label: 'On-target bases (WES / panel)',
      description: 'Percentage of reads or bases falling within the capture target in exome or panel sequencing.'
    }
  ];
  readonly vcfQualityFields = [
    {
      key: 'vcf-q-qual',
      label: 'QUAL',
      description: 'Variant call quality. Higher values usually indicate greater confidence.'
    },
    {
      key: 'vcf-q-filter',
      label: 'FILTER',
      description: 'Whether the variant passed filters (PASS) or which filter failed.'
    },
    {
      key: 'vcf-q-gq',
      label: 'GQ (Genotype Quality)',
      description: 'Confidence in the sample genotype call.'
    },
    {
      key: 'vcf-q-dp',
      label: 'DP (Depth)',
      description: 'Read depth at the variant locus.'
    },
    {
      key: 'vcf-q-ad',
      label: 'AD (Allelic Depth)',
      description: 'Number of reads supporting each allele.'
    },
    {
      key: 'vcf-q-vaf',
      label: 'VAF / AF (Variant Allele Fraction)',
      description: 'Fraction of reads supporting the variant allele.'
    }
  ];
  readonly vcfCompositionFields = [
    {
      key: 'vcf-c-total',
      label: 'Total variant count',
      description: 'Overall number of variants reported in the VCF.'
    },
    {
      key: 'vcf-c-snp-indel',
      label: 'SNPs vs. indels',
      description: 'Breakdown of single-nucleotide variants compared with insertions and deletions.'
    },
    {
      key: 'vcf-c-titv',
      label: 'SNV transitions / transversions (Ti/Tv)',
      description: 'Ratio and counts of transition versus transversion SNVs across the callset.'
    },
    {
      key: 'vcf-c-zygosity',
      label: 'Heterozygous / homozygous',
      description: 'Distribution of heterozygous and homozygous genotypes in the sample.'
    },
    {
      key: 'vcf-c-chromosome',
      label: 'Variants per chromosome',
      description: 'Variant counts or density grouped by chromosome or contig.'
    },
    {
      key: 'vcf-c-pass-filter',
      label: 'PASS vs. FILTER variants',
      description: 'How many variants passed all filters versus failed one or more FILTER flags.'
    },
    {
      key: 'vcf-c-coding',
      label: 'Variants in coding regions',
      description: 'Variants overlapping protein-coding sequence according to the annotation model.'
    },
    {
      key: 'vcf-c-exonic',
      label: 'Exonic / splice-site variants',
      description: 'Variants in exons or canonical splice sites that may affect transcript sequence.'
    },
    {
      key: 'vcf-c-rare',
      label: 'Rare variants after population filtering',
      description: 'Variants remaining after applying population allele-frequency thresholds.'
    }
  ];
  private timer?: ReturnType<typeof setInterval>;
  collapsed: Record<string, boolean> = {};
  metricOpen: Record<string, boolean> = {};

  isOpen(key: string): boolean {
    return !this.collapsed[key];
  }

  togglePanel(key: string): void {
    this.collapsed[key] = !this.collapsed[key];
    if (key === 'files' && !this.isOpen('files')) {
      this.closeGuide();
    }
  }

  toggleMetric(key: string, event?: Event): void {
    event?.stopPropagation();
    this.metricOpen[key] = !this.metricOpen[key];
    if (key === 'seq-q-target-thresholds' && this.metricOpen[key]) {
      setTimeout(() => window.dispatchEvent(new Event('resize')), 50);
    }
  }

  isMetricOpen(key: string): boolean {
    return !!this.metricOpen[key];
  }

  meanCoveragePercent(): number {
    if (this.meanCoverageDepth == null) {
      return 0;
    }
    return Math.min(100, (this.meanCoverageDepth / this.meanCoverageMax) * 100);
  }

  meanCoverageTargetPercent(): number {
    return (this.meanCoverageTarget / this.meanCoverageMax) * 100;
  }

  get meanCoverageTarget(): number {
    return COVERAGE_PROFILES[this.coverageAssayType].target;
  }

  get meanCoverageMax(): number {
    return COVERAGE_PROFILES[this.coverageAssayType].max;
  }

  meanCoverageWarnThreshold(): number {
    return Math.max(10, Math.round(this.meanCoverageTarget * 0.67));
  }

  meanCoverageLowBandPercent(): number {
    return (this.meanCoverageWarnThreshold() / this.meanCoverageMax) * 100;
  }

  meanCoverageMidBandPercent(): number {
    return ((this.meanCoverageTarget - this.meanCoverageWarnThreshold()) / this.meanCoverageMax) * 100;
  }

  meanCoverageHighBandPercent(): number {
    return ((this.meanCoverageMax - this.meanCoverageTarget) / this.meanCoverageMax) * 100;
  }

  meanCoverageStatusLabel(): string {
    if (this.meanCoverageDepth == null) {
      return '';
    }
    return this.meanCoverageDepth >= this.meanCoverageTarget ? 'On target' : 'Below target';
  }

  meanCoverageMetaLine(): string {
    const profile = COVERAGE_PROFILES[this.coverageAssayType];
    return `Target: ${profile.target}× (${profile.assayLabel}) · Scale: 0–${profile.max}×`;
  }

  meanCoverageAriaLabel(): string {
    const depth = this.meanCoverageDepth == null ? 'unknown' : `${this.meanCoverageDepth.toFixed(1)}x`;
    return `Mean coverage ${depth}, ${this.meanCoverageStatusLabel().toLowerCase()}, target ${this.meanCoverageTarget}x`;
  }

  meanCoverageStatusClass(): string {
    if (this.meanCoverageDepth == null) {
      return '';
    }
    if (this.meanCoverageDepth >= this.meanCoverageTarget) {
      return 'kpi-good';
    }
    if (this.meanCoverageDepth >= this.meanCoverageWarnThreshold()) {
      return 'kpi-warn';
    }
    return 'kpi-bad';
  }

  private resolveCoverageAssayType(metrics: any): CoverageAssayType {
    const raw = String(metrics?.coverageAssayType || metrics?.assayType || '').toUpperCase();
    if (raw === 'WGS' || raw === 'WES' || raw === 'PANEL') {
      return raw;
    }
    return 'UNKNOWN';
  }

  get visibleFileRows(): FileAnalysisRow[] {
    if (!this.guideOpen || !this.selectedFileId) {
      return this.fileAnalysisRows;
    }
    return this.fileAnalysisRows.filter(row => row.file.id === this.selectedFileId);
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
    private notify: NotifyService
  ) {}

  ngOnInit(): void {
    this.sampleId = this.route.snapshot.paramMap.get('id') || '';
    this.refresh();
    this.timer = setInterval(() => this.refresh(), 3000);
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
    this.closeFullscreen();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.fullscreenOpen) {
      this.closeFullscreen();
    }
  }

  openFullscreen(event: Event): void {
    event.stopPropagation();
    if (!this.guideOpen || !this.selectedAnalysisId) {
      return;
    }
    this.fullscreenOpen = true;
    document.body.classList.add('pbi-fullscreen-active');
    setTimeout(() => window.dispatchEvent(new Event('resize')), 100);
  }

  closeFullscreen(): void {
    if (!this.fullscreenOpen) {
      return;
    }
    this.fullscreenOpen = false;
    document.body.classList.remove('pbi-fullscreen-active');
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
        this.selectedFileId = null;
        this.guideOpen = false;
        this.selectedFileLabel = '';
        this.fastq = undefined;
        this.vcf = undefined;
        this.meanCoverageDepth = null;
        this.coverageAssayType = 'UNKNOWN';
        this.targetCoverageThresholds = null;
        this.targetCoverageChart = {};
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

  statusSeverity(status: string): 'success' | 'danger' | 'info' | 'secondary' {
    switch (status) {
      case 'DONE': return 'success';
      case 'FAILED': return 'danger';
      case 'RUNNING': return 'info';
      default: return 'secondary';
    }
  }

  selectRow(row: FileAnalysisRow, event: Event): void {
    event.stopPropagation();
    if (!row.analysis) {
      this.closeGuide();
      this.notify.info('No analysis for this file yet.');
      return;
    }
    if (row.analysis.id === this.selectedAnalysisId && this.guideOpen) {
      this.closeGuide();
      return;
    }
    if (row.analysis.status !== 'DONE') {
      this.closeGuide();
      this.notify.info(`Analysis is ${row.analysis.status.toLowerCase()}…`);
      return;
    }
    this.selectedAnalysisId = row.analysis.id;
    this.selectedFileId = row.file.id;
    this.selectedFileLabel = row.file.originalFilename;
    this.selectionStatus = '';
    this.guideOpen = true;
    this.loadMetricsForAnalysis(row.analysis, row.file.fileType);
  }

  closeGuide(): void {
    this.closeFullscreen();
    this.guideOpen = false;
    this.selectedAnalysisId = null;
    this.selectedFileId = null;
    this.selectedFileLabel = '';
    this.metricsLoading = false;
    this.selectionStatus = '';
    this.fastq = undefined;
    this.vcf = undefined;
    this.meanCoverageDepth = null;
    this.coverageAssayType = 'UNKNOWN';
    this.targetCoverageThresholds = null;
    this.targetCoverageChart = {};
    this.metricOpen = {};
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
    if (!this.guideOpen || !this.selectedAnalysisId) {
      return;
    }
    const selected = this.analyses.find(a => a.id === this.selectedAnalysisId);
    if (!selected) {
      return;
    }
    if (selected.status !== 'DONE') {
      this.closeGuide();
      this.notify.info(`Analysis is ${selected.status.toLowerCase()}…`);
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

  private loadMetricsForAnalysis(analysis: NonNullable<FileAnalysisRow['analysis']>, fileType: string): void {
    this.metricsLoading = true;
    this.metricOpen = {};
    this.selectionStatus = '';
    this.fastq = undefined;
    this.vcf = undefined;
    this.meanCoverageDepth = null;
    this.coverageAssayType = 'UNKNOWN';
    this.targetCoverageThresholds = null;
    this.targetCoverageChart = {};
    const type = fileType.toUpperCase();
    if (type.startsWith('FASTQ')) {
      this.api.fastqMetricsByAnalysis(analysis.id).subscribe({
        next: (m) => {
          this.metricsLoading = false;
          this.applyFastq(m);
        },
        error: () => {
          this.metricsLoading = false;
          this.selectionStatus = 'FASTQ metrics not available for this analysis.';
        }
      });
      return;
    }
    if (type.startsWith('VCF')) {
      this.api.vcfMetricsByAnalysis(analysis.id).subscribe({
        next: (m) => {
          this.metricsLoading = false;
          this.applyVcf(m);
        },
        error: () => {
          this.metricsLoading = false;
          this.selectionStatus = 'VCF metrics not available for this analysis.';
        }
      });
      return;
    }
    this.metricsLoading = false;
    this.selectionStatus = 'Reference metadata only — no sequencing metrics for FASTA.';
  }

  private applyFastq(m: any): void {
    this.fastq = m;
    this.meanCoverageDepth = null;
    this.coverageAssayType = this.resolveCoverageAssayType(m);
    this.applyTargetCoverageMetrics(m);
    this.metricOpen['seq-quality-panel'] = true;
  }

  private applyVcf(m: any): void {
    this.vcf = m;
    const meanDp = Number(m.meanDp);
    this.meanCoverageDepth = Number.isFinite(meanDp) ? meanDp : null;
    this.coverageAssayType = this.resolveCoverageAssayType(m);
    this.applyTargetCoverageMetrics(m);
    this.metricOpen['seq-quality-panel'] = true;
    this.metricOpen['vcf-quality-panel'] = true;
    this.metricOpen['vcf-composition-panel'] = true;
  }

  private applyTargetCoverageMetrics(m: any): void {
    this.targetCoverageThresholds = this.parseTargetCoverageThresholds(m, this.meanCoverageDepth);
    this.targetCoverageChart = this.targetCoverageThresholds?.length
      ? this.buildTargetCoverageChart(this.targetCoverageThresholds)
      : {};
  }

  private parseTargetCoverageThresholds(m: any, meanDepth: number | null): TargetCoveragePoint[] | null {
    const parsed = this.parseTargetCoverageRaw(m?.targetCoverageThresholds);
    if (parsed?.length) {
      return parsed;
    }
    if (meanDepth != null) {
      return this.demoTargetCoverageThresholds();
    }
    return null;
  }

  private parseTargetCoverageRaw(raw: unknown): TargetCoveragePoint[] | null {
    if (!raw) {
      return null;
    }
    if (Array.isArray(raw)) {
      const points = raw
        .map((item: any) => {
          const depth = Number(item?.depth ?? item?.threshold);
          const percent = Number(item?.percent ?? item?.pct ?? item?.value);
          if (!Number.isFinite(depth) || !Number.isFinite(percent)) {
            return null;
          }
          return {
            depth,
            label: item?.label || `≥${depth}×`,
            percent
          } as TargetCoveragePoint;
        })
        .filter((point): point is TargetCoveragePoint => point != null);
      return points.length ? points.sort((a, b) => a.depth - b.depth) : null;
    }
    if (typeof raw === 'object') {
      const points = Object.entries(raw as Record<string, unknown>)
        .map(([key, value]) => {
          const depth = Number(String(key).replace(/[^\d.]/g, ''));
          const percent = Number(value);
          if (!Number.isFinite(depth) || !Number.isFinite(percent)) {
            return null;
          }
          return { depth, label: `≥${depth}×`, percent } as TargetCoveragePoint;
        })
        .filter((point): point is TargetCoveragePoint => point != null);
      return points.length ? points.sort((a, b) => a.depth - b.depth) : null;
    }
    return null;
  }

  private demoTargetCoverageThresholds(): TargetCoveragePoint[] {
    return [
      { depth: 10, label: '≥10×', percent: 96.8 },
      { depth: 20, label: '≥20×', percent: 91.4 },
      { depth: 30, label: '≥30×', percent: 82.7 },
      { depth: 50, label: '≥50×', percent: 61.3 }
    ];
  }

  private buildTargetCoverageChart(points: TargetCoveragePoint[]): EChartsOption {
    const labels = points.map(point => point.label);
    const values = points.map(point => point.percent);
    return {
      color: ['#0f6a4d'],
      textStyle: BRAND_FONT,
      grid: { left: 52, right: 20, top: 42, bottom: 40 },
      tooltip: {
        trigger: 'axis',
        valueFormatter: (value) => `${value}%`
      },
      xAxis: {
        type: 'category',
        data: labels,
        axisLabel: { fontSize: 11, color: '#4d6358' },
        axisLine: { lineStyle: { color: '#b7c9be' } }
      },
      yAxis: {
        type: 'value',
        name: '% target covered',
        nameGap: 38,
        min: 0,
        max: 100,
        axisLabel: { formatter: '{value}%', color: '#4d6358' },
        splitLine: { lineStyle: { color: 'rgba(183, 201, 190, 0.45)' } }
      },
      series: [{
        type: 'line',
        data: values,
        symbol: 'circle',
        symbolSize: 10,
        lineStyle: { width: 2.5, color: '#0f6a4d' },
        itemStyle: { color: '#0f6a4d', borderColor: '#fff', borderWidth: 2 },
        label: {
          show: true,
          position: 'top',
          distance: 8,
          formatter: '{c}%',
          fontSize: 11,
          fontWeight: 600,
          color: '#1a2e24'
        }
      }]
    };
  }
}
