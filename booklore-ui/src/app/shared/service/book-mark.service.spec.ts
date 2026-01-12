import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {BookMarkService, BookMark, CreateBookMarkRequest, UpdateBookMarkRequest} from './book-mark.service';

describe('BookMarkService', () => {
  let service: BookMarkService;
  let httpClientMock: any;

  const mockBookmark: BookMark = {
    id: 1,
    userId: 2,
    bookId: 3,
    cfi: 'epubcfi(/6/2[cover]!/6)',
    title: 'Bookmark 1',
    color: 'yellow',
    notes: 'note',
    priority: 1,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T01:00:00Z'
  };

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        BookMarkService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    service = TestBed.inject(BookMarkService);
  });

  it('should get bookmarks for a book', () => {
    httpClientMock.get.mockReturnValue(of([mockBookmark]));
    service.getBookmarksForBook(3).subscribe(bookmarks => {
      expect(bookmarks).toEqual([mockBookmark]);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringContaining('/book/3'));
    });
  });

  it('should create a bookmark', () => {
    httpClientMock.post.mockReturnValue(of(mockBookmark));
    const req: CreateBookMarkRequest = {bookId: 3, cfi: 'epubcfi(/6/2[cover]!/6)', title: 'Bookmark 1'};
    service.createBookmark(req).subscribe(bm => {
      expect(bm).toEqual(mockBookmark);
      expect(httpClientMock.post).toHaveBeenCalledWith(expect.any(String), req);
    });
  });

  it('should update a bookmark', () => {
    httpClientMock.put.mockReturnValue(of(mockBookmark));
    const req: UpdateBookMarkRequest = {title: 'Updated', notes: 'Updated note'};
    service.updateBookmark(1, req).subscribe(bm => {
      expect(bm).toEqual(mockBookmark);
      expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringContaining('/1'), req);
    });
  });

  it('should delete a bookmark', () => {
    httpClientMock.delete.mockReturnValue(of(void 0));
    service.deleteBookmark(1).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringContaining('/1'));
    });
  });

  it('should handle getBookmarksForBook error', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getBookmarksForBook(3).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
      }
    });
  });

  it('should handle createBookmark error', () => {
    httpClientMock.post.mockReturnValue(throwError(() => new Error('fail')));
    service.createBookmark({bookId: 3, cfi: 'cfi'}).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
      }
    });
  });

  it('should handle updateBookmark error', () => {
    httpClientMock.put.mockReturnValue(throwError(() => new Error('fail')));
    service.updateBookmark(1, {title: 'fail'}).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
      }
    });
  });

  it('should handle deleteBookmark error', () => {
    httpClientMock.delete.mockReturnValue(throwError(() => new Error('fail')));
    service.deleteBookmark(1).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
      }
    });
  });
});

describe('BookMarkService - API Contract Tests', () => {
  let service: BookMarkService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        BookMarkService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    service = TestBed.inject(BookMarkService);
  });

  describe('BookMark interface contract', () => {
    it('should validate all required BookMark fields exist', () => {
      const requiredFields: (keyof BookMark)[] = [
        'id', 'bookId', 'cfi', 'title', 'createdAt'
      ];
      const mockResponse: BookMark = {
        id: 1,
        bookId: 2,
        cfi: 'cfi',
        title: 't',
        createdAt: '2024-01-01T00:00:00Z'
      };
      httpClientMock.get.mockReturnValue(of([mockResponse]));
      service.getBookmarksForBook(2).subscribe(bookmarks => {
        requiredFields.forEach(field => {
          expect(bookmarks[0]).toHaveProperty(field);
          expect(bookmarks[0][field]).toBeDefined();
        });
      });
    });
  });

  describe('HTTP endpoint contract', () => {
    it('should call correct endpoint for getBookmarksForBook', () => {
      httpClientMock.get.mockReturnValue(of([]));
      service.getBookmarksForBook(5).subscribe();
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/bookmarks\/book\/5$/));
    });

    it('should call correct endpoint for createBookmark', () => {
      httpClientMock.post.mockReturnValue(of({}));
      const req: CreateBookMarkRequest = {bookId: 2, cfi: 'cfi', title: 't'};
      service.createBookmark(req).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/bookmarks$/),
        req
      );
    });

    it('should call correct endpoint for updateBookmark', () => {
      httpClientMock.put.mockReturnValue(of({}));
      const req: UpdateBookMarkRequest = {title: 't'};
      service.updateBookmark(7, req).subscribe();
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/bookmarks\/7$/),
        req
      );
    });

    it('should call correct endpoint for deleteBookmark', () => {
      httpClientMock.delete.mockReturnValue(of(void 0));
      service.deleteBookmark(8).subscribe();
      expect(httpClientMock.delete).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/bookmarks\/8$/)
      );
    });
  });

  describe('Request payload contract', () => {
    it('should send correct structure for createBookmark', () => {
      httpClientMock.post.mockReturnValue(of({}));
      const req: CreateBookMarkRequest = {bookId: 2, cfi: 'cfi', title: 't'};
      service.createBookmark(req).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(expect.any(String), req);
    });

    it('should send correct structure for updateBookmark', () => {
      httpClientMock.put.mockReturnValue(of({}));
      const req: UpdateBookMarkRequest = {title: 't', notes: 'n'};
      service.updateBookmark(9, req).subscribe();
      expect(httpClientMock.put).toHaveBeenCalledWith(expect.any(String), req);
    });
  });

  describe('Response type contract', () => {
    it('should expect BookMark[] from getBookmarksForBook', () => {
      const mockBookmarks: BookMark[] = [{
        id: 1,
        bookId: 2,
        cfi: 'cfi',
        title: 't',
        createdAt: '2024-01-01T00:00:00Z'
      }];
      httpClientMock.get.mockReturnValue(of(mockBookmarks));
      service.getBookmarksForBook(2).subscribe(bookmarks => {
        expect(Array.isArray(bookmarks)).toBe(true);
        expect(bookmarks[0]).toHaveProperty('id');
        expect(bookmarks[0]).toHaveProperty('bookId');
      });
    });

    it('should expect BookMark from createBookmark', () => {
      const mockBookmark: BookMark = {
        id: 1,
        bookId: 2,
        cfi: 'cfi',
        title: 't',
        createdAt: '2024-01-01T00:00:00Z'
      };
      httpClientMock.post.mockReturnValue(of(mockBookmark));
      service.createBookmark({bookId: 2, cfi: 'cfi', title: 't'}).subscribe(bm => {
        expect(bm).toHaveProperty('id');
        expect(bm).toHaveProperty('bookId');
      });
    });

    it('should expect BookMark from updateBookmark', () => {
      const mockBookmark: BookMark = {
        id: 1,
        bookId: 2,
        cfi: 'cfi',
        title: 't',
        createdAt: '2024-01-01T00:00:00Z'
      };
      httpClientMock.put.mockReturnValue(of(mockBookmark));
      service.updateBookmark(1, {title: 't'}).subscribe(bm => {
        expect(bm).toHaveProperty('id');
        expect(bm).toHaveProperty('bookId');
      });
    });

    it('should expect void from deleteBookmark', () => {
      httpClientMock.delete.mockReturnValue(of(void 0));
      service.deleteBookmark(1).subscribe(result => {
        expect(result).toBeUndefined();
      });
    });
  });
});

