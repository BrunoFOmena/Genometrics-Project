import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <section class="auth">
      <h1>GENOMETRICS</h1>
      <p>Sign in to manage sequencing projects.</p>
      <form (ngSubmit)="submit()">
        <label>Email <input type="email" [(ngModel)]="email" name="email" required /></label>
        <label>Password <input type="password" [(ngModel)]="password" name="password" required /></label>
        <button type="submit">Sign in</button>
        <p class="error" *ngIf="error">{{ error }}</p>
      </form>
      <a routerLink="/register">Create an account</a>
    </section>
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
