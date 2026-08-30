import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ApiService } from '../../core/api.service';
import { HelpHintComponent } from '../../core/help-hint.component';

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule, HelpHintComponent, TableModule, TagModule],
  template: `
    <section>
      <header class="page-head">
        <div>
          <h1 class="head-row">
            Analysis history
            <app-help-hint text="Complete log of every analysis job: queued, running, completed, or failed." />
          </h1>
          <p>Queued, running, completed, and failed jobs.</p>
        </div>
      </header>
      <div class="section-head">
        <h2>Job log</h2>
        <app-help-hint text="Status: QUEUED → RUNNING → DONE or FAILED. Engine shows JAVA for parsed files or METADATA for FASTA-only uploads." />
      </div>
      <p-table [value]="rows" responsiveLayout="scroll" [tableStyle]="{ 'min-width': '56rem' }" styleClass="history-table">
        <ng-template pTemplate="header">
          <tr>
            <th pSortableColumn="status">Status <p-sortIcon field="status" /> <app-help-hint text="Current job state." /></th>
            <th>Engine <app-help-hint text="JAVA runs FASTQ/VCF parsers; METADATA stores FASTA reference info only." /></th>
            <th>Sample <app-help-hint text="Sample UUID the file belongs to." /></th>
            <th pSortableColumn="createdAt">Created <p-sortIcon field="createdAt" /> <app-help-hint text="When the upload queued the analysis." /></th>
            <th>Finished <app-help-hint text="When parsing completed or failed." /></th>
            <th>Error <app-help-hint text="Failure message if status is FAILED." /></th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-a>
          <tr>
            <td><p-tag [value]="a.status" [severity]="statusSeverity(a.status)" /></td>
            <td>{{ a.engine }}</td>
            <td class="seq-cell">{{ a.sampleId }}</td>
            <td>{{ a.createdAt | date:'short' }}</td>
            <td>{{ a.finishedAt ? (a.finishedAt | date:'short') : '—' }}</td>
            <td>{{ a.errorMessage || '—' }}</td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr>
            <td colspan="6" class="muted">No analysis jobs yet. Upload a file on a sample page to queue one.</td>
          </tr>
        </ng-template>
      </p-table>
    </section>
  `
})
export class HistoryComponent implements OnInit {
  rows: any[] = [];

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.history().subscribe(h => this.rows = h);
  }

  statusSeverity(status: string): 'success' | 'danger' | 'info' | 'secondary' {
    switch (status) {
      case 'DONE': return 'success';
      case 'FAILED': return 'danger';
      case 'RUNNING': return 'info';
      default: return 'secondary';
    }
  }
}
