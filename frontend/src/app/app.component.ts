import { Component, HostListener, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ToastModule } from 'primeng/toast';
import {
  LucideDna,
  LucideLayoutDashboard,
  LucideFolderKanban,
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
    LucideDna, LucideLayoutDashboard, LucideFolderKanban, LucideSearch, LucideMenu, LucideX
  ],
  template: `
    <p-toast position="bottom-right" />
    <a class="skip-link" href="#main-content">Skip to main content</a>

    <div class="shell" [class.auth-shell]="authRoute" [class.public-shell]="publicRoute">
      <header class="topbar" *ngIf="!authRoute && !publicRoute">
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
        *ngIf="!authRoute && !publicRoute"
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
              Panel
            </a>
            <app-help-hint text="Named charts for the FASTQ and VCF metrics already calculated, plus job status." />
          </div>
          <div class="nav-item">
            <a routerLink="/explore" routerLinkActive="active" (click)="closeNavigation()">
              <svg lucideSearch size="18"></svg>
              Explore
            </a>
            <app-help-hint text="Query catalogued metrics and ask questions that cite those facts." />
          </div>
          <div class="nav-item">
            <a routerLink="/projects" routerLinkActive="active" (click)="closeNavigation()">
              <svg lucideFolderKanban size="18"></svg>
              Projects
            </a>
            <app-help-hint text="Create projects, add samples, and upload FASTQ or VCF files." />
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
  publicRoute = false;
  menuOpen = false;

  private readonly router = inject(Router);

  constructor() {
    this.syncShell(this.router.url);
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(e => {
        this.syncShell(e.urlAfterRedirects);
        this.closeNavigation();
      });
  }

  private syncShell(url: string): void {
    const path = url.split('?')[0];
    this.authRoute = /^\/(login|register)/.test(path);
    this.publicRoute = path === '/' || path === '';
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
