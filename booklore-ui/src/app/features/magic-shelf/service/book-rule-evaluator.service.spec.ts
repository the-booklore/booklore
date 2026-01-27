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
});
