import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../core/auth.service';

describe('LoginComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    auth = jasmine.createSpyObj('AuthService', ['login']);
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth }
      ]
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
  });

  it('navigates to projects on success', () => {
    auth.login.and.returnValue(of({
      token: 't', email: 'a@b.com', displayName: 'A', role: 'RESEARCHER', userId: '1'
    }));
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.componentInstance.email = 'a@b.com';
    fixture.componentInstance.password = 'secret12';
    fixture.componentInstance.submit();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/projects');
  });

  it('shows API error on 401', () => {
    auth.login.and.returnValue(throwError(() => ({ error: { message: 'Invalid credentials' } })));
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.componentInstance.submit();
    fixture.detectChanges();
    expect(fixture.componentInstance.error).toBe('Invalid credentials');
    expect(fixture.nativeElement.querySelector('.error').textContent).toContain('Invalid credentials');
  });
});
