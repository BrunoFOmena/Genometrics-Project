import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { LucideDna } from '@lucide/angular';
import { AuthService } from '../../core/auth.service';
import { HelpHintComponent } from '../../core/help-hint.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, HelpHintComponent, ButtonModule, InputTextModule, LucideDna],
  template: `
    <div class="auth-page">
      <section class="auth">
        <div class="auth-brand">
          <svg lucideDna size="26"></svg>
          <span>GENOMETRICS</span>
        </div>
        <h1 class="head-row">
          Welcome back
          <app-help-hint text="Sign in to access your projects and analyses. Auth is disabled in current dev mode." />
        </h1>
        <p>Sign in to manage sequencing projects.</p>
        <form (ngSubmit)="submit()">
          <label>
            <span class="label-row">
              Email
              <app-help-hint text="Account email used at registration." />
            </span>
            <input pInputText type="email" [(ngModel)]="email" name="email" required autocomplete="email" />
          </label>
          <label>
            <span class="label-row">
              Password
              <app-help-hint text="Your account password (minimum 6 characters at registration)." />
            </span>
            <input pInputText type="password" [(ngModel)]="password" name="password" required autocomplete="current-password" />
          </label>
          <button pButton type="submit" label="Sign in"></button>
          <p class="error" *ngIf="error">{{ error }}</p>
        </form>
        <a routerLink="/register">Create an account</a>
      </section>
    </div>
  `
})
export class LoginComponent {
  email = '';
  password = '';
  error = '';

  constructor(private auth: AuthService, private router: Router) {}

  submit(): void {
    this.error = '';
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigateByUrl('/projects'),
      error: (err) => this.error = err?.error?.message || 'Login failed'
    });
  }
}
