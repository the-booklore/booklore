import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BookSearcherComponent } from './book-searcher.component';
import { SearchPreferenceService } from '../../../../shared/service/search-preference.service';
import { BookService } from '../../service/book.service';
import { Router } from '@angular/router';
import { UrlHelperService } from '../../../../shared/service/url-helper.service';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { BookState } from '../../model/state/book-state.model';
import { beforeEach, describe, expect, it, vi, afterEach } from 'vitest';
import { Book } from '../../model/book.model';

describe('BookSearcherComponent', () => {
  let component: BookSearcherComponent;
  let fixture: ComponentFixture<BookSearcherComponent>;
  let mockSearchPrefService: any;
  let mockBookService: any;
  let mockRouter: any;
  let bookStateSubject: BehaviorSubject<BookState>;

  beforeEach(async () => {
    bookStateSubject = new BehaviorSubject<BookState>({ books: [], loaded: true, error: null });

    mockBookService = {
      bookState$: bookStateSubject.asObservable()
    };

    mockSearchPrefService = {
      mode: 'instant',
      setMode: vi.fn((m) => { mockSearchPrefService.mode = m; })
    };

    mockRouter = {
      navigate: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [BookSearcherComponent, TranslocoTestingModule.forRoot({ langs: {} })],
      providers: [
        { provide: SearchPreferenceService, useValue: mockSearchPrefService },
        { provide: BookService, useValue: mockBookService },
        { provide: Router, useValue: mockRouter },
        { provide: UrlHelperService, useValue: { getThumbnailUrl: vi.fn() } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BookSearcherComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('instant mode (default)', () => {
    beforeEach(() => {
      mockSearchPrefService.mode = 'instant';
    });

    it('should filter books on input change dynamically', () => {
      vi.useFakeTimers();
      bookStateSubject.next({ books: [{ id: 1, metadata: { title: 'Test Book' } } as Book], loaded: true, error: null });

      component.searchQuery = 'Test';
      component.onSearchInputChange();
      vi.advanceTimersByTime(600);
      fixture.detectChanges();

      expect(component.books.length).toBe(1);
      vi.useRealTimers();
    });

    it('should not do anything special on Enter key', () => {
      component.searchQuery = 'test';
      const emitSpy = vi.spyOn(component, 'triggerSearch');
      const event = new KeyboardEvent('keydown', { key: 'Enter' });
      component.onSearchKeydown(event);
      expect(emitSpy).not.toHaveBeenCalled();
    });
  });

  describe('button mode', () => {
    beforeEach(() => {
      mockSearchPrefService.mode = 'button';
    });

    it('should NOT trigger search on input change', () => {
      vi.useFakeTimers();
      bookStateSubject.next({ books: [{ id: 1, metadata: { title: 'Test Book' } } as Book], loaded: true, error: null });
      component.searchQuery = 'Test';
      component.onSearchInputChange();
      vi.advanceTimersByTime(600);
      fixture.detectChanges();

      expect(component.books.length).toBe(0);
      vi.useRealTimers();
    });

    it('should trigger search on Enter key', () => {
      vi.useFakeTimers();
      bookStateSubject.next({ books: [{ id: 1, metadata: { title: 'Test Book' } } as Book], loaded: true, error: null });
      component.searchQuery = 'Test';
      const event = new KeyboardEvent('keydown', { key: 'Enter' });
      component.onSearchKeydown(event);
      vi.advanceTimersByTime(600);
      fixture.detectChanges();

      expect(component.books.length).toBe(1);
      vi.useRealTimers();
    });

    it('should NOT trigger search on non-Enter keys', () => {
      const emitSpy = vi.spyOn(component, 'triggerSearch');
      component.searchQuery = 'test';
      component.onSearchKeydown(new KeyboardEvent('keydown', { key: 'a' }));
      expect(emitSpy).not.toHaveBeenCalled();

      component.onSearchKeydown(new KeyboardEvent('keydown', { key: 'Tab' }));
      expect(emitSpy).not.toHaveBeenCalled();
    });

    it('should trigger search via triggerSearch method', () => {
      vi.useFakeTimers();
      bookStateSubject.next({ books: [{ id: 1, metadata: { title: 'Test Book' } } as Book], loaded: true, error: null });
      component.searchQuery = 'Test';
      component.triggerSearch();
      vi.advanceTimersByTime(600);
      fixture.detectChanges();

      expect(component.books.length).toBe(1);
      vi.useRealTimers();
    });
  });

  describe('clearSearch', () => {
    it('should clear the search query and results', () => {
      component.searchQuery = 'some text';
      component.books = [{ id: 1 } as any];
      component.clearSearch();
      expect(component.searchQuery).toBe('');
      expect(component.books.length).toBe(0);
    });
  });

  describe('searchMode getter', () => {
    it('should return the current mode from SearchPreferenceService', () => {
      mockSearchPrefService.mode = 'button';
      expect(component.searchMode).toBe('button');

      mockSearchPrefService.mode = 'instant';
      expect(component.searchMode).toBe('instant');
    });
  });
});
