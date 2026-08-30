import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { LucideFlaskConical, LucideChevronRight, LucideTestTubes } from '@lucide/angular';
import { ApiService } from '../../core/api.service';
import { HelpHintComponent } from '../../core/help-hint.component';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, HelpHintComponent,
    CardModule, ButtonModule, InputTextModule,
    LucideFlaskConical, LucideChevronRight, LucideTestTubes
  ],
  template: `
    <section *ngIf="project">
      <header class="page-head">
        <div>
          <nav class="breadcrumb" aria-label="Breadcrumb">
            <a routerLink="/projects">Projects</a>
            <svg lucideChevronRight size="14"></svg>
            <span>{{ project.name }}</span>
          </nav>
          <h1>{{ project.name }}</h1>
          <p class="head-row">
            {{ project.description || 'Project samples' }}
            <app-help-hint text="Each sample represents one specimen or library. Upload FASTQ/VCF on the sample page after creating it here." />
          </p>
        </div>
      </header>

      <p-card styleClass="form-card">
        <form class="inline-form" (ngSubmit)="createSample()">
          <label>
            <span class="label-row">
              Sample name
              <app-help-hint text="Identifier for this specimen or library prep (e.g. Patient-001-R1)." />
            </span>
            <input pInputText [(ngModel)]="sampleName" name="sampleName" placeholder="Sample name" required />
          </label>
          <label>
            <span class="label-row">
              Notes
              <app-help-hint text="Optional context: batch ID, kit, tissue type, or run date." />
            </span>
            <input pInputText [(ngModel)]="notes" name="notes" placeholder="Notes" />
          </label>
          <label>
            <span class="label-row">
              FASTA reference
              <app-help-hint text="Optional label for a reference genome. Upload the actual FASTA file on the sample page; this field stores its display name." />
            </span>
            <input pInputText [(ngModel)]="fastaRef" name="fastaRef" placeholder="FASTA reference name (optional)" />
          </label>
          <label>
            <span class="label-row">
              Add sample
              <app-help-hint text="Creates the sample and adds it to the list. Click the sample to upload and analyze files." />
            </span>
            <button pButton type="submit" label="Add sample"></button>
          </label>
        </form>
      </p-card>

      <ul class="list">
        <li *ngFor="let s of samples">
          <a [routerLink]="['/samples', s.id]" class="list-row">
            <svg lucideFlaskConical size="20" class="row-icon"></svg>
            <span class="row-main">
              <strong>{{ s.name }}</strong>
              <span class="muted">{{ s.notes || 'No notes' }}</span>
            </span>
            <svg lucideChevronRight size="18" class="row-chevron"></svg>
          </a>
        </li>
      </ul>

      <div class="empty-state" *ngIf="!samples.length">
        <svg lucideTestTubes size="34"></svg>
        <strong>No samples yet</strong>
        <span>Add a sample above, then open it to upload FASTQ, VCF, or FASTA files.</span>
      </div>
    </section>
  `
})
export class ProjectDetailComponent implements OnInit {
  project: any;
  samples: any[] = [];
  sampleName = '';
  notes = '';
  fastaRef = '';
  private projectId = '';

  constructor(private route: ActivatedRoute, private api: ApiService) {}

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id') || '';
    this.api.project(this.projectId).subscribe(p => this.project = p);
    this.reloadSamples();
  }

  createSample(): void {
    this.api.createSample(this.projectId, this.sampleName, this.notes, this.fastaRef || undefined)
      .subscribe(() => {
        this.sampleName = '';
        this.notes = '';
        this.fastaRef = '';
        this.reloadSamples();
      });
  }

  private reloadSamples(): void {
    this.api.samples(this.projectId).subscribe(s => this.samples = s);
  }
}
