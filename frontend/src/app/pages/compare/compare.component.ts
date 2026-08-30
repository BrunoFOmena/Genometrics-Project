import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import { BarChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { EChartsOption } from 'echarts';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { ApiService } from '../../core/api.service';
import { HelpHintComponent } from '../../core/help-hint.component';

echarts.use([BarChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

@Component({
  selector: 'app-compare',
  standalone: true,
  imports: [CommonModule, FormsModule, NgxEchartsDirective, HelpHintComponent, CardModule, ButtonModule, SelectModule],
  providers: [provideEchartsCore({ echarts })],
  template: `
    <section>
      <header class="page-head">
        <div>
          <h1 class="head-row">
            Compare samples
            <app-help-hint text="Side-by-side comparison of headline FASTQ and VCF metrics for two samples that both have completed analyses." />
          </h1>
          <p>Side-by-side FASTQ and VCF headline metrics.</p>
        </div>
      </header>

      <p-card styleClass="form-card">
        <form class="inline-form" (ngSubmit)="run()">
          <label>
            <span class="label-row">
              Sample A
              <app-help-hint text="First sample in the comparison (baseline or control)." />
            </span>
            <p-select [(ngModel)]="a" name="a" [options]="samples" optionLabel="name" optionValue="id" placeholder="Sample A" required />
          </label>
          <label>
            <span class="label-row">
              Sample B
              <app-help-hint text="Second sample to compare against Sample A." />
            </span>
            <p-select [(ngModel)]="b" name="b" [options]="samples" optionLabel="name" optionValue="id" placeholder="Sample B" required />
          </label>
          <label>
            <span class="label-row">
              Compare
              <app-help-hint text="Loads metrics for both samples and renders a bar chart plus per-metric cards." />
            </span>
            <button pButton type="submit" label="Compare"></button>
          </label>
        </form>
      </p-card>

      <p class="error" *ngIf="error">{{ error }}</p>

      <div class="section-head" *ngIf="chart">
        <h2>Comparison chart</h2>
        <app-help-hint text="Grouped bars show FASTQ (GC, reads, quality, length) and VCF (variants, SNPs, Ts/Tv) metrics for each sample." />
      </div>
      <div echarts *ngIf="chart" [options]="chart" class="chart tall"></div>

      <ng-container *ngIf="result">
        <div class="section-head">
          <h2>Metric breakdown</h2>
          <app-help-hint text="Exact values per metric for both samples. A is the first sample, B the second." />
        </div>
        <div class="compare-grid">
          <p-card *ngFor="let m of result.fastq | keyvalue" styleClass="metric-card">
            <span class="metric-name">FASTQ · {{ m.key }}</span>
            <div class="metric-values">
              <div class="metric-side">
                <span class="muted">{{ result.sampleA.name }}</span>
                <strong>{{ $any(m.value).a }}</strong>
              </div>
              <div class="metric-side">
                <span class="muted">{{ result.sampleB.name }}</span>
                <strong>{{ $any(m.value).b }}</strong>
              </div>
            </div>
          </p-card>
          <p-card *ngFor="let m of result.vcf | keyvalue" styleClass="metric-card">
            <span class="metric-name">VCF · {{ m.key }}</span>
            <div class="metric-values">
              <div class="metric-side">
                <span class="muted">{{ result.sampleA.name }}</span>
                <strong>{{ $any(m.value).a }}</strong>
              </div>
              <div class="metric-side">
                <span class="muted">{{ result.sampleB.name }}</span>
                <strong>{{ $any(m.value).b }}</strong>
              </div>
            </div>
          </p-card>
        </div>
      </ng-container>
    </section>
  `
})
export class CompareComponent implements OnInit {
  samples: any[] = [];
  a = '';
  b = '';
  result: any;
  error = '';
  chart: EChartsOption | null = null;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.allSamples().subscribe(s => this.samples = s);
  }

  run(): void {
    this.error = '';
    this.api.compare(this.a, this.b).subscribe({
      next: (res) => {
        this.result = res;
        const categories: string[] = [];
        const valuesA: number[] = [];
        const valuesB: number[] = [];
        if (res.fastq) {
          for (const [k, v] of Object.entries(res.fastq)) {
            categories.push(`FASTQ ${k}`);
            valuesA.push((v as any).a);
            valuesB.push((v as any).b);
          }
        }
        if (res.vcf) {
          for (const [k, v] of Object.entries(res.vcf)) {
            categories.push(`VCF ${k}`);
            valuesA.push((v as any).a);
            valuesB.push((v as any).b);
          }
        }
        this.chart = {
          color: ['#0f6a4d', '#c45c26'],
          textStyle: { fontFamily: 'IBM Plex Sans, sans-serif' },
          tooltip: { trigger: 'axis' },
          legend: { data: [res.sampleA.name, res.sampleB.name] },
          grid: { left: 48, right: 24, bottom: 80 },
          xAxis: { type: 'category', data: categories, axisLabel: { rotate: 30 } },
          yAxis: { type: 'value' },
          series: [
            { name: res.sampleA.name, type: 'bar', data: valuesA, itemStyle: { borderRadius: [5, 5, 0, 0] } },
            { name: res.sampleB.name, type: 'bar', data: valuesB, itemStyle: { borderRadius: [5, 5, 0, 0] } }
          ]
        };
      },
      error: (err) => this.error = err?.error?.message || 'Compare failed'
    });
  }
}
