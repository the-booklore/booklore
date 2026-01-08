import {inject, Injectable} from '@angular/core';
import {first, Observable, of, throwError} from 'rxjs';
import {HttpClient, HttpParams} from '@angular/common/http';
import {catchError, distinctUntilChanged, filter, finalize, map, shareReplay, tap} from 'rxjs/operators';
import {AdditionalFile, AdditionalFileType, Book, BookDeletionResponse, BookMetadata, BookRecommendation, BookSetting, BulkMetadataUpdateRequest, MetadataUpdateWrapper, ReadStatus} from '../model/book.model';
import {BookState} from '../model/state/book-state.model';
import {API_CONFIG} from '../../../core/config/api-config';
import {MessageService} from 'primeng/api';
import {ResetProgressType} from '../../../shared/constants/reset-progress-type';
import {AuthService} from '../../../shared/service/auth.service';
import {FileDownloadService} from '../../../shared/service/file-download.service';
import {Router} from '@angular/router';
import {BookStateService} from './book-state.service';
import {BookSocketService} from './book-socket.service';
import {BookPatchService} from './book-patch.service';

export interface BookStatusUpdateResponse {
  bookId: number;
  readStatus: ReadStatus;
  readStatusModifiedTime: string;
  dateFinished?: string;
}

export interface PersonalRatingUpdateResponse {
  bookId: number;
  personalRating?: number;
}

