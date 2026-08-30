import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ToastModule } from 'primeng/toast';
import {
  LucideDna,
  LucideLayoutDashboard,
  LucideFolderKanban,
  LucideGitCompareArrows,
  LucideHistory,
  LucideMenu,
  LucideX
} from '@lucide/angular';
import { environment } from '../environments/environment';
import { HelpHintComponent } from './core/help-hint.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule, RouterOutlet, RouterLink, RouterLinkActive, HelpHintComponent, ToastModule,
    LucideDna, LucideLayoutDashboard, LucideFolderKanban, LucideGitCompareArrows, LucideHistory, LucideMenu, LucideX
  ],
  template: `
    <p-toast position="bottom-right" />
    <div class="shell" [class.auth-shell]="authRoute">
      <header class="topbar" *ngIf="!authRoute">
        <button type="button" class="menu-btn" (click)="menuOpen = !menuOpen" [attr.aria-expanded]="menuOpen" aria-label="Toggle navigation">
          <svg *ngIf="!menuOpen" lucideMenu size="20"></svg>
          <svg *ngIf="menuOpen" lucideX size="20"></svg>
        </button>
        <span class="brand">GENOMETRICS</span>
      </header>
      <div class="nav-backdrop" *ngIf="menuOpen" (click)="menuOpen = false"></div>
      <aside class="nav" [class.open]="menuOpen" *ngIf="!authRoute">
        <div class="brand-block">
          <div class="brand brand-row">
            <svg lucideDna size="22" class="brand-icon"></svg>
            GENOMETRICS
          </div>
          <div class="brand-sub">by Bruno Omena</div>
        </div>
        <nav>
          <div class="nav-item">
            <a routerLink="/overview" routerLinkActive="active" (click)="menuOpen = false">
              <svg lucideLayoutDashboard size="17"></svg>
              Overview
            </a>
            <app-help-hint text="Dashboard with project, sample, and analysis counts plus recent jobs." />
          </div>
          <div class="nav-item">
            <a routerLink="/projects" routerLinkActive="active" (click)="menuOpen = false">
              <svg lucideFolderKanban size="17"></svg>
              Projects
            </a>
            <app-help-hint text="Create and open projects, then add samples and upload sequencing files." />
          </div>
          <div class="nav-item">
            <a routerLink="/compare" routerLinkActive="active" (click)="menuOpen = false">
              <svg lucideGitCompareArrows size="17"></svg>
              Compare
            </a>
            <app-help-hint text="Compare FASTQ/VCF metrics between two samples side by side." />
          </div>
          <div class="nav-item">
            <a routerLink="/history" routerLinkActive="active" (click)="menuOpen = false">
              <svg lucideHistory size="17"></svg>
              History
            </a>
            <app-help-hint text="Full log of all analysis jobs and their status." />
          </div>
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

  authRoute = false;
  menuOpen = false;

  private readonly router = inject(Router);

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(e => {
        this.authRoute = /^\/(login|register)/.test(e.urlAfterRedirects);
        this.menuOpen = false;
      });
  }
}
