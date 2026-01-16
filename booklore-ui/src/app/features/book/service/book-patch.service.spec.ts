import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {of, throwError} from 'rxjs';

import {BookPatchService} from './book-patch.service';
import {BookStateService} from './book-state.service';
import {Book, ReadStatus} from '../model/book.model';

describe('BookPatchService', () => {
  let service: BookPatchService;
  let httpMock: any;
  let bookStateServiceMock: any;

  const mockBooks: Book[] = [
    {id: 1, bookType: 'PDF', libraryId: 1, libraryName: 'Lib', dateFinished: undefined} as Book,
    {id: 2, bookType: 'EPUB', libraryId: 1, libraryName: 'Lib', dateFinished: undefined} as Book,
  ];

  beforeEach(() => {
    httpMock = {
      post: vi.fn(),
      put: vi.fn(),
    };
    bookStateServiceMock = {
      getCurrentBookState: vi.fn(),
      updateBookState: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        BookPatchService,
        {provide: HttpClient, useValue: httpMock},
        {provide: BookStateService, useValue: bookStateServiceMock},
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(
      injector,
      () => TestBed.inject(BookPatchService)
    );
  });

  it('should update book shelves and update state', () => {
    const updatedBooks = [{...mockBooks[0], id: 1, libraryName: 'Updated'}];
    httpMock.post.mockReturnValue(of(updatedBooks));
    bookStateServiceMock.getCurrentBookState.mockReturnValue({books: [...mockBooks]});
    service.updateBookShelves(new Set([1]), new Set([2]), new Set([3])).subscribe(result => {
      expect(result).toEqual(updatedBooks);
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalledWith(expect.objectContaining({
        books: expect.arrayContaining([expect.objectContaining({id: 1, libraryName: 'Updated'})])
      }));
    });
  });

  it('should save PDF progress', () => {
    httpMock.post.mockReturnValue(of(void 0));
    service.savePdfProgress(1, 10, 0.5).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpMock.post).toHaveBeenCalledWith(expect.stringContaining('/progress'), {
        bookId: 1,
        pdfProgress: {page: 10, percentage: 0.5}
      });
    });
  });

  it('should save EPUB progress', () => {
    httpMock.post.mockReturnValue(of(void 0));
    service.saveEpubProgress(2, 'cfi123', 'href123', 0.8);
    expect(httpMock.post).toHaveBeenCalledWith(expect.stringContaining('/progress'), {
      bookId: 2,
      epubProgress: {cfi: 'cfi123', href: 'href123', percentage: 0.8}
    });
  });

  it('should save CBX progress', () => {
    httpMock.post.mockReturnValue(of(void 0));
    service.saveCbxProgress(3, 5, 0.2).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpMock.post).toHaveBeenCalledWith(expect.stringContaining('/progress'), {
        bookId: 3,
        cbxProgress: {page: 5, percentage: 0.2}
      });
    });
  });

  it('should update date finished and update state', () => {
    httpMock.post.mockReturnValue(of(void 0));
    bookStateServiceMock.getCurrentBookState.mockReturnValue({books: [...mockBooks]});
    service.updateDateFinished(1, '2023-01-01').subscribe(() => {
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalledWith(expect.objectContaining({
        books: expect.arrayContaining([expect.objectContaining({id: 1, dateFinished: '2023-01-01'})])
      }));
    });
  });

  it('should reset progress and update state', () => {
    const responses = [{bookId: 1, readStatus: ReadStatus.UNREAD, readStatusModifiedTime: 'now', dateFinished: undefined}];
    httpMock.post.mockReturnValue(of(responses));
    bookStateServiceMock.getCurrentBookState.mockReturnValue({books: [...mockBooks]});
    service.resetProgress([1], 'BOOKLORE').subscribe(result => {
      expect(result).toEqual(responses);
      expect(httpMock.post).toHaveBeenCalledWith(
        expect.stringContaining('/reset-progress'),
        [1],
        {params: expect.any(HttpParams)}
      );
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalled();
    });
  });

  it('should update book read status and update state', () => {
    const responses = [{bookId: 2, readStatus: ReadStatus.READING, readStatusModifiedTime: 'now', dateFinished: '2023-01-01'}];
    httpMock.post.mockReturnValue(of(responses));
    bookStateServiceMock.getCurrentBookState.mockReturnValue({books: [...mockBooks]});
    service.updateBookReadStatus([2], ReadStatus.READING).subscribe(result => {
      expect(result).toEqual(responses);
      expect(httpMock.post).toHaveBeenCalledWith(
        expect.stringContaining('/status'),
        {bookIds: [2], status: ReadStatus.READING}
      );
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalled();
    });
  });

  it('should reset personal rating and update state', () => {
    const responses = [{bookId: 1, personalRating: null}];
    httpMock.post.mockReturnValue(of(responses));
    bookStateServiceMock.getCurrentBookState.mockReturnValue({books: [...mockBooks]});
    service.resetPersonalRating([1]).subscribe(result => {
      expect(result).toEqual(responses);
      expect(httpMock.post).toHaveBeenCalledWith(expect.stringContaining('/reset-personal-rating'), [1]);
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalled();
    });
  });

  it('should update personal rating and update state', () => {
    const responses = [{bookId: 2, personalRating: 5}];
    httpMock.put.mockReturnValue(of(responses));
    bookStateServiceMock.getCurrentBookState.mockReturnValue({books: [...mockBooks]});
    service.updatePersonalRating([2], 5).subscribe(result => {
      expect(result).toEqual(responses);
      expect(httpMock.put).toHaveBeenCalledWith(expect.stringContaining('/personal-rating'), {ids: [2], rating: 5});
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalled();
    });
  });

  it('should update last read time in state', () => {
    const now = new Date().toISOString();
    vi.useFakeTimers().setSystemTime(new Date(now));
    bookStateServiceMock.getCurrentBookState.mockReturnValue({books: [...mockBooks]});
    service.updateLastReadTime(1);
    expect(bookStateServiceMock.updateBookState).toHaveBeenCalledWith(expect.objectContaining({
      books: expect.arrayContaining([expect.objectContaining({id: 1, lastReadTime: now})])
    }));
    vi.useRealTimers();
  });

  it('should handle errors from http.post gracefully', () => {
    httpMock.post.mockReturnValue(throwError(() => new Error('fail')));
    service.savePdfProgress(1, 1, 1).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });
});