@Injectable({
  providedIn: 'root',
})
export class BookService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);
  private messageService = inject(MessageService);
  private authService = inject(AuthService);
  private fileDownloadService = inject(FileDownloadService);
  private router = inject(Router);
  private bookStateService = inject(BookStateService);
  private bookSocketService = inject(BookSocketService);
  private bookPatchService = inject(BookPatchService);

  private loading$: Observable<Book[]> | null = null;

  constructor() {
    this.authService.token$.pipe(
      distinctUntilChanged()
    ).subscribe(token => {
      if (token === null) {
        this.bookStateService.resetBookState();
        this.loading$ = null;
      } else {
        const current = this.bookStateService.getCurrentBookState();
        if (current.loaded && !current.books) {
          this.bookStateService.updateBookState({
            books: null,
            loaded: false,
            error: null,
          });
          this.loading$ = null;
        }
      }
    });
  }

  /*------------------ State Management ------------------*/

  bookState$ = this.bookStateService.bookState$.pipe(
    tap(state => {
      if (!state.loaded && !state.error && !this.loading$) {
        this.loading$ = this.fetchBooks().pipe(
          shareReplay(1),
          finalize(() => (this.loading$ = null))
        );
        this.loading$.subscribe();
      }
    })
  );

  getCurrentBookState(): BookState {
    return this.bookStateService.getCurrentBookState();
  }

  private fetchBooks(): Observable<Book[]> {
    return this.http.get<Book[]>(this.url).pipe(
      tap(books => {
        this.bookStateService.updateBookState({
          books: books || [],
          loaded: true,
          error: null,
        });
      }),
      catchError(error => {
        const curr = this.bookStateService.getCurrentBookState();
        this.bookStateService.updateBookState({
          books: curr.books,
          loaded: true,
          error: error.message,
        });
        throw error;
      })
    );
  }

  refreshBooks(): void {
    this.http.get<Book[]>(this.url).pipe(
      tap(books => {
        this.bookStateService.updateBookState({
          books: books || [],
          loaded: true,
          error: null,
        });
      }),
      catchError(error => {
        this.bookStateService.updateBookState({
          books: null,
          loaded: true,
          error: error.message,
        });
        return of(null);
      })
    ).subscribe();
  }

  removeBooksByLibraryId(libraryId: number): void {
    const currentState = this.bookStateService.getCurrentBookState();
    const currentBooks = currentState.books || [];
    const filteredBooks = currentBooks.filter(book => book.libraryId !== libraryId);
    this.bookStateService.updateBookState({...currentState, books: filteredBooks});
  }

  removeBooksFromShelf(shelfId: number): void {
    const currentState = this.bookStateService.getCurrentBookState();
    const currentBooks = currentState.books || [];
    const updatedBooks = currentBooks.map(book => ({
      ...book,
      shelves: book.shelves?.filter(shelf => shelf.id !== shelfId),
    }));
    this.bookStateService.updateBookState({...currentState, books: updatedBooks});
  }

  /*------------------ Book Retrieval ------------------*/

  getBookByIdFromState(bookId: number): Book | undefined {
    const currentState = this.bookStateService.getCurrentBookState();
    return currentState.books?.find(book => +book.id === +bookId);
  }

  getBooksByIdsFromState(bookIds: number[]): Book[] {
    const currentState = this.bookStateService.getCurrentBookState();
    if (!currentState.books || bookIds.length === 0) return [];

    const idSet = new Set(bookIds.map(id => +id));
    return currentState.books.filter(book => idSet.has(+book.id));
  }

  getBookByIdFromAPI(bookId: number, withDescription: boolean): Observable<Book> {
    return this.http.get<Book>(`${this.url}/${bookId}`, {
      params: {
        withDescription: withDescription.toString()
      }
    });
  }

  getBooksInSeries(bookId: number): Observable<Book[]> {
    return this.bookStateService.bookState$.pipe(
      filter(state => state.loaded),
      first(),
      map(state => {
        const allBooks = state.books || [];
        const currentBook = allBooks.find(b => b.id === bookId);

        if (!currentBook || !currentBook.metadata?.seriesName) {
          return [];
        }

        const seriesName = currentBook.metadata.seriesName.toLowerCase();
        return allBooks.filter(b => b.metadata?.seriesName?.toLowerCase() === seriesName);
      })
    );
  }

  getBookRecommendations(bookId: number, limit: number = 20): Observable<BookRecommendation[]> {
    return this.http.get<BookRecommendation[]>(`${this.url}/${bookId}/recommendations`, {
      params: {limit: limit.toString()}
    });
  }

  /*------------------ Book Operations ------------------*/

  deleteBooks(ids: Set<number>): Observable<BookDeletionResponse> {
    const idList = Array.from(ids);
    const params = new HttpParams().set('ids', idList.join(','));

    return this.http.delete<BookDeletionResponse>(this.url, {params}).pipe(
      tap(response => {
        const currentState = this.bookStateService.getCurrentBookState();
        const remainingBooks = (currentState.books || []).filter(
          book => !ids.has(book.id)
        );

        this.bookStateService.updateBookState({
          books: remainingBooks,
          loaded: true,
          error: null,
        });

        if (response.failedFileDeletions?.length > 0) {
          this.messageService.add({
            severity: 'warn',
            summary: 'Some files could not be deleted',
            detail: `Books: ${response.failedFileDeletions.join(', ')}`,
          });
        } else {
          this.messageService.add({
            severity: 'success',
            summary: 'Books Deleted',
            detail: `${idList.length} book(s) deleted successfully.`,
          });
        }
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: 'Delete Failed',
          detail: error?.error?.message || error?.message || 'An error occurred while deleting books.',
        });
        return throwError(() => error);
      })
    );
  }

  updateBookShelves(bookIds: Set<number | undefined>, shelvesToAssign: Set<number | null | undefined>, shelvesToUnassign: Set<number | null | undefined>): Observable<Book[]> {
    return this.bookPatchService.updateBookShelves(bookIds, shelvesToAssign, shelvesToUnassign).pipe(
      catchError(error => {
        const currentState = this.bookStateService.getCurrentBookState();
        this.bookStateService.updateBookState({...currentState, error: error.message});
        throw error;
      })
    );
  }

  /*------------------ Reading & Viewer Settings ------------------*/

  readBook(bookId: number, reader: 'ngx' | 'streaming' = 'ngx'): void {
    const book = this.bookStateService
      .getCurrentBookState()
      .books?.find(b => b.id === bookId);

    if (!book) {
      console.error('Book not found');
      return;
    }

    const baseUrl =
      book.bookType === 'PDF'
        ? reader === 'ngx'
          ? 'pdf-reader'
          : 'cbx-reader'
        : book.bookType === 'EPUB'
          ? 'epub-reader'
          : book.bookType === 'CBX'
            ? 'cbx-reader'
            : null;

    if (!baseUrl) {
      console.error('Unsupported book type:', book.bookType);
      return;
    }

    this.router.navigate([`/${baseUrl}/book/${book.id}`]);
    this.updateLastReadTime(book.id);
  }

  getBookSetting(bookId: number): Observable<BookSetting> {
    return this.http.get<BookSetting>(`${this.url}/${bookId}/viewer-setting`);
  }

  updateViewerSetting(bookSetting: BookSetting, bookId: number): Observable<void> {
    return this.http.put<void>(`${this.url}/${bookId}/viewer-setting`, bookSetting);
  }

  /*------------------ File Operations ------------------*/

  getFileContent(bookId: number): Observable<Blob> {
    return this.http.get<Blob>(`${this.url}/${bookId}/content`, {responseType: 'blob' as 'json'});
  }

  downloadFile(book: Book): void {
    const downloadUrl = `${this.url}/${book.id}/download`;
    this.fileDownloadService.downloadFile(downloadUrl, book.fileName!);
  }

  deleteAdditionalFile(bookId: number, fileId: number): Observable<void> {
    const deleteUrl = `${this.url}/${bookId}/files/${fileId}`;
    return this.http.delete<void>(deleteUrl).pipe(
      tap(() => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          if (book.id === bookId) {
            return {
              ...book,
              alternativeFormats: book.alternativeFormats?.filter(file => file.id !== fileId),
              supplementaryFiles: book.supplementaryFiles?.filter(file => file.id !== fileId)
            };
          }
          return book;
        });

        this.bookStateService.updateBookState({
          ...currentState,
          books: updatedBooks
        });

        this.messageService.add({
          severity: 'success',
          summary: 'File Deleted',
          detail: 'Additional file deleted successfully.'
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: 'Delete Failed',
          detail: error?.error?.message || error?.message || 'An error occurred while deleting the file.'
        });
        return throwError(() => error);
      })
    );
  }

  uploadAdditionalFile(bookId: number, file: File, fileType: AdditionalFileType, description?: string): Observable<AdditionalFile> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('additionalFileType', fileType);
    if (description) {
      formData.append('description', description);
    }

    return this.http.post<AdditionalFile>(`${this.url}/${bookId}/files`, formData).pipe(
      tap((newFile) => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          if (book.id === bookId) {
            const updatedBook = {...book};
            if (fileType === AdditionalFileType.ALTERNATIVE_FORMAT) {
              updatedBook.alternativeFormats = [...(book.alternativeFormats || []), newFile];
            } else {
              updatedBook.supplementaryFiles = [...(book.supplementaryFiles || []), newFile];
            }
            return updatedBook;
          }
          return book;
        });

        this.bookStateService.updateBookState({
          ...currentState,
          books: updatedBooks
        });

        this.messageService.add({
          severity: 'success',
          summary: 'File Uploaded',
          detail: 'Additional file uploaded successfully.'
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: 'Upload Failed',
          detail: error?.error?.message || error?.message || 'An error occurred while uploading the file.'
        });
        return throwError(() => error);
      })
    );
  }

  downloadAdditionalFile(book: Book, fileId: number): void {
    const additionalFile = book.alternativeFormats!.find((f: AdditionalFile) => f.id === fileId);
    const downloadUrl = `${this.url}/${additionalFile!.id}/files/${fileId}/download`;
    this.fileDownloadService.downloadFile(downloadUrl, additionalFile!.fileName!);
  }

  /*------------------ Progress & Status Tracking ------------------*/

  updateLastReadTime(bookId: number): void {
    this.bookPatchService.updateLastReadTime(bookId);
  }

  savePdfProgress(bookId: number, page: number, percentage: number): Observable<void> {
    return this.bookPatchService.savePdfProgress(bookId, page, percentage);
  }

  saveEpubProgress(bookId: number, cfi: string, percentage: number): Observable<void> {
    return this.bookPatchService.saveEpubProgress(bookId, cfi, percentage);
  }

  saveCbxProgress(bookId: number, page: number, percentage: number): Observable<void> {
    return this.bookPatchService.saveCbxProgress(bookId, page, percentage);
  }

  updateDateFinished(bookId: number, dateFinished: string | null): Observable<void> {
    return this.bookPatchService.updateDateFinished(bookId, dateFinished);
  }

  resetProgress(bookIds: number | number[], type: ResetProgressType): Observable<BookStatusUpdateResponse[]> {
    return this.bookPatchService.resetProgress(bookIds, type);
  }

  updateBookReadStatus(bookIds: number | number[], status: ReadStatus): Observable<BookStatusUpdateResponse[]> {
    return this.bookPatchService.updateBookReadStatus(bookIds, status);
  }

  /*------------------ Personal Rating ------------------*/

  resetPersonalRating(bookIds: number | number[]): Observable<PersonalRatingUpdateResponse[]> {
    return this.bookPatchService.resetPersonalRating(bookIds);
  }

  updatePersonalRating(bookIds: number | number[], rating: number): Observable<PersonalRatingUpdateResponse[]> {
    return this.bookPatchService.updatePersonalRating(bookIds, rating);
  }

  /*------------------ Metadata Operations ------------------*/


  updateBookMetadata(bookId: number | undefined, wrapper: MetadataUpdateWrapper, mergeCategories: boolean): Observable<BookMetadata> {
    const params = new HttpParams().set('mergeCategories', mergeCategories.toString());
    return this.http.put<BookMetadata>(`${this.url}/${bookId}/metadata`, wrapper, {params}).pipe(
      map(updatedMetadata => {
        this.handleBookMetadataUpdate(bookId!, updatedMetadata);
        return updatedMetadata;
      })
    );
  }

  updateBooksMetadata(request: BulkMetadataUpdateRequest): Observable<void> {
    return this.http.put(`${this.url}/bulk-edit-metadata`, request).pipe(
      map(() => void 0)
    );
  }

  toggleAllLock(bookIds: Set<number>, lock: string): Observable<void> {
    const requestBody = {
      bookIds: Array.from(bookIds),
      lock: lock
    };
    return this.http.put<BookMetadata[]>(`${this.url}/metadata/toggle-all-lock`, requestBody).pipe(
      tap((updatedMetadataList) => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          const updatedMetadata = updatedMetadataList.find(meta => meta.bookId === book.id);
          return updatedMetadata ? {...book, metadata: updatedMetadata} : book;
        });
        this.bookStateService.updateBookState({...currentState, books: updatedBooks});
      }),
      map(() => void 0),
      catchError((error) => {
        throw error;
      })
    );
  }

  toggleFieldLocks(bookIds: number[] | Set<number>, fieldActions: Record<string, 'LOCK' | 'UNLOCK'>): Observable<void> {
    const bookIdSet = bookIds instanceof Set ? bookIds : new Set(bookIds);

    const requestBody = {
      bookIds: Array.from(bookIdSet),
      fieldActions
    };

    return this.http.put<void>(`${this.url}/metadata/toggle-field-locks`, requestBody).pipe(
      tap(() => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          if (!bookIdSet.has(book.id)) return book;
          const updatedMetadata = {...book.metadata};
          for (const [field, action] of Object.entries(fieldActions)) {
            const lockField = field.endsWith('Locked') ? field : `${field}Locked`;
            if (lockField in updatedMetadata) {
              (updatedMetadata as Record<string, unknown>)[lockField] = action === 'LOCK';
            }
          }
          return {
            ...book,
            metadata: updatedMetadata
          };
        });
        this.bookStateService.updateBookState({
          ...currentState,
          books: updatedBooks as Book[]
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: 'Field Lock Update Failed',
          detail: 'Failed to update metadata field locks. Please try again.',
        });
        throw error;
      })
    );
  }

  consolidateMetadata(metadataType: 'authors' | 'categories' | 'moods' | 'tags' | 'series' | 'publishers' | 'languages', targetValues: string[], valuesToMerge: string[]): Observable<unknown> {
    const payload = {metadataType, targetValues, valuesToMerge};
    return this.http.post(`${this.url}/metadata/manage/consolidate`, payload).pipe(
      tap(() => {
        this.refreshBooks();
      })
    );
  }

  deleteMetadata(metadataType: 'authors' | 'categories' | 'moods' | 'tags' | 'series' | 'publishers' | 'languages', valuesToDelete: string[]): Observable<unknown> {
    const payload = {metadataType, valuesToDelete};
    return this.http.post(`${this.url}/metadata/manage/delete`, payload).pipe(
      tap(() => {
        this.refreshBooks();
      })
    );
  }

  /*------------------ Cover Operations ------------------*/

  getUploadCoverUrl(bookId: number): string {
    return this.url + '/' + bookId + "/metadata/cover/upload"
  }

  uploadCoverFromUrl(bookId: number, url: string): Observable<BookMetadata> {
    return this.http
      .post<BookMetadata>(`${this.url}/${bookId}/metadata/cover/from-url`, {url})
      .pipe(
        tap(updatedMetadata =>
          this.handleBookMetadataUpdate(bookId, updatedMetadata)
        )
      );
  }

  regenerateCovers(): Observable<void> {
    return this.http.post<void>(`${this.url}/regenerate-covers`, {});
  }

  regenerateCover(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${bookId}/regenerate-cover`, {});
  }

  generateCustomCover(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${bookId}/generate-custom-cover`, {});
  }

  regenerateCoversForBooks(bookIds: number[]): Observable<void> {
    return this.http.post<void>(`${this.url}/bulk-regenerate-covers`, {bookIds});
  }

  bulkUploadCover(bookIds: number[], file: File): Observable<void> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('bookIds', bookIds.join(','));
    return this.http.post<void>(`${this.url}/bulk-upload-cover`, formData);
  }

  /*------------------ Websocket Handlers ------------------*/

  handleNewlyCreatedBook(book: Book): void {
    this.bookSocketService.handleNewlyCreatedBook(book);
  }

  handleRemovedBookIds(removedBookIds: number[]): void {
    this.bookSocketService.handleRemovedBookIds(removedBookIds);
  }

  handleBookUpdate(updatedBook: Book): void {
    this.bookSocketService.handleBookUpdate(updatedBook);
  }

  handleMultipleBookUpdates(updatedBooks: Book[]): void {
    this.bookSocketService.handleMultipleBookUpdates(updatedBooks);
  }

  handleBookMetadataUpdate(bookId: number, updatedMetadata: BookMetadata): void {
    this.bookSocketService.handleBookMetadataUpdate(bookId, updatedMetadata);
  }

  handleMultipleBookCoverPatches(patches: { id: number; coverUpdatedOn: string }[]): void {
    this.bookSocketService.handleMultipleBookCoverPatches(patches);
  }
}
