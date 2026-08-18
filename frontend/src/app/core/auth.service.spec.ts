import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';

const body = {
  token: 'jwt-token',
  email: 'a@b.com',
  displayName: 'Ana',
  role: 'RESEARCHER',
  userId: 'u1'
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('persists login response', () => {
    service.login('a@b.com', 'secret12').subscribe();
    http.expectOne('http://localhost:8080/api/auth/login').flush(body);
    expect(service.isLoggedIn()).toBeTrue();
    expect(service.token()).toBe('jwt-token');
    expect(JSON.parse(localStorage.getItem('ngs_auth')!).email).toBe('a@b.com');
  });

  it('logout clears storage', () => {
    service.login('a@b.com', 'secret12').subscribe();
    http.expectOne('http://localhost:8080/api/auth/login').flush(body);
    service.logout();
    expect(service.isLoggedIn()).toBeFalse();
    expect(localStorage.getItem('ngs_auth')).toBeNull();
  });
});

describe('AuthService with corrupt storage', () => {
  it('invalid localStorage yields a logged-out user', () => {
    localStorage.setItem('ngs_auth', '{not-json');
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    const broken = TestBed.inject(AuthService);
    expect(broken.user()).toBeNull();
    expect(broken.isLoggedIn()).toBeFalse();
    localStorage.clear();
  });
});
