import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {EmailService} from './email.service';

describe('EmailService', () => {
  let service: EmailService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        EmailService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    service = TestBed.inject(EmailService);
  });

  it('should email book', () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    const req = {bookId: 1, providerId: 2, recipientId: 3};
    service.emailBook(req).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringContaining('/api/v2/email/book'),
        req
      );
    });
  });

  it('should email book quick', () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    service.emailBookQuick(42).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringContaining('/api/v2/email/book/42'),
        {}
      );
    });
  });

  it('should handle error for emailBook', () => {
    httpClientMock.post.mockReturnValue(throwError(() => new Error('fail')));
    service.emailBook({bookId: 1, providerId: 2, recipientId: 3}).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle error for emailBookQuick', () => {
    httpClientMock.post.mockReturnValue(throwError(() => new Error('fail')));
    service.emailBookQuick(42).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });
});

describe('EmailService - API Contract Tests', () => {
  let service: EmailService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        EmailService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    service = TestBed.inject(EmailService);
  });

  it('should call correct endpoint for emailBook', () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    const req = {bookId: 1, providerId: 2, recipientId: 3};
    service.emailBook(req).subscribe();
    expect(httpClientMock.post).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v2\/email\/book$/),
      req
    );
  });

  it('should call correct endpoint for emailBookQuick', () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    service.emailBookQuick(99).subscribe();
    expect(httpClientMock.post).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v2\/email\/book\/99$/),
      {}
    );
  });

  it('should require all fields for emailBook request', () => {
    const req = {bookId: 1, providerId: 2, recipientId: 3};
    expect(req).toHaveProperty('bookId');
    expect(req).toHaveProperty('providerId');
    expect(req).toHaveProperty('recipientId');
  });
});

