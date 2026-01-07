import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, of, throwError} from 'rxjs';
import {BookService} from './book.service';
import {BookStateService} from './book-state.service';
import {BookSocketService} from './book-socket.service';
import {BookPatchService} from './book-patch.service';
import {MessageService} from 'primeng/api';
import {AuthService} from '../../../shared/service/auth.service';
import {FileDownloadService} from '../../../shared/service/file-download.service';
import {Router} from '@angular/router';
import {AdditionalFileType, Book, BookMetadata, ReadStatus} from '../model/book.model';

describe('BookService', () => {
  let service: BookService;
  let httpMock: any;
  let messageServiceMock: any;
  let authServiceMock: any;
  let fileDownloadServiceMock: any;
  let routerMock: any;
  let bookStateServiceMock: any;
  let bookSocketServiceMock: any;
  let bookPatchServiceMock: any;

  beforeEach(() => {
    httpMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn()
    };
    messageServiceMock = {add: vi.fn()};
    authServiceMock = {token$: of('token')};
    fileDownloadServiceMock = {downloadFile: vi.fn()};
    routerMock = {navigate: vi.fn()};
    bookStateServiceMock = {
      bookState$: of({books: [{id: 1, bookType: 'PDF', shelves: [], metadata: {seriesName: 'S'}}], loaded: true, error: null}),
      getCurrentBookState: vi.fn(),
      updateBookState: vi.fn(),
      resetBookState: vi.fn()
    };
    bookSocketServiceMock = {
      handleNewlyCreatedBook: vi.fn(),
      handleRemovedBookIds: vi.fn(),
      handleBookUpdate: vi.fn(),
      handleMultipleBookUpdates: vi.fn(),
      handleBookMetadataUpdate: vi.fn(),
      handleMultipleBookCoverPatches: vi.fn()
    };
    bookPatchServiceMock = {
      updateBookShelves: vi.fn(),
      updateLastReadTime: vi.fn(),
      savePdfProgress: vi.fn(),
      saveEpubProgress: vi.fn(),
      saveCbxProgress: vi.fn(),
      updateDateFinished: vi.fn(),
      resetProgress: vi.fn(),
      updateBookReadStatus: vi.fn(),
      resetPersonalRating: vi.fn(),
      updatePersonalRating: vi.fn()
    };

    bookStateServiceMock.getCurrentBookState.mockReturnValue({
      books: [
        {id: 1, bookType: 'PDF', shelves: [{id: 10}], metadata: {seriesName: 'S'}, alternativeFormats: [], supplementaryFiles: [], fileName: 'file.pdf'},
        {id: 2, bookType: 'EPUB', shelves: [], metadata: {}, alternativeFormats: [], supplementaryFiles: [], fileName: 'file2.epub'}
      ],
      loaded: true,
      error: null
    });

    TestBed.configureTestingModule({
      providers: [
        BookService,
        {provide: HttpClient, useValue: httpMock},
        {provide: MessageService, useValue: messageServiceMock},
        {provide: AuthService, useValue: authServiceMock},
        {provide: FileDownloadService, useValue: fileDownloadServiceMock},
        {provide: Router, useValue: routerMock},
        {provide: BookStateService, useValue: bookStateServiceMock},
        {provide: BookSocketService, useValue: bookSocketServiceMock},
        {provide: BookPatchService, useValue: bookPatchServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(BookService));
  });

  describe('State Management', () => {
    it('should get current book state', () => {
      expect(service.getCurrentBookState()).toEqual(bookStateServiceMock.getCurrentBookState());
    });

    it('should fetch books and update state', async () => {
      const books = [{id: 1}];
      httpMock.get.mockReturnValue(of(books));
      await firstValueFrom(service['fetchBooks']());
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalledWith({
        books,
        loaded: true,
        error: null
      });
    });

    it('should handle fetchBooks error', async () => {
      httpMock.get.mockReturnValue(throwError(() => ({message: 'fail'})));
      bookStateServiceMock.getCurrentBookState.mockReturnValue({books: [], loaded: false, error: null});
      await expect(firstValueFrom(service['fetchBooks']())).rejects.toBeTruthy();
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalledWith({
        books: [],
        loaded: true,
        error: 'fail'
      });
    });

    it('should refreshBooks and update state', () => {
      httpMock.get.mockReturnValue(of([{id: 1}]));
      service.refreshBooks();
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalledWith({
        books: [{id: 1}],
        loaded: true,
        error: null
      });
    });

    it('should handle refreshBooks error', () => {
      httpMock.get.mockReturnValue(throwError(() => ({message: 'fail'})));
      service.refreshBooks();
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalledWith({
        books: null,
        loaded: true,
        error: 'fail'
      });
    });

    it('should remove books by library id', () => {
      bookStateServiceMock.getCurrentBookState.mockReturnValue({
        books: [{id: 1, libraryId: 2}, {id: 2, libraryId: 3}],
        loaded: true,
        error: null
      });
      service.removeBooksByLibraryId(2);
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalledWith({
        books: [{id: 2, libraryId: 3}],
        loaded: true,
        error: null
      });
    });

    it('should remove books from shelf', () => {
      bookStateServiceMock.getCurrentBookState.mockReturnValue({
        books: [{id: 1, shelves: [{id: 10}, {id: 20}]}, {id: 2, shelves: [{id: 20}]}],
        loaded: true,
        error: null
      });
      service.removeBooksFromShelf(20);
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalled();
    });
  });

  describe('Book Retrieval', () => {
    it('should get book by id from state', () => {
      expect(service.getBookByIdFromState(1)).toEqual(expect.objectContaining({id: 1}));
      expect(service.getBookByIdFromState(999)).toBeUndefined();
    });

    it('should get books by ids from state', () => {
      expect(service.getBooksByIdsFromState([1, 2])).toHaveLength(2);
      expect(service.getBooksByIdsFromState([])).toEqual([]);
    });

    it('should get book by id from API', async () => {
      httpMock.get.mockReturnValue(of({id: 1}));
      const result = await firstValueFrom(service.getBookByIdFromAPI(1, true));
      expect(result).toEqual({id: 1});
    });

    it('should get books in series', async () => {
      const result = await firstValueFrom(service.getBooksInSeries(1));
      expect(result).toEqual([expect.objectContaining({id: 1})]);
    });

    it('should get book recommendations', async () => {
      httpMock.get.mockReturnValue(of([{id: 1}]));
      const result = await firstValueFrom(service.getBookRecommendations(1));
      expect(result).toEqual([{id: 1}]);
    });
  });

  describe('Book Operations', () => {
    it('should delete books and update state', async () => {
      httpMock.delete.mockReturnValue(of({failedFileDeletions: []}));
      bookStateServiceMock.getCurrentBookState.mockReturnValue({
        books: [{id: 1}, {id: 2}],
        loaded: true,
        error: null
      });
      await firstValueFrom(service.deleteBooks(new Set([1])));
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalled();
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should show warning if some files could not be deleted', async () => {
      httpMock.delete.mockReturnValue(of({failedFileDeletions: ['file.pdf']}));
      bookStateServiceMock.getCurrentBookState.mockReturnValue({
        books: [{id: 1, fileName: 'file.pdf'}],
        loaded: true,
        error: null
      });
      await firstValueFrom(service.deleteBooks(new Set([1])));
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'warn'}));
    });

    it('should handle deleteBooks error', async () => {
      httpMock.delete.mockReturnValue(throwError(() => ({error: {message: 'fail'}})));
      await expect(firstValueFrom(service.deleteBooks(new Set([1])))).rejects.toBeTruthy();
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });

    it('should update book shelves', async () => {
      bookPatchServiceMock.updateBookShelves.mockReturnValue(of([{id: 1}]));
      const result = await firstValueFrom(service.updateBookShelves(new Set([1]), new Set([2]), new Set([3])));
      expect(result).toEqual([{id: 1}]);
    });

    it('should handle updateBookShelves error', async () => {
      bookPatchServiceMock.updateBookShelves.mockReturnValue(throwError(() => ({message: 'fail'})));
      await expect(firstValueFrom(service.updateBookShelves(new Set([1]), new Set([2]), new Set([3])))).rejects.toBeTruthy();
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalled();
    });
  });

  describe('Reading & Viewer Settings', () => {
    it('should navigate to correct reader and update last read time', () => {
      service.readBook(1, 'ngx');
      expect(routerMock.navigate).toHaveBeenCalledWith(['/pdf-reader/book/1']);
      expect(bookPatchServiceMock.updateLastReadTime).toHaveBeenCalledWith(1);
    });

    it('should not navigate if book not found', () => {
      bookStateServiceMock.getCurrentBookState.mockReturnValue({books: [], loaded: true, error: null});
      service.readBook(999, 'ngx');
      expect(routerMock.navigate).not.toHaveBeenCalled();
    });

    it('should get book setting', async () => {
      httpMock.get.mockReturnValue(of({fontSize: 12}));
      const result = await firstValueFrom(service.getBookSetting(1));
      expect(result).toEqual({fontSize: 12});
    });

    it('should update viewer setting', async () => {
      httpMock.put.mockReturnValue(of(void 0));
      const result = await firstValueFrom(service.updateViewerSetting({fontSize: 12}, 1));
      expect(result).toBeUndefined();
    });
  });

  describe('File Operations', () => {
    it('should get file content', async () => {
      httpMock.get.mockReturnValue(of(new Blob(['abc'])));
      const result = await firstValueFrom(service.getFileContent(1));
      expect(result).toBeInstanceOf(Blob);
    });

    it('should download file', () => {
      const book = {id: 1, fileName: 'file.pdf'};
      service.downloadFile(book as Book);
      expect(fileDownloadServiceMock.downloadFile).toHaveBeenCalled();
    });

    it('should delete additional file and update state', async () => {
      httpMock.delete.mockReturnValue(of(void 0));
      bookStateServiceMock.getCurrentBookState.mockReturnValue({
        books: [{id: 1, alternativeFormats: [{id: 2}], supplementaryFiles: [{id: 3}]}],
        loaded: true,
        error: null
      });
      await firstValueFrom(service.deleteAdditionalFile(1, 2));
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalled();
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should handle deleteAdditionalFile error', async () => {
      httpMock.delete.mockReturnValue(throwError(() => ({error: {message: 'fail'}})));
      await expect(firstValueFrom(service.deleteAdditionalFile(1, 2))).rejects.toBeTruthy();
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });

    it('should upload additional file and update state', async () => {
      httpMock.post.mockReturnValue(of({id: 2}));
      bookStateServiceMock.getCurrentBookState.mockReturnValue({
        books: [{id: 1, alternativeFormats: [], supplementaryFiles: []}],
        loaded: true,
        error: null
      });
      const file = new File(['abc'], 'file.txt');
      await firstValueFrom(service.uploadAdditionalFile(1, file, AdditionalFileType.ALTERNATIVE_FORMAT));
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalled();
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should handle uploadAdditionalFile error', async () => {
      httpMock.post.mockReturnValue(throwError(() => ({error: {message: 'fail'}})));
      const file = new File(['abc'], 'file.txt');
      await expect(firstValueFrom(service.uploadAdditionalFile(1, file, AdditionalFileType.ALTERNATIVE_FORMAT))).rejects.toBeTruthy();
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });

    it('should download additional file', () => {
      const book = {id: 1, alternativeFormats: [{id: 2, fileName: 'f.txt'}]};
      service.downloadAdditionalFile(book as Book, 2);
      expect(fileDownloadServiceMock.downloadFile).toHaveBeenCalled();
    });
  });

  describe('Progress & Status Tracking', () => {
    it('should update last read time', () => {
      service.updateLastReadTime(1);
      expect(bookPatchServiceMock.updateLastReadTime).toHaveBeenCalledWith(1);
    });

    it('should save pdf progress', async () => {
      bookPatchServiceMock.savePdfProgress.mockReturnValue(of(void 0));
      const result = await firstValueFrom(service.savePdfProgress(1, 2, 0.5));
      expect(result).toBeUndefined();
    });

    it('should save epub progress', async () => {
      bookPatchServiceMock.saveEpubProgress.mockReturnValue(of(void 0));
      const result = await firstValueFrom(service.saveEpubProgress(1, 'cfi', 0.5));
      expect(result).toBeUndefined();
    });

    it('should save cbx progress', async () => {
      bookPatchServiceMock.saveCbxProgress.mockReturnValue(of(void 0));
      const result = await firstValueFrom(service.saveCbxProgress(1, 2, 0.5));
      expect(result).toBeUndefined();
    });

    it('should update date finished', async () => {
      bookPatchServiceMock.updateDateFinished.mockReturnValue(of(void 0));
      const result = await firstValueFrom(service.updateDateFinished(1, '2020-01-01'));
      expect(result).toBeUndefined();
    });

    it('should reset progress', async () => {
      bookPatchServiceMock.resetProgress.mockReturnValue(of([{bookId: 1, readStatus: ReadStatus.READ, readStatusModifiedTime: ''}]));
      const result = await firstValueFrom(service.resetProgress([1], 'BOOKLORE'));
      expect(result).toEqual([{bookId: 1, readStatus: ReadStatus.READ, readStatusModifiedTime: ''}]);
    });

    it('should update book read status', async () => {
      bookPatchServiceMock.updateBookReadStatus.mockReturnValue(of([{bookId: 1, readStatus: ReadStatus.READ, readStatusModifiedTime: ''}]));
      const result = await firstValueFrom(service.updateBookReadStatus([1], ReadStatus.READ));
      expect(result).toEqual([{bookId: 1, readStatus: ReadStatus.READ, readStatusModifiedTime: ''}]);
    });
  });

  describe('Personal Rating', () => {
    it('should reset personal rating', async () => {
      bookPatchServiceMock.resetPersonalRating.mockReturnValue(of([{bookId: 1}]));
      const result = await firstValueFrom(service.resetPersonalRating([1]));
      expect(result).toEqual([{bookId: 1}]);
    });

    it('should update personal rating', async () => {
      bookPatchServiceMock.updatePersonalRating.mockReturnValue(of([{bookId: 1, personalRating: 5}]));
      const result = await firstValueFrom(service.updatePersonalRating([1], 5));
      expect(result).toEqual([{bookId: 1, personalRating: 5}]);
    });
  });

  describe('Metadata Operations', () => {

    it('should update book metadata', async () => {
      httpMock.put.mockReturnValue(of({bookId: 1}));
      service.handleBookMetadataUpdate = vi.fn();
      const result = await firstValueFrom(service.updateBookMetadata(1, {} as any, true));
      expect(result).toEqual({bookId: 1});
      expect(service.handleBookMetadataUpdate).toHaveBeenCalledWith(1, {bookId: 1});
    });

    it('should update books metadata', async () => {
      httpMock.put.mockReturnValue(of(void 0));
      const result = await firstValueFrom(service.updateBooksMetadata({} as any));
      expect(result).toBeUndefined();
    });

    it('should toggle all lock', async () => {
      httpMock.put.mockReturnValue(of([{bookId: 1}]));
      bookStateServiceMock.getCurrentBookState.mockReturnValue({
        books: [{id: 1, metadata: {bookId: 1}}],
        loaded: true,
        error: null
      });
      const result = await firstValueFrom(service.toggleAllLock(new Set([1]), 'lock'));
      expect(result).toBeUndefined();
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalled();
    });

    it('should handle toggle all lock error', async () => {
      httpMock.put.mockReturnValue(throwError(() => ({message: 'fail'})));
      await expect(firstValueFrom(service.toggleAllLock(new Set([1]), 'lock'))).rejects.toBeTruthy();
    });

    it('should toggle field locks', async () => {
      httpMock.put.mockReturnValue(of(void 0));
      bookStateServiceMock.getCurrentBookState.mockReturnValue({
        books: [{id: 1, metadata: {}}],
        loaded: true,
        error: null
      });
      const result = await firstValueFrom(service.toggleFieldLocks([1], {title: 'LOCK'}));
      expect(result).toBeUndefined();
      expect(bookStateServiceMock.updateBookState).toHaveBeenCalled();
    });

    it('should handle toggle field locks error', async () => {
      httpMock.put.mockReturnValue(throwError(() => ({message: 'fail'})));
      await expect(firstValueFrom(service.toggleFieldLocks([1], {title: 'LOCK'}))).rejects.toBeTruthy();
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });

    it('should consolidate metadata', async () => {
      httpMock.post.mockReturnValue(of({}));
      service.refreshBooks = vi.fn();
      await firstValueFrom(service.consolidateMetadata('authors', ['a'], ['b']));
      expect(service.refreshBooks).toHaveBeenCalled();
    });

    it('should delete metadata', async () => {
      httpMock.post.mockReturnValue(of({}));
      service.refreshBooks = vi.fn();
      await firstValueFrom(service.deleteMetadata('authors', ['a']));
      expect(service.refreshBooks).toHaveBeenCalled();
    });
  });

  describe('Cover Operations', () => {
    it('should get upload cover url', () => {
      expect(service.getUploadCoverUrl(1)).toContain('/1/metadata/cover/upload');
    });

    it('should upload cover from url and handleBookMetadataUpdate', async () => {
      httpMock.post.mockReturnValue(of({bookId: 1}));
      service.handleBookMetadataUpdate = vi.fn();
      const result = await firstValueFrom(service.uploadCoverFromUrl(1, 'url'));
      expect(result).toEqual({bookId: 1});
      expect(service.handleBookMetadataUpdate).toHaveBeenCalledWith(1, {bookId: 1});
    });

    it('should regenerate covers', async () => {
      httpMock.post.mockReturnValue(of(void 0));
      const result = await firstValueFrom(service.regenerateCovers());
      expect(result).toBeUndefined();
    });

    it('should regenerate cover', async () => {
      httpMock.post.mockReturnValue(of(void 0));
      const result = await firstValueFrom(service.regenerateCover(1));
      expect(result).toBeUndefined();
    });

    it('should generate custom cover', async () => {
      httpMock.post.mockReturnValue(of(void 0));
      const result = await firstValueFrom(service.generateCustomCover(1));
      expect(result).toBeUndefined();
    });

    it('should regenerate covers for books', async () => {
      httpMock.post.mockReturnValue(of(void 0));
      const result = await firstValueFrom(service.regenerateCoversForBooks([1, 2]));
      expect(result).toBeUndefined();
    });

    it('should bulk upload cover', async () => {
      httpMock.post.mockReturnValue(of(void 0));
      const file = new File(['abc'], 'cover.jpg');
      const result = await firstValueFrom(service.bulkUploadCover([1, 2], file));
      expect(result).toBeUndefined();
    });
  });

  describe('Websocket Handlers', () => {
    it('should handle newly created book', () => {
      const book = {id: 1};
      service.handleNewlyCreatedBook(book as Book);
      expect(bookSocketServiceMock.handleNewlyCreatedBook).toHaveBeenCalledWith(book);
    });

    it('should handle removed book ids', () => {
      service.handleRemovedBookIds([1, 2]);
      expect(bookSocketServiceMock.handleRemovedBookIds).toHaveBeenCalledWith([1, 2]);
    });

    it('should handle book update', () => {
      const book = {id: 1};
      service.handleBookUpdate(book as Book);
      expect(bookSocketServiceMock.handleBookUpdate).toHaveBeenCalledWith(book);
    });

    it('should handle multiple book updates', () => {
      const books = [{id: 1}, {id: 2}];
      service.handleMultipleBookUpdates(books as Book[]);
      expect(bookSocketServiceMock.handleMultipleBookUpdates).toHaveBeenCalledWith(books);
    });

    it('should handle book metadata update', () => {
      service.handleBookMetadataUpdate(1, {bookId: 1} as BookMetadata);
      expect(bookSocketServiceMock.handleBookMetadataUpdate).toHaveBeenCalledWith(1, {bookId: 1});
    });

    it('should handle multiple book cover patches', () => {
      const patches = [{id: 1, coverUpdatedOn: 'now'}];
      service.handleMultipleBookCoverPatches(patches);
      expect(bookSocketServiceMock.handleMultipleBookCoverPatches).toHaveBeenCalledWith(patches);
    });
  });
});

