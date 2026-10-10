import { Component, HostListener, inject } from '@angular/core';
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
  LucideSearch,
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
    LucideDna, LucideLayoutDashboard, LucideFolderKanban, LucideGitCompareArrows, LucideHistory, LucideSearch, LucideMenu, LucideX
  ],
  template: `
    <p-toast position="bottom-right" />
    <a class="skip-link" href="#main-content">Skip to main content</a>

    <div class="shell" [class.auth-shell]="authRoute">
      <header class="topbar" *ngIf="!authRoute">
        <button
          type="button"
          class="menu-btn"
          (click)="toggleNavigation()"
          [attr.aria-expanded]="menuOpen"
          aria-controls="primary-navigation"
          aria-label="Toggle navigation"
        >
          <svg *ngIf="!menuOpen" lucideMenu size="20"></svg>
          <svg *ngIf="menuOpen" lucideX size="20"></svg>
        </button>
        <a class="topbar-brand" routerLink="/overview" aria-label="GENOMETRICS overview">
          <span class="brand-mark brand-mark-small">
            <svg lucideDna size="19"></svg>
          </span>
          <span>GENOMETRICS</span>
        </a>
        <span class="topbar-context">Genomic analytics workspace</span>
      </header>

      <button
        type="button"
        class="nav-backdrop"
        *ngIf="menuOpen"
        (click)="closeNavigation()"
        aria-label="Close navigation"
      ></button>

      <aside
        id="primary-navigation"
        class="nav"
        [class.open]="menuOpen"
        *ngIf="!authRoute"
        aria-label="Primary navigation"
      >
        <div class="sidebar-head">
          <a class="brand-block" routerLink="/overview" (click)="closeNavigation()" aria-label="GENOMETRICS overview">
            <span class="brand-mark">
              <svg lucideDna size="24"></svg>
            </span>
            <span class="brand-copy">
              <strong class="brand">GENOMETRICS</strong>
              <span class="brand-sub">by Bruno Omena</span>
            </span>
          </a>
          <button type="button" class="sidebar-close" (click)="closeNavigation()" aria-label="Close navigation">
            <svg lucideX size="20"></svg>
          </button>
        </div>

        <nav aria-label="Workspace">
          <span class="nav-eyebrow">Workspace</span>
          <div class="nav-item">
            <a routerLink="/overview" routerLinkActive="active" (click)="closeNavigation()">
              <svg lucideLayoutDashboard size="18"></svg>
              Overview
            </a>
            <app-help-hint text="Dashboard with project, sample, and analysis counts plus recent jobs." />
          </div>
          <div class="nav-item">
            <a routerLink="/explore" routerLinkActive="active" (click)="closeNavigation()">
              <svg lucideSearch size="18"></svg>
              Explore
            </a>
            <app-help-hint text="Query catalogued metrics and ask the grounded local analyst." />
          </div>
          <div class="nav-item">
            <a routerLink="/projects" routerLinkActive="active" (click)="closeNavigation()">
              <svg lucideFolderKanban size="18"></svg>
              Projects
            </a>
            <app-help-hint text="Create and open projects, then add samples and upload sequencing files." />
          </div>
          <div class="nav-item">
            <a routerLink="/compare" routerLinkActive="active" (click)="closeNavigation()">
              <svg lucideGitCompareArrows size="18"></svg>
              Compare
            </a>
            <app-help-hint text="Compare FASTQ/VCF metrics between two samples side by side." />
          </div>
          <div class="nav-item">
            <a routerLink="/history" routerLinkActive="active" (click)="closeNavigation()">
              <svg lucideHistory size="18"></svg>
              History
            </a>
            <app-help-hint text="Full log of all analysis jobs and their status." />
          </div>
        </nav>

        <div class="sidebar-foot">
          <span class="status-dot" [class.status-dot-live]="!authDisabled" aria-hidden="true"></span>
          <span class="status-copy">
            <strong>{{ authDisabled ? 'Development mode' : 'Protected workspace' }}</strong>
            <span>{{ authDisabled ? 'Authentication disabled' : 'Authentication enabled' }}</span>
          </span>
        </div>
      </aside>

      <main id="main-content" tabindex="-1">
        <div class="content-frame">
          <router-outlet />
        </div>
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
        this.closeNavigation();
      });
  }

  toggleNavigation(): void {
    this.menuOpen = !this.menuOpen;
  }

  closeNavigation(): void {
    this.menuOpen = false;
  }

  @HostListener('document:keydown.escape')
  closeNavigationOnEscape(): void {
    this.closeNavigation();
  }
}
