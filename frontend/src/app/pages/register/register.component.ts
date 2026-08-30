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
  selector: 'app-register',
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
          Create account
          <app-help-hint text="Register a new workspace account. Each user sees only their own projects and samples." />
        </h1>
        <p>Join the GENOMETRICS workspace.</p>
        <form (ngSubmit)="submit()">
          <label>
            <span class="label-row">
              Display name
              <app-help-hint text="Name shown in the app header after sign-in." />
            </span>
            <input pInputText [(ngModel)]="displayName" name="displayName" required autocomplete="name" />
          </label>
          <label>
            <span class="label-row">
              Email
              <app-help-hint text="Used to sign in; must be unique across the platform." />
            </span>
            <input pInputText type="email" [(ngModel)]="email" name="email" required autocomplete="email" />
          </label>
          <label>
            <span class="label-row">
              Password
              <app-help-hint text="At least 6 characters. Stored hashed on the server." />
            </span>
            <input pInputText type="password" [(ngModel)]="password" name="password" required minlength="6" autocomplete="new-password" />
          </label>
          <button pButton type="submit" label="Register"></button>
          <p class="error" *ngIf="error">{{ error }}</p>
        </form>
        <a routerLink="/login">Back to sign in</a>
      </section>
    </div>
  `
})
export class RegisterComponent {
  displayName = '';
  email = '';
  password = '';
  error = '';

  constructor(private auth: AuthService, private router: Router) {}

  submit(): void {
    this.error = '';
    this.auth.register(this.email, this.password, this.displayName).subscribe({
      next: () => this.router.navigateByUrl('/projects'),
      error: (err) => this.error = err?.error?.message || 'Registration failed'
    });
  }
}
