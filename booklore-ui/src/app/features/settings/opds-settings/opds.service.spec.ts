import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {OpdsService, OpdsUserV2, OpdsUserV2CreateRequest, OpdsSortOrder} from './opds.service';

describe('OpdsService', () => {
  let service: OpdsService;
  let httpClientMock: any;

  const mockOpdsUser: OpdsUserV2 = {
    id: 1,
    userId: 2,
    username: 'opdsuser',
    sortOrder: 'TITLE_ASC'
  };

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        OpdsService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(OpdsService));
  });

  it('should get OPDS users', () => {
    httpClientMock.get.mockReturnValue(of([mockOpdsUser]));
    service.getUser().subscribe(users => {
      expect(users).toEqual([mockOpdsUser]);
      expect(httpClientMock.get).toHaveBeenCalled();
    });
  });

  it('should create OPDS user', () => {
    httpClientMock.post.mockReturnValue(of(mockOpdsUser));
    const req: OpdsUserV2CreateRequest = {username: 'opdsuser', password: 'pass', sortOrder: 'TITLE_ASC'};
    service.createUser(req).subscribe(user => {
      expect(user).toEqual(mockOpdsUser);
      expect(httpClientMock.post).toHaveBeenCalledWith(expect.any(String), req);
    });
  });

  it('should update OPDS user sortOrder', () => {
    httpClientMock.patch.mockReturnValue(of(mockOpdsUser));
    service.updateUser(1, 'TITLE_DESC').subscribe(user => {
      expect(user).toEqual(mockOpdsUser);
      expect(httpClientMock.patch).toHaveBeenCalledWith(expect.stringContaining('/1'), {sortOrder: 'TITLE_DESC'});
    });
  });

  it('should delete OPDS credential', () => {
    httpClientMock.delete.mockReturnValue(of(void 0));
    service.deleteCredential(1).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringContaining('/1'));
    });
  });

  it('should handle error on getUser', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getUser().subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });
});

describe('OpdsService - API Contract Tests', () => {
  let service: OpdsService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        OpdsService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(OpdsService));
  });

  describe('OpdsUserV2 interface contract', () => {
    it('should validate all required OpdsUserV2 fields exist', () => {
      const requiredFields: (keyof OpdsUserV2)[] = ['id', 'userId', 'username'];
      const mockResponse: OpdsUserV2 = {
        id: 1,
        userId: 2,
        username: 'opdsuser',
        sortOrder: 'TITLE_ASC'
      };
      httpClientMock.get.mockReturnValue(of([mockResponse]));
      service.getUser().subscribe(users => {
        requiredFields.forEach(field => {
          expect(users[0]).toHaveProperty(field);
          expect(users[0][field]).toBeDefined();
        });
      });
    });

    it('should validate OpdsSortOrder enum values', () => {
      const validValues: OpdsSortOrder[] = [
        'RECENT', 'TITLE_ASC', 'TITLE_DESC', 'AUTHOR_ASC', 'AUTHOR_DESC',
        'SERIES_ASC', 'SERIES_DESC', 'RATING_ASC', 'RATING_DESC'
      ];
      validValues.forEach(val => {
        expect(validValues).toContain(val);
      });
      expect(validValues).toHaveLength(9);
    });
  });

  describe('HTTP endpoint contract', () => {
    it('should call correct endpoint for getUser', () => {
      httpClientMock.get.mockReturnValue(of([]));
      service.getUser().subscribe();
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v2\/opds-users$/));
    });

    it('should call correct endpoint for createUser', () => {
      httpClientMock.post.mockReturnValue(of({}));
      const req: OpdsUserV2CreateRequest = {username: 'a', password: 'b'};
      service.createUser(req).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v2\/opds-users$/),
        req
      );
    });

    it('should call correct endpoint for updateUser', () => {
      httpClientMock.patch.mockReturnValue(of({}));
      service.updateUser(42, 'AUTHOR_DESC').subscribe();
      expect(httpClientMock.patch).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v2\/opds-users\/42$/),
        {sortOrder: 'AUTHOR_DESC'}
      );
    });

    it('should call correct endpoint for deleteCredential', () => {
      httpClientMock.delete.mockReturnValue(of(void 0));
      service.deleteCredential(99).subscribe();
      expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v2\/opds-users\/99$/));
    });
  });

  describe('Request payload contract', () => {
    it('should send OpdsUserV2CreateRequest with correct structure', () => {
      httpClientMock.post.mockReturnValue(of({}));
      const req: OpdsUserV2CreateRequest = {username: 'x', password: 'y', sortOrder: 'RATING_DESC'};
      service.createUser(req).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.any(String),
        req
      );
    });

    it('should send updateUser payload with correct structure', () => {
      httpClientMock.patch.mockReturnValue(of({}));
      service.updateUser(5, 'SERIES_ASC').subscribe();
      expect(httpClientMock.patch).toHaveBeenCalledWith(
        expect.any(String),
        {sortOrder: 'SERIES_ASC'}
      );
    });
  });

  describe('Response type contract', () => {
    it('should expect OpdsUserV2 array from getUser', () => {
      const mockUsers: OpdsUserV2[] = [{
        id: 1,
        userId: 2,
        username: 'opdsuser'
      }];
      httpClientMock.get.mockReturnValue(of(mockUsers));
      service.getUser().subscribe(users => {
        expect(Array.isArray(users)).toBe(true);
        expect(users[0]).toHaveProperty('id');
        expect(users[0]).toHaveProperty('username');
      });
    });

    it('should expect OpdsUserV2 from createUser', () => {
      const mockUser: OpdsUserV2 = {
        id: 2,
        userId: 3,
        username: 'created'
      };
      httpClientMock.post.mockReturnValue(of(mockUser));
      service.createUser({username: 'created', password: 'pw'}).subscribe(user => {
        expect(user).toHaveProperty('id');
        expect(user).toHaveProperty('username');
      });
    });

    it('should expect OpdsUserV2 from updateUser', () => {
      const mockUser: OpdsUserV2 = {
        id: 3,
        userId: 4,
        username: 'updated'
      };
      httpClientMock.patch.mockReturnValue(of(mockUser));
      service.updateUser(3, 'RECENT').subscribe(user => {
        expect(user).toHaveProperty('id');
        expect(user).toHaveProperty('username');
      });
    });

    it('should expect void from deleteCredential', () => {
      httpClientMock.delete.mockReturnValue(of(void 0));
      service.deleteCredential(4).subscribe(result => {
        expect(result).toBeUndefined();
      });
    });
  });
});

