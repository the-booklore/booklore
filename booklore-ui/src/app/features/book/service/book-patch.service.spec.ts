import {beforeEach, describe, expect, it, afterEach} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient, withInterceptorsFromDi} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {BookPatchService} from './book-patch.service';
import {BookStateService} from './book-state.service';
import {Book} from '../model/book.model';

class MockBookStateService {
  private state: { books?: Book[] } = {books: []};

  getCurrentBookState() {
    return this.state;
  }

  updateBookState(nextState: { books?: Book[] }) {
    this.state = nextState;
  }

  setBooks(books: Book[]) {
    this.state = {...this.state, books};
  }
}

describe('BookPatchService (purchase date)', () => {
  let service: BookPatchService;
  let httpMock: HttpTestingController;
  let mockState: MockBookStateService;

  const createBook = (overrides: Partial<Book>): Book => ({
    id: 1,
    libraryId: 1,
    libraryName: 'Main',
    bookType: 'EPUB',
    metadata: {
      bookId: 1,
      title: 'Test',
      authors: ['Tester'],
      categories: [],
      language: 'en'
    },
    ...overrides
  } as Book);

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        BookPatchService,
        {provide: BookStateService, useClass: MockBookStateService},
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(BookPatchService);
    httpMock = TestBed.inject(HttpTestingController);
    mockState = TestBed.inject(BookStateService) as unknown as MockBookStateService;
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('should set purchase date for targeted books', () => {
    const initialBooks = [
      createBook({id: 1, addedOn: '2024-01-10T00:00:00Z'}),
      createBook({id: 2, addedOn: '2024-01-12T00:00:00Z'})
    ];
    mockState.setBooks(initialBooks);

    const newDate = '2024-02-01T00:00:00Z';
    service.updatePurchaseDate(1, newDate).subscribe();

    const req = httpMock.expectOne(r => r.url.endsWith('/api/v1/books/purchase-date'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({bookIds: [1], purchaseDate: newDate});
    req.flush(null);

    const updated = mockState.getCurrentBookState().books ?? [];
    expect(updated.find(b => b.id === 1)?.purchaseDate).toBe(newDate);
    expect(updated.find(b => b.id === 2)?.purchaseDate).toBeUndefined();
  });

  it('should fall back to addedOn when clearing purchase date', () => {
    const addedOn = '2024-01-05T00:00:00Z';
    mockState.setBooks([
      createBook({id: 10, addedOn, purchaseDate: '2024-02-10T00:00:00Z'})
    ]);

    service.updatePurchaseDate(10, null).subscribe();

    const req = httpMock.expectOne(r => r.url.endsWith('/api/v1/books/purchase-date'));
    expect(req.request.body).toEqual({bookIds: [10], purchaseDate: null});
    req.flush(null);

    const updated = mockState.getCurrentBookState().books ?? [];
    expect(updated[0].purchaseDate).toBeUndefined();
  });
});
