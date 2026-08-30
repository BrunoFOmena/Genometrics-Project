import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { LucideFolderOpen, LucideChevronRight, LucideFolderKanban } from '@lucide/angular';
import { ApiService } from '../../core/api.service';
import { HelpHintComponent } from '../../core/help-hint.component';

@Component({
  selector: 'app-projects',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, HelpHintComponent,
    CardModule, ButtonModule, InputTextModule,
    LucideFolderOpen, LucideChevronRight, LucideFolderKanban
  ],
  template: `
    <section>
      <header class="page-head">
        <div>
          <h1 class="head-row">
            Projects
            <app-help-hint text="Projects group related samples under one study or cohort. Create a project first, then add samples and upload sequencing files." />
          </h1>
          <p>Organize samples and sequencing uploads.</p>
        </div>
      </header>

      <p-card styleClass="form-card">
        <form class="inline-form" (ngSubmit)="create()">
          <label>
            <span class="label-row">
              Project name
              <app-help-hint text="Short name for the study (e.g. WES Cohort 2024)." />
            </span>
            <input pInputText [(ngModel)]="name" name="name" placeholder="Project name" required />
          </label>
          <label>
            <span class="label-row">
              Description
              <app-help-hint text="Optional notes about goals, protocol, or panel used." />
            </span>
            <input pInputText [(ngModel)]="description" name="description" placeholder="Description" />
          </label>
          <label>
            <span class="label-row">
              Create
              <app-help-hint text="Saves the project and lists it below. Open it to add samples." />
            </span>
            <button pButton type="submit" label="Create"></button>
          </label>
        </form>
      </p-card>

      <ul class="list">
        <li *ngFor="let p of projects">
          <a [routerLink]="['/projects', p.id]" class="list-row">
            <svg lucideFolderOpen size="20" class="row-icon"></svg>
            <span class="row-main">
              <strong>{{ p.name }}</strong>
              <span class="muted">{{ p.description || 'No description' }}</span>
            </span>
            <svg lucideChevronRight size="18" class="row-chevron"></svg>
          </a>
        </li>
      </ul>

      <div class="empty-state" *ngIf="!projects.length">
        <svg lucideFolderKanban size="34"></svg>
        <strong>No projects yet</strong>
        <span>Create your first project above to start uploading sequencing files.</span>
      </div>
    </section>
  `
})
export class ProjectsComponent implements OnInit {
  projects: any[] = [];
  name = '';
  description = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.reload();
  }

  create(): void {
    this.api.createProject(this.name, this.description).subscribe(() => {
      this.name = '';
      this.description = '';
      this.reload();
    });
  }

  private reload(): void {
    this.api.projects().subscribe(p => this.projects = p);
  }
}
