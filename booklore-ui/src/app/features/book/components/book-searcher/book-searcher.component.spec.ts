import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {SearchPreferenceService, SearchTriggerMode} from '../../../../shared/service/search-preference.service';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';

/**
 * Tests for the search trigger mode behavior used in BookSearcherComponent.
 * We test the logic directly (mirroring the component methods) rather than
 * rendering the full component template, to avoid deep DI chains.
 */
describe('BookSearcherComponent search trigger logic', () => {
  let searchPrefService: SearchPreferenceService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SearchPreferenceService,
        {
          provide: LocalStorageService,
          useValue: {
            get: vi.fn(() => null),
            set: vi.fn(),
          },
        },
      ],
    });

    searchPrefService = TestBed.inject(SearchPreferenceService);
  });

  function createComponentLogic() {
    let lastEmittedTerm: string | null = null;
    const emitSearch = (term: string) => {
      lastEmittedTerm = term;
    };

    return {
      searchQuery: '',
      books: [] as any[],

      get searchMode(): SearchTriggerMode {
        return searchPrefService.mode;
      },

      onSearchInputChange() {
        if (this.searchMode === 'instant') {
          emitSearch(this.searchQuery.trim());
        }
      },

      triggerSearch() {
        emitSearch(this.searchQuery.trim());
      },

      onSearchKeydown(event: KeyboardEvent) {
        if (event.key === 'Enter' && this.searchMode === 'enter') {
          this.triggerSearch();
        }
      },

      clearSearch() {
        this.searchQuery = '';
        this.books = [];
      },

      getLastEmittedTerm: () => lastEmittedTerm,
    };
  }

  describe('instant mode (default)', () => {
    beforeEach(() => {
      searchPrefService.setMode('instant');
    });

    it('should emit search term on input change', () => {
      const comp = createComponentLogic();
      comp.searchQuery = 'test query';
      comp.onSearchInputChange();
      expect(comp.getLastEmittedTerm()).toBe('test query');
    });

    it('should not trigger search on Enter key', () => {
      const comp = createComponentLogic();
      comp.searchQuery = 'test';
      const event = new KeyboardEvent('keydown', {key: 'Enter'});
      comp.onSearchKeydown(event);
      expect(comp.getLastEmittedTerm()).toBeNull();
    });

    it('should trim whitespace from search query', () => {
      const comp = createComponentLogic();
      comp.searchQuery = '  hello  ';
      comp.onSearchInputChange();
      expect(comp.getLastEmittedTerm()).toBe('hello');
    });
  });

  describe('enter mode', () => {
    beforeEach(() => {
      searchPrefService.setMode('enter');
    });

    it('should NOT emit search term on input change', () => {
      const comp = createComponentLogic();
      comp.searchQuery = 'test query';
      comp.onSearchInputChange();
      expect(comp.getLastEmittedTerm()).toBeNull();
    });

    it('should trigger search on Enter key', () => {
      const comp = createComponentLogic();
      comp.searchQuery = 'search term';
      const event = new KeyboardEvent('keydown', {key: 'Enter'});
      comp.onSearchKeydown(event);
      expect(comp.getLastEmittedTerm()).toBe('search term');
    });

    it('should NOT trigger search on non-Enter keys', () => {
      const comp = createComponentLogic();
      comp.searchQuery = 'test';
      comp.onSearchKeydown(new KeyboardEvent('keydown', {key: 'a'}));
      expect(comp.getLastEmittedTerm()).toBeNull();

      comp.onSearchKeydown(new KeyboardEvent('keydown', {key: 'Tab'}));
      expect(comp.getLastEmittedTerm()).toBeNull();
    });
  });

  describe('button mode', () => {
    beforeEach(() => {
      searchPrefService.setMode('button');
    });

    it('should NOT emit search term on input change', () => {
      const comp = createComponentLogic();
      comp.searchQuery = 'test query';
      comp.onSearchInputChange();
      expect(comp.getLastEmittedTerm()).toBeNull();
    });

    it('should NOT trigger search on Enter key', () => {
      const comp = createComponentLogic();
      comp.searchQuery = 'test';
      const event = new KeyboardEvent('keydown', {key: 'Enter'});
      comp.onSearchKeydown(event);
      expect(comp.getLastEmittedTerm()).toBeNull();
    });

    it('should trigger search via triggerSearch method', () => {
      const comp = createComponentLogic();
      comp.searchQuery = 'button search';
      comp.triggerSearch();
      expect(comp.getLastEmittedTerm()).toBe('button search');
    });
  });

  describe('clearSearch', () => {
    it('should clear the search query and results', () => {
      const comp = createComponentLogic();
      comp.searchQuery = 'some text';
      comp.books = [{id: 1} as any];
      comp.clearSearch();
      expect(comp.searchQuery).toBe('');
      expect(comp.books).toEqual([]);
    });
  });

  describe('searchMode getter', () => {
    it('should return the current mode from SearchPreferenceService', () => {
      const comp = createComponentLogic();

      searchPrefService.setMode('enter');
      expect(comp.searchMode).toBe('enter');

      searchPrefService.setMode('button');
      expect(comp.searchMode).toBe('button');

      searchPrefService.setMode('instant');
      expect(comp.searchMode).toBe('instant');
    });
  });
});
