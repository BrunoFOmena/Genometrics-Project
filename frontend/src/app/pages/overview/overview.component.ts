import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import {
  LucideFolderKanban,
  LucideFlaskConical,
  LucideRefreshCw,
  LucideCircleCheck,
  LucideHistory
} from '@lucide/angular';
import gsap from 'gsap';
import { ApiService } from '../../core/api.service';
import { HelpHintComponent } from '../../core/help-hint.component';

interface StatCard {
  key: string;
  label: string;
  icon: string;
  hint: string;
  value: number;
}

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    HelpHintComponent,
    CardModule,
    TagModule,
    LucideFolderKanban,
    LucideFlaskConical,
    LucideRefreshCw,
    LucideCircleCheck,
    LucideHistory
  ],
  template: `
    <section>
      <header class="page-head">
        <div>
          <h1 class="head-row">
            Overview
            <app-help-hint text="Dashboard summary of your workspace: project and sample counts, analysis progress, and recent jobs." />
          </h1>
          <p>Workspace summary across projects and analyses.</p>
        </div>
      </header>

      <div class="overview-stat-grid">
        <p-card *ngFor="let stat of stats" styleClass="overview-stat-card">
          <div class="stat-icon" [ngSwitch]="stat.key">
            <svg *ngSwitchCase="'projects'" lucideFolderKanban size="22"></svg>
            <svg *ngSwitchCase="'samples'" lucideFlaskConical size="22"></svg>
            <svg *ngSwitchCase="'analyses'" lucideRefreshCw size="22"></svg>
            <svg *ngSwitchCase="'done'" lucideCircleCheck size="22"></svg>
          </div>
          <div class="overview-stat-value" [attr.data-stat]="stat.key">0</div>
          <div class="overview-stat-label label-row">
            {{ stat.label }}
            <app-help-hint [text]="stat.hint" />
          </div>
        </p-card>
      </div>

      <div class="section-head">
        <h2>Recent analyses</h2>
        <app-help-hint text="Latest jobs by creation time. Open History for the full list." />
      </div>
      <ul class="list recent-list">
        <li *ngFor="let a of recent">
          <a routerLink="/history" class="list-row">
            <svg lucideHistory size="18" class="row-icon"></svg>
            <span class="row-main">
              <strong>Sample {{ a.sampleId }}</strong>
              <span class="muted">{{ a.engine }}</span>
            </span>
            <p-tag [value]="a.status" [severity]="statusSeverity(a.status)" />
          </a>
        </li>
      </ul>
      <p class="muted" *ngIf="!recent.length">No analyses yet.</p>
    </section>
  `
})
export class OverviewComponent implements OnInit, OnDestroy {
  stats: StatCard[] = [
    { key: 'projects', label: 'Projects', icon: 'folder', hint: 'Total sequencing projects in your account.', value: 0 },
    { key: 'samples', label: 'Samples', icon: 'science', hint: 'Total samples across all projects.', value: 0 },
    { key: 'analyses', label: 'Analyses', icon: 'sync', hint: 'All parse jobs ever queued (FASTQ, VCF, or FASTA metadata).', value: 0 },
    { key: 'done', label: 'Done', icon: 'check_circle', hint: 'Analyses that finished successfully with metrics available.', value: 0 }
  ];

  recent: any[] = [];
  private tweens: gsap.core.Tween[] = [];

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.projects().subscribe(p => this.setStat('projects', p.length));
    this.api.allSamples().subscribe(s => this.setStat('samples', s.length));
    this.api.history().subscribe(h => {
      this.recent = h.slice(0, 8);
      this.setStat('analyses', h.length);
      this.setStat('done', h.filter(x => x.status === 'DONE').length);
    });
  }

  ngOnDestroy(): void {
    this.tweens.forEach(t => t.kill());
  }

  statusSeverity(status: string): 'success' | 'danger' | 'info' | 'secondary' {
    switch (status) {
      case 'DONE': return 'success';
      case 'FAILED': return 'danger';
      case 'RUNNING': return 'info';
      default: return 'secondary';
    }
  }

  private setStat(key: string, value: number): void {
    const stat = this.stats.find(s => s.key === key);
    if (!stat) return;
    stat.value = value;
    setTimeout(() => this.animateStat(key, value), 0);
  }

  private animateStat(key: string, value: number): void {
    const el = document.querySelector(`[data-stat="${key}"]`) as HTMLElement | null;
    if (!el) return;
    const counter = { val: 0 };
    const tween = gsap.to(counter, {
      val: value,
      duration: 0.8,
      ease: 'power2.out',
      onUpdate: () => {
        el.textContent = String(Math.round(counter.val));
      }
    });
    this.tweens.push(tween);
  }
}
