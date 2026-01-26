import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {FileMoveRequest, FileOperationsService} from './file-operations.service';

describe('FileOperationsService', () => {
  let service: FileOperationsService;
  let httpClientMock: any;

  const mockRequest: FileMoveRequest = {
    bookIds: [1, 2],
    moves: [
      {bookId: 1, targetLibraryId: 10, targetLibraryPathId: 100},
      {bookId: 2, targetLibraryId: 20, targetLibraryPathId: 200}
    ]
  };

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        FileOperationsService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(FileOperationsService));
  });

  it('should move files and return void', () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    service.moveFiles(mockRequest).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/files\/move$/),
        mockRequest
      );
    });
  });

  it('should handle move files error', () => {
    httpClientMock.post.mockReturnValue(throwError(() => new Error('fail')));
    service.moveFiles(mockRequest).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });
});

describe('FileOperationsService - API Contract Tests', () => {
  let service: FileOperationsService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        FileOperationsService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(FileOperationsService));
  });

  describe('FileMoveRequest interface contract', () => {
    it('should have required fields', () => {
      const req: FileMoveRequest = {
        bookIds: [1],
        moves: [{bookId: 1, targetLibraryId: 2, targetLibraryPathId: 3}]
      };
      expect(req).toHaveProperty('bookIds');
      expect(req).toHaveProperty('moves');
      expect(Array.isArray(req.bookIds)).toBe(true);
      expect(Array.isArray(req.moves)).toBe(true);
      expect(req.moves[0]).toHaveProperty('bookId');
      expect(req.moves[0]).toHaveProperty('targetLibraryId');
      expect(req.moves[0]).toHaveProperty('targetLibraryPathId');
    });
  });

  describe('HTTP endpoint contract', () => {
    it('should call correct endpoint for moveFiles', () => {
      httpClientMock.post.mockReturnValue(of(void 0));
      const req: FileMoveRequest = {
        bookIds: [1],
        moves: [{bookId: 1, targetLibraryId: 2, targetLibraryPathId: 3}]
      };
      service.moveFiles(req).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/files\/move$/),
        req
      );
    });
  });

  describe('Request payload contract', () => {
    it('should send FileMoveRequest with correct structure', () => {
      httpClientMock.post.mockReturnValue(of(void 0));
      const req: FileMoveRequest = {
        bookIds: [1, 2],
        moves: [
          {bookId: 1, targetLibraryId: 10, targetLibraryPathId: 100},
          {bookId: 2, targetLibraryId: 20, targetLibraryPathId: 200}
        ]
      };
      service.moveFiles(req).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.any(String),
        req
      );
    });
  });

  describe('Response type contract', () => {
    it('should expect void from moveFiles', () => {
      httpClientMock.post.mockReturnValue(of(void 0));
      const req: FileMoveRequest = {
        bookIds: [1],
        moves: [{bookId: 1, targetLibraryId: 2, targetLibraryPathId: 3}]
      };
      service.moveFiles(req).subscribe(result => {
        expect(result).toBeUndefined();
      });
    });
  });
});
