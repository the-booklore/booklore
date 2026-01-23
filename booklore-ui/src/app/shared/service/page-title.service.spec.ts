import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {Title} from '@angular/platform-browser';
import {PageTitleService} from './page-title.service';
import {Book, BookType} from '../../features/book/model/book.model';

describe('PageTitleService', () => {
  let service: PageTitleService;
  let titleService: Title;
  let setTitleMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    setTitleMock = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        PageTitleService,
        { provide: Title, useValue: { setTitle: setTitleMock } }
      ]
    });
    service = TestBed.inject(PageTitleService);
    titleService = TestBed.inject(Title);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should set page title with setPageTitle', () => {
    service.setPageTitle('Dashboard');
    expect(setTitleMock).toHaveBeenCalledWith('Dashboard - BookLore');
  });

  it('should set book page title with all fields', () => {
    const book: Book = {
      id: 1,
      bookType: 'PDF' as BookType,
      libraryId: 2,
      libraryName: 'Lib',
      fileName: 'file.pdf',
      metadata: {
        bookId: 1,
        title: 'Book Title',
        seriesName: 'Series',
        authors: ['Author1', 'Author2']
      }
    };
    service.setBookPageTitle(book);
    expect(setTitleMock).toHaveBeenCalledWith(
      'Lib/Book Title (Series series) - by Author1 and Author2 (PDF) - BookLore'
    );
  });

  it('should set book page title without series and authors', () => {
    const book: Book = {
      id: 1,
      bookType: 'EPUB' as BookType,
      libraryId: 2,
      libraryName: 'Lib',
      fileName: 'file.epub',
      metadata: {
        bookId: 1,
        title: 'Book Title'
      }
    };
    service.setBookPageTitle(book);
    expect(setTitleMock).toHaveBeenCalledWith(
      'Lib/Book Title (EPUB) - BookLore'
    );
  });

  it('should set book page title with fallback to fileName', () => {
    const book: Book = {
      id: 1,
      bookType: 'CBX' as BookType,
      libraryId: 2,
      libraryName: 'Lib',
      fileName: 'file.cbx'
    };
    service.setBookPageTitle(book);
    expect(setTitleMock).toHaveBeenCalledWith(
      'Lib/file.cbx (CBX) - BookLore'
    );
  });
});
