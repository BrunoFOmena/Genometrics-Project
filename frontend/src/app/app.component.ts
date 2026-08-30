import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <aside class="nav">
        <div class="brand-block">
          <div class="brand">GENOMETRICS</div>
          <div class="brand-sub">by Bruno Omena</div>
        </div>
        <nav>
          <a routerLink="/overview" routerLinkActive="active">Overview</a>
          <a routerLink="/projects" routerLinkActive="active">Projects</a>
          <a routerLink="/compare" routerLinkActive="active">Compare</a>
          <a routerLink="/history" routerLinkActive="active">History</a>
        </nav>
        <div class="sidebar-foot">
          <span class="dev-badge" *ngIf="authDisabled">Auth disabled · dev mode</span>
        </div>
      </aside>
      <main>
        <router-outlet />
      </main>
    </div>
  `
})
export class AppComponent {
  readonly authDisabled = environment.authDisabled;
}
