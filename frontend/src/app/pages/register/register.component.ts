import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <section class="auth">
      <h1>Create account</h1>
      <p>Join the GENOMETRICS workspace.</p>
      <form (ngSubmit)="submit()">
        <label>Display name <input [(ngModel)]="displayName" name="displayName" required /></label>
        <label>Email <input type="email" [(ngModel)]="email" name="email" required /></label>
        <label>Password <input type="password" [(ngModel)]="password" name="password" required minlength="6" /></label>
        <button type="submit">Register</button>
        <p class="error" *ngIf="error">{{ error }}</p>
      </form>
      <a routerLink="/login">Back to sign in</a>
    </section>
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
