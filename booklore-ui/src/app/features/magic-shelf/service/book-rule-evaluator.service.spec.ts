import {beforeEach, describe, expect, it} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BookRuleEvaluatorService} from './book-rule-evaluator.service';
import {Book, ReadStatus} from '../../book/model/book.model';
import {GroupRule} from '../component/magic-shelf-component';

describe('BookRuleEvaluatorService', () => {
  let service: BookRuleEvaluatorService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(BookRuleEvaluatorService);
  });

  const createBook = (overrides: Partial<Book> = {}): Book => ({
    id: 1,
    bookType: 'EPUB',
    libraryId: 1,
    libraryName: 'Test Library',
    fileName: 'test.epub',
    filePath: '/path/to/test.epub',
    readStatus: ReadStatus.UNREAD,
    shelves: [],
    metadata: {
      bookId: 1,
      title: 'Test Book',
      authors: ['Test Author'],
      categories: ['Fiction'],
      language: 'en'
    },
    ...overrides
  });

  describe('fileType filtering', () => {
    it('should filter EPUB books correctly when rule uses "epub"', () => {
      const book = createBook({ bookType: 'EPUB' });
      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'equals',
            value: 'epub'
          }
        ]
      };

      const result = service.evaluateGroup(book, group);
      expect(result).toBe(true);
    });

    it('should filter PDF books correctly when rule uses "pdf"', () => {
      const book = createBook({ bookType: 'PDF' });
      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'equals',
            value: 'pdf'
          }
        ]
      };

      const result = service.evaluateGroup(book, group);
      expect(result).toBe(true);
    });

    it('should handle CBX books correctly for cbr, cbz, and cb7 rules', () => {
      const book = createBook({ bookType: 'CBX' });

      // Test CBR
      const cbrGroup: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'equals',
            value: 'cbr'
          }
        ]
      };
      expect(service.evaluateGroup(book, cbrGroup)).toBe(true);

      // Test CBZ
      const cbzGroup: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'equals',
            value: 'cbz'
          }
        ]
      };
      expect(service.evaluateGroup(book, cbzGroup)).toBe(true);

      // Test CB7
      const cb7Group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'equals',
            value: 'cb7'
          }
        ]
      };
      expect(service.evaluateGroup(book, cb7Group)).toBe(true);
    });

    it('should handle not_equals operator correctly', () => {
      const epubBook = createBook({ bookType: 'EPUB' });
      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'not_equals',
            value: 'pdf'
          }
        ]
      };

      const result = service.evaluateGroup(epubBook, group);
      expect(result).toBe(true);

      // Test the opposite case
      const pdfBook = createBook({ bookType: 'PDF' });
      const result2 = service.evaluateGroup(pdfBook, group);
      expect(result2).toBe(false);
    });

    it('should filter EPUB books correctly when rule uses "not_equals" with "epub"', () => {
      const epubBook = createBook({ bookType: 'EPUB' });
      const pdfBook = createBook({ bookType: 'PDF' });

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'not_equals',
            value: 'epub'
          }
        ]
      };

      // EPUB book should not match when rule is "not_equals epub"
      expect(service.evaluateGroup(epubBook, group)).toBe(false);

      // PDF book should match when rule is "not_equals epub"
      expect(service.evaluateGroup(pdfBook, group)).toBe(true);
    });
  });

  describe('evaluateGroup', () => {
    it('should evaluate group rules with AND logic', () => {
      const book = createBook({
        bookType: 'EPUB'
      });

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          { field: 'fileType', operator: 'equals', value: 'epub' },
          { field: 'language', operator: 'equals', value: 'en' }
        ]
      };

      const result = service.evaluateGroup(book, group);
      expect(result).toBe(true);
    });

    it('should evaluate group rules with OR logic', () => {
      const book = createBook({ bookType: 'PDF' });

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'or',
        rules: [
          { field: 'fileType', operator: 'equals', value: 'epub' },
          { field: 'fileType', operator: 'equals', value: 'pdf' }
        ]
      };

      const result = service.evaluateGroup(book, group);
      expect(result).toBe(true);
    });
  });

  describe('metadata field filtering', () => {
    it('should check if book has ISBN-13', () => {
      const book = createBook({
        metadata: {
          bookId: 1,
          isbn13: '9781234567890',
          title: 'Test Book'
        }
      });

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'metadata',
            operator: 'has',
            value: 'isbn13'
          }
        ]
      };

      expect(service.evaluateGroup(book, group)).toBe(true);
    });

    it('should check if book is missing Goodreads rating', () => {
      const book = createBook({
        metadata: {
          bookId: 1,
          title: 'Test Book'
        }
      });

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'metadata',
            operator: 'missing',
            value: 'goodreadsRating'
          }
        ]
      };

      expect(service.evaluateGroup(book, group)).toBe(true);
    });

    it('should check if book has personal rating', () => {
      const book = createBook({
        personalRating: 8.5
      });

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'metadata',
            operator: 'has',
            value: 'personalRating'
          }
        ]
      };

      expect(service.evaluateGroup(book, group)).toBe(true);
    });

    it('should check if book has authors', () => {
      const book = createBook({
        metadata: {
          bookId: 1,
          authors: ['Author One', 'Author Two']
        }
      });

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'metadata',
            operator: 'has',
            value: 'authors'
          }
        ]
      };

      expect(service.evaluateGroup(book, group)).toBe(true);
    });

    it('should check if book is missing description', () => {
      const book = createBook({
        metadata: {
          bookId: 1,
          title: 'Test Book'
        }
      });

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'metadata',
            operator: 'missing',
            value: 'description'
          }
        ]
      };

      expect(service.evaluateGroup(book, group)).toBe(true);
    });
  });

  describe('series status filtering', () => {
    it('should return "reading" status for all books in a series when one book is being read', () => {
      const book1 = createBook({
        id: 1,
        metadata: {
          bookId: 1,
          seriesName: 'Test Series',
          seriesNumber: 1,
          seriesTotal: 5
        },
        readStatus: ReadStatus.READING
      });

      const book2 = createBook({
        id: 2,
        metadata: {
          bookId: 2,
          seriesName: 'Test Series',
          seriesNumber: 2,
          seriesTotal: 5
        },
        readStatus: ReadStatus.UNREAD
      });

      // Set all books so the service can check the series
      service.setAllBooks([book1, book2]);

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'seriesStatus',
            operator: 'equals',
            value: 'reading'
          }
        ]
      };

      // Both books should match since they're in the same series and book1 is being read
      expect(service.evaluateGroup(book1, group)).toBe(true);
      expect(service.evaluateGroup(book2, group)).toBe(true);
    });

    it('should return "reading" status for all books in a series when one book is read', () => {
      const book1 = createBook({
        id: 1,
        metadata: {
          bookId: 1,
          seriesName: 'Test Series',
          seriesNumber: 1,
          seriesTotal: 5
        },
        readStatus: ReadStatus.READ
      });

      const book2 = createBook({
        id: 2,
        metadata: {
          bookId: 2,
          seriesName: 'Test Series',
          seriesNumber: 2,
          seriesTotal: 5
        },
        readStatus: ReadStatus.UNREAD
      });

      service.setAllBooks([book1, book2]);

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'seriesStatus',
            operator: 'equals',
            value: 'reading'
          }
        ]
      };

      expect(service.evaluateGroup(book1, group)).toBe(true);
      expect(service.evaluateGroup(book2, group)).toBe(true);
    });

    it('should return "completed" status when seriesNumber equals seriesTotal', () => {
      const book = createBook({
        metadata: {
          bookId: 1,
          seriesName: 'Test Series',
          seriesNumber: 5,
          seriesTotal: 5
        },
        readStatus: ReadStatus.UNREAD
      });

      service.setAllBooks([book]);

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'seriesStatus',
            operator: 'equals',
            value: 'completed'
          }
        ]
      };

      expect(service.evaluateGroup(book, group)).toBe(true);
    });

    it('should return "ongoing" status for unread books in series with no read books', () => {
      const book = createBook({
        metadata: {
          bookId: 1,
          seriesName: 'Test Series',
          seriesNumber: 1,
          seriesTotal: 5
        },
        readStatus: ReadStatus.UNREAD
      });

      service.setAllBooks([book]);

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'seriesStatus',
            operator: 'equals',
            value: 'ongoing'
          }
        ]
      };

      expect(service.evaluateGroup(book, group)).toBe(true);
    });

    it('should not match "reading" status when no books in series are read', () => {
      const book1 = createBook({
        id: 1,
        metadata: {
          bookId: 1,
          seriesName: 'Test Series',
          seriesNumber: 1,
          seriesTotal: 5
        },
        readStatus: ReadStatus.UNREAD
      });

      const book2 = createBook({
        id: 2,
        metadata: {
          bookId: 2,
          seriesName: 'Test Series',
          seriesNumber: 2,
          seriesTotal: 5
        },
        readStatus: ReadStatus.UNREAD
      });

      service.setAllBooks([book1, book2]);

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'seriesStatus',
            operator: 'equals',
            value: 'reading'
          }
        ]
      };

      expect(service.evaluateGroup(book1, group)).toBe(false);
      expect(service.evaluateGroup(book2, group)).toBe(false);
    });
  });
});
