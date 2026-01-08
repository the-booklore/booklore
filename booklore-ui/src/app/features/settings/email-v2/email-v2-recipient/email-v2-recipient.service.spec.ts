import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {EmailV2RecipientService} from './email-v2-recipient.service';
import {EmailRecipient} from '../email-recipient.model';

describe('EmailV2RecipientService', () => {
  let service: EmailV2RecipientService;
  let httpClientMock: any;

  const mockRecipient: EmailRecipient = {
    id: 1,
    email: 'to@example.com',
    name: 'Recipient1',
    defaultRecipient: false,
    isEditing: false
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
        EmailV2RecipientService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    service = TestBed.inject(EmailV2RecipientService);
  });

  it('should get recipients', () => {
    httpClientMock.get.mockReturnValue(of([mockRecipient]));
    service.getRecipients().subscribe(recipients => {
      expect(recipients).toEqual([mockRecipient]);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringContaining('/api/v2/email/recipients'));
    });
  });

  it('should create recipient', () => {
    httpClientMock.post.mockReturnValue(of(mockRecipient));
    service.createRecipient(mockRecipient).subscribe(recipient => {
      expect(recipient).toEqual(mockRecipient);
      expect(httpClientMock.post).toHaveBeenCalledWith(expect.stringContaining('/api/v2/email/recipients'), mockRecipient);
    });
  });

  it('should update recipient', () => {
    httpClientMock.put.mockReturnValue(of(mockRecipient));
    service.updateRecipient(mockRecipient).subscribe(recipient => {
      expect(recipient).toEqual(mockRecipient);
      expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringContaining(`/api/v2/email/recipients/${mockRecipient.id}`), mockRecipient);
    });
  });

  it('should delete recipient', () => {
    httpClientMock.delete.mockReturnValue(of(void 0));
    service.deleteRecipient(1).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringContaining('/api/v2/email/recipients/1'));
    });
  });

  it('should set default recipient', () => {
    httpClientMock.patch.mockReturnValue(of(void 0));
    service.setDefaultRecipient(1).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.patch).toHaveBeenCalledWith(expect.stringContaining('/api/v2/email/recipients/1/set-default'), {});
    });
  });

  it('should handle getRecipients error', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getRecipients().subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle createRecipient error', () => {
    httpClientMock.post.mockReturnValue(throwError(() => new Error('fail')));
    service.createRecipient(mockRecipient).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle updateRecipient error', () => {
    httpClientMock.put.mockReturnValue(throwError(() => new Error('fail')));
    service.updateRecipient(mockRecipient).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle deleteRecipient error', () => {
    httpClientMock.delete.mockReturnValue(throwError(() => new Error('fail')));
    service.deleteRecipient(1).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle setDefaultRecipient error', () => {
    httpClientMock.patch.mockReturnValue(throwError(() => new Error('fail')));
    service.setDefaultRecipient(1).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });
});

describe('EmailV2RecipientService - API Contract Tests', () => {
  let service: EmailV2RecipientService;
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
        EmailV2RecipientService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    service = TestBed.inject(EmailV2RecipientService);
  });

  it('should validate all required EmailRecipient fields exist', () => {
    const requiredFields: (keyof EmailRecipient)[] = [
      'id', 'email', 'name', 'defaultRecipient', 'isEditing'
    ];
    const mockResponse: EmailRecipient = {
      id: 1,
      email: 'to@example.com',
      name: 'Recipient1',
      defaultRecipient: false,
      isEditing: false
    };
    httpClientMock.get.mockReturnValue(of([mockResponse]));
    service.getRecipients().subscribe(recipients => {
      requiredFields.forEach(field => {
        expect(recipients[0]).toHaveProperty(field);
        expect(recipients[0][field]).toBeDefined();
      });
    });
  });

  it('should fail if API returns EmailRecipient without required id field', () => {
    const invalidResponse = [{
      email: 'to@example.com',
      name: 'Recipient1',
      defaultRecipient: false,
      isEditing: false
    }];
    httpClientMock.get.mockReturnValue(of(invalidResponse));
    service.getRecipients().subscribe(recipients => {
      expect(recipients[0]).not.toHaveProperty('id');
    });
  });

  it('should call correct endpoint for getRecipients', () => {
    httpClientMock.get.mockReturnValue(of([]));
    service.getRecipients().subscribe();
    expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v2\/email\/recipients$/));
  });

  it('should call correct endpoint for createRecipient', () => {
    httpClientMock.post.mockReturnValue(of({}));
    const recipient = {
      id: 1,
      email: 'to@example.com',
      name: 'Recipient1',
      defaultRecipient: false,
      isEditing: false
    };
    service.createRecipient(recipient).subscribe();
    expect(httpClientMock.post).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v2\/email\/recipients$/), recipient);
  });

  it('should call correct endpoint for updateRecipient', () => {
    httpClientMock.put.mockReturnValue(of({}));
    const recipient = {
      id: 1,
      email: 'to@example.com',
      name: 'Recipient1',
      defaultRecipient: false,
      isEditing: false
    };
    service.updateRecipient(recipient).subscribe();
    expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v2\/email\/recipients\/1$/), recipient);
  });

  it('should call correct endpoint for deleteRecipient', () => {
    httpClientMock.delete.mockReturnValue(of(void 0));
    service.deleteRecipient(1).subscribe();
    expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v2\/email\/recipients\/1$/));
  });

  it('should call correct endpoint for setDefaultRecipient', () => {
    httpClientMock.patch.mockReturnValue(of(void 0));
    service.setDefaultRecipient(1).subscribe();
    expect(httpClientMock.patch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v2\/email\/recipients\/1\/set-default$/), {});
  });
});

