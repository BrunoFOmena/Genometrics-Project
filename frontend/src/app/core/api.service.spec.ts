import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ApiService, provideHttpClient(), provideHttpClientTesting()]
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts createProject to /projects', () => {
    api.createProject('Cohort', 'desc').subscribe();
    const req = http.expectOne('http://localhost:8080/api/projects');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Cohort', description: 'desc' });
    req.flush({ id: 'p1', name: 'Cohort' });
  });

  it('uploads a file as multipart form data', () => {
    const file = new File(['@r\nACGT\n+\nIIII\n'], 'a.fastq');
    api.upload('s1', file).subscribe();
    const req = http.expectOne('http://localhost:8080/api/samples/s1/files');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    expect((req.request.body as FormData).get('file')).toBeTruthy();
    req.flush({ analysis: { status: 'QUEUED' } });
  });
});
