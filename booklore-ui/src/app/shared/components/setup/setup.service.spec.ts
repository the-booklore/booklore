import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';

import {SetupPayload, SetupService} from './setup.service';

describe('SetupService', () => {
  let service: SetupService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        SetupService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(
      injector,
      () => TestBed.inject(SetupService)
    );
  });

  it('should create admin with correct payload', () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    const payload: SetupPayload = {email: 'admin@test.com', password: 'pass'};
    service.createAdmin(payload).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/setup'),
        payload
      );
    });
  });

  it('should handle error when creating admin', () => {
    httpClientMock.post.mockReturnValue(throwError(() => new Error('fail')));
    service.createAdmin({email: 'fail', password: 'fail'}).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });
});

describe('SetupService - API Contract Tests', () => {
  let service: SetupService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        SetupService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(
      injector,
      () => TestBed.inject(SetupService)
    );
  });

  it('should call correct endpoint for createAdmin', () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    const payload: SetupPayload = {email: 'a@b.com', password: 'pw'};
    service.createAdmin(payload).subscribe();
    expect(httpClientMock.post).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/setup$/),
      payload
    );
  });

  it('should expect void from createAdmin', () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    service.createAdmin({email: 'x@y.com', password: 'pw'}).subscribe(result => {
      expect(result).toBeUndefined();
    });
  });

  it('should send correct payload structure', () => {
    httpClientMock.post.mockImplementation((url: string, body: SetupPayload) => {
      expect(body).toHaveProperty('email');
      expect(body).toHaveProperty('password');
      return of(void 0);
    });
    service.createAdmin({email: 'z@z.com', password: 'pw'}).subscribe();
  });
});

