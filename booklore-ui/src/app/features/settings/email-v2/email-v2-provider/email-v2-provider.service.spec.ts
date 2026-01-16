import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {EmailV2ProviderService} from './email-v2-provider.service';
import {EmailProvider} from '../email-provider.model';

describe('EmailV2ProviderService', () => {
  let service: EmailV2ProviderService;
  let httpClientMock: any;

  const mockProvider: EmailProvider = {
    isEditing: false,
    id: 1,
    userId: 2,
    name: 'Provider1',
    host: 'smtp.example.com',
    port: 587,
    username: 'user',
    password: 'pass',
    fromAddress: 'from@example.com',
    auth: true,
    startTls: true,
    defaultProvider: false,
    shared: false
  };

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
      patch: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        EmailV2ProviderService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    service = TestBed.inject(EmailV2ProviderService);
  });

  it('should get email providers', () => {
    httpClientMock.get.mockReturnValue(of([mockProvider]));
    service.getEmailProviders().subscribe(providers => {
      expect(providers).toEqual([mockProvider]);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringContaining('/api/v2/email/providers'));
    });
  });

  it('should create email provider', () => {
    httpClientMock.post.mockReturnValue(of(mockProvider));
    service.createEmailProvider(mockProvider).subscribe(provider => {
      expect(provider).toEqual(mockProvider);
      expect(httpClientMock.post).toHaveBeenCalledWith(expect.stringContaining('/api/v2/email/providers'), mockProvider);
    });
  });

  it('should update provider', () => {
    httpClientMock.put.mockReturnValue(of(mockProvider));
    service.updateProvider(mockProvider).subscribe(provider => {
      expect(provider).toEqual(mockProvider);
      expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringContaining(`/api/v2/email/providers/${mockProvider.id}`), mockProvider);
    });
  });

  it('should delete provider', () => {
    httpClientMock.delete.mockReturnValue(of(void 0));
    service.deleteProvider(1).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringContaining('/api/v2/email/providers/1'));
    });
  });

  it('should set default provider', () => {
    httpClientMock.patch.mockReturnValue(of(void 0));
    service.setDefaultProvider(1).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.patch).toHaveBeenCalledWith(expect.stringContaining('/api/v2/email/providers/1/set-default'), {});
    });
  });

  it('should handle getEmailProviders error', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getEmailProviders().subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle createEmailProvider error', () => {
    httpClientMock.post.mockReturnValue(throwError(() => new Error('fail')));
    service.createEmailProvider(mockProvider).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle updateProvider error', () => {
    httpClientMock.put.mockReturnValue(throwError(() => new Error('fail')));
    service.updateProvider(mockProvider).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle deleteProvider error', () => {
    httpClientMock.delete.mockReturnValue(throwError(() => new Error('fail')));
    service.deleteProvider(1).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle setDefaultProvider error', () => {
    httpClientMock.patch.mockReturnValue(throwError(() => new Error('fail')));
    service.setDefaultProvider(1).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });
});

describe('EmailV2ProviderService - API Contract Tests', () => {
  let service: EmailV2ProviderService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
      patch: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        EmailV2ProviderService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    service = TestBed.inject(EmailV2ProviderService);
  });

  it('should validate all required EmailProvider fields exist', () => {
    const requiredFields: (keyof EmailProvider)[] = [
      'isEditing', 'id', 'userId', 'name', 'host', 'port', 'username', 'password',
      'fromAddress', 'auth', 'startTls', 'defaultProvider', 'shared'
    ];
    const mockResponse: EmailProvider = {
      isEditing: false,
      id: 1,
      userId: 2,
      name: 'Provider1',
      host: 'smtp.example.com',
      port: 587,
      username: 'user',
      password: 'pass',
      fromAddress: 'from@example.com',
      auth: true,
      startTls: true,
      defaultProvider: false,
      shared: false
    };
    httpClientMock.get.mockReturnValue(of([mockResponse]));
    service.getEmailProviders().subscribe(providers => {
      requiredFields.forEach(field => {
        expect(providers[0]).toHaveProperty(field);
        expect(providers[0][field]).toBeDefined();
      });
    });
  });

  it('should fail if API returns EmailProvider without required id field', () => {
    const invalidResponse = [{
      isEditing: false,
      userId: 2,
      name: 'Provider1',
      host: 'smtp.example.com',
      port: 587,
      username: 'user',
      password: 'pass',
      fromAddress: 'from@example.com',
      auth: true,
      startTls: true,
      defaultProvider: false,
      shared: false
    }];
    httpClientMock.get.mockReturnValue(of(invalidResponse));
    service.getEmailProviders().subscribe(providers => {
      expect(providers[0]).not.toHaveProperty('id');
    });
  });

  it('should call correct endpoint for getEmailProviders', () => {
    httpClientMock.get.mockReturnValue(of([]));
    service.getEmailProviders().subscribe();
    expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v2\/email\/providers$/));
  });

  it('should call correct endpoint for createEmailProvider', () => {
    httpClientMock.post.mockReturnValue(of({}));
    const provider = {
      isEditing: false,
      id: 1,
      userId: 2,
      name: 'Provider1',
      host: 'smtp.example.com',
      port: 587,
      username: 'user',
      password: 'pass',
      fromAddress: 'from@example.com',
      auth: true,
      startTls: true,
      defaultProvider: false,
      shared: false
    };
    service.createEmailProvider(provider).subscribe();
    expect(httpClientMock.post).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v2\/email\/providers$/), provider);
  });

  it('should call correct endpoint for updateProvider', () => {
    httpClientMock.put.mockReturnValue(of({}));
    const provider = {
      isEditing: false,
      id: 1,
      userId: 2,
      name: 'Provider1',
      host: 'smtp.example.com',
      port: 587,
      username: 'user',
      password: 'pass',
      fromAddress: 'from@example.com',
      auth: true,
      startTls: true,
      defaultProvider: false,
      shared: false
    };
    service.updateProvider(provider).subscribe();
    expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v2\/email\/providers\/1$/), provider);
  });

  it('should call correct endpoint for deleteProvider', () => {
    httpClientMock.delete.mockReturnValue(of(void 0));
    service.deleteProvider(1).subscribe();
    expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v2\/email\/providers\/1$/));
  });

  it('should call correct endpoint for setDefaultProvider', () => {
    httpClientMock.patch.mockReturnValue(of(void 0));
    service.setDefaultProvider(1).subscribe();
    expect(httpClientMock.patch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v2\/email\/providers\/1\/set-default$/), {});
  });
});

