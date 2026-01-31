import { Injectable, inject } from '@angular/core';
import { Book } from '../../book/model/book.model';
import { GroupRule, Rule, RuleField } from '../component/magic-shelf-component';
import { IncompleteSeriesService } from './incomplete-series.service';

@Injectable({ providedIn: 'root' })
export class BookRuleEvaluatorService {
  private incompleteSeriesService = inject(IncompleteSeriesService);
  private allBooks: Book[] = [];

  /**
   * Set all books for context-dependent rules like incompleteSeries
   */
  setAllBooks(books: Book[]): void {
    this.allBooks = books;
  }

  evaluateGroup(book: Book, group: GroupRule): boolean {
    const results = group.rules.map(rule => {
      if ('type' in rule && rule.type === 'group') {
        return this.evaluateGroup(book, rule as GroupRule);
      } else {
        return this.evaluateRule(book, rule as Rule);
      }
    });
    return group.join === 'and' ? results.every(Boolean) : results.some(Boolean);
  }

  private evaluateRule(book: Book, rule: Rule): boolean {
    const rawValue = this.extractBookValue(book, rule.field);

    // Special handling for boolean fields
    if (rule.field === 'incompleteSeries') {
      const boolValue = rawValue === true;
      const ruleBoolValue = rule.value === 'true' || rule.value === true;

      switch (rule.operator) {
        case 'equals':
          return boolValue === ruleBoolValue;
        case 'not_equals':
          return boolValue !== ruleBoolValue;
        default:
          return false;
      }
    }

    // Special handling for seriesStatus field
    if (rule.field === 'seriesStatus') {
      const statusValue = String(rawValue).toLowerCase();
      const ruleStatusValue = String(rule.value).toLowerCase();

      switch (rule.operator) {
        case 'equals':
          return statusValue === ruleStatusValue;
        case 'not_equals':
          return statusValue !== ruleStatusValue;
        default:
          return false;
      }
    }

    // Special handling for externalId and externalRating
    if (rule.field === 'externalId' || rule.field === 'externalRating') {
      const source = String(rule.value).toLowerCase();
      let fieldValue: unknown;

      if (rule.field === 'externalId') {
        // Map source to the actual ID field
        switch (source) {
          case 'isbn13':
            fieldValue = book.metadata?.isbn13;
            break;
          case 'isbn10':
            fieldValue = book.metadata?.isbn10;
            break;
          case 'asin':
            fieldValue = (book.metadata as Record<string, unknown>)?.['asin'];
            break;
          case 'goodreads':
            fieldValue = book.metadata?.goodreadsId;
            break;
          case 'comicvine':
            fieldValue = book.metadata?.comicvineId;
            break;
          case 'hardcover':
            fieldValue = book.metadata?.hardcoverId;
            break;
          case 'hardcoverbook':
            fieldValue = book.metadata?.hardcoverBookId;
            break;
          case 'google':
            fieldValue = book.metadata?.googleId;
            break;
          case 'lubimyczytac':
            fieldValue = book.metadata?.lubimyczytacId;
            break;
          case 'ranobedb':
            fieldValue = book.metadata?.ranobedbId;
            break;
          default:
            fieldValue = null;
        }
      } else if (rule.field === 'externalRating') {
        // Map source to the actual rating field
        switch (source) {
          case 'amazon':
            fieldValue = book.metadata?.amazonRating;
            break;
          case 'goodreads':
            fieldValue = book.metadata?.goodreadsRating;
            break;
          case 'hardcover':
            fieldValue = book.metadata?.hardcoverRating;
            break;
          case 'lubimyczytac':
            fieldValue = book.metadata?.lubimyczytacRating;
            break;
          case 'ranobedb':
            fieldValue = book.metadata?.ranobedbRating;
            break;
          case 'personal':
            fieldValue = book.personalRating;
            break;
          default:
            fieldValue = null;
        }
      }

      // Check if the field is empty/not empty
      const isEmpty = fieldValue === null || fieldValue === undefined || fieldValue === '';
      switch (rule.operator) {
        case 'has':
          return !isEmpty;
        case 'missing':
          return isEmpty;
        case 'is_empty':
          return isEmpty;
        case 'is_not_empty':
          return !isEmpty;
        case 'equals':
          // For equals, we check if it has a value (not empty)
          return !isEmpty;
        case 'not_equals':
          // For not equals, we check if it's empty
          return isEmpty;
        default:
          return false;
      }
    }

    const normalize = (val: unknown): unknown => {
      if (val === null || val === undefined) return val;
      if (val instanceof Date) return val;
      if (typeof val === 'number') return val; // Don't normalize numbers
      if (typeof val === 'string') {
        // Only try to parse as date if it looks like a date (contains - or /)
        if (val.includes('-') || val.includes('/')) {
          const date = new Date(val);
          if (!isNaN(date.getTime())) return date;
        }
        return val.toLowerCase();
      }
      return val;
    };

    const value = normalize(rawValue);
    const ruleVal = normalize(rule.value);
    const ruleStart = normalize(rule.valueStart);
    const ruleEnd = normalize(rule.valueEnd);

    const getArrayField = (field: RuleField): string[] => {
      switch (field) {
        case 'authors':
          return (book.metadata?.authors ?? []).map(a => a.toLowerCase());
        case 'categories':
          return (book.metadata?.categories ?? []).map(c => c.toLowerCase());
        case 'moods':
          return (book.metadata?.moods ?? []).map(m => m.toLowerCase());
        case 'tags':
          return (book.metadata?.tags ?? []).map(t => t.toLowerCase());
        case 'readStatus':
          return [String(book.readStatus ?? 'UNSET').toLowerCase()];
        case 'fileType': {
          const fileType = this.getBookTypeAsFileType(book);
          if (!fileType) return [];
          
          // Special case: CBX books should also match cbr, cbz, cb7
          if (fileType === 'cbx') {
            return ['cbx', 'cbr', 'cbz', 'cb7'];
          }
          return [fileType];
        }
        case 'library':
          return [String(book.libraryId)];
        case 'shelf':
          return (book.shelves ?? []).map(s => String(s.id));
        case 'language':
          return [String(book.metadata?.language ?? '').toLowerCase()];
        case 'title':
          return [String(book.metadata?.title ?? '').toLowerCase()];
        case 'subtitle':
          return [String(book.metadata?.subtitle ?? '').toLowerCase()];
        case 'publisher':
          return [String(book.metadata?.publisher ?? '').toLowerCase()];
        case 'seriesName':
          return [String(book.metadata?.seriesName ?? '').toLowerCase()];
        case 'incompleteSeries':
          return [String(book.incompleteSeries ?? false)];
        case 'seriesStatus':
          return [this.getSeriesStatus(book)];
        default:
          return [];
      }
    };

    const isNumericIdField = rule.field === 'library' || rule.field === 'shelf';
    const ruleList = Array.isArray(rule.value)
      ? rule.value.map(v => isNumericIdField ? String(v) : String(v).toLowerCase())
      : (rule.value ? [isNumericIdField ? String(rule.value) : String(rule.value).toLowerCase()] : []);

    switch (rule.operator) {
      case 'equals':
        // Special handling for fileType with CBX books
        if (rule.field === 'fileType' && value === 'cbx') {
          const cbxExtensions = ['cbx', 'cbr', 'cbz', 'cb7'];
          return cbxExtensions.includes(String(ruleVal).toLowerCase());
        }
        if (Array.isArray(value)) {
          return value.some(v => ruleList.includes(isNumericIdField ? String(v) : String(v).toLowerCase()));
        }
        if (value instanceof Date && ruleVal instanceof Date) {
          return value.getTime() === ruleVal.getTime();
        }
        return value === ruleVal;

      case 'not_equals':
        // Special handling for fileType with CBX books
        if (rule.field === 'fileType' && value === 'cbx') {
          const cbxExtensions = ['cbx', 'cbr', 'cbz', 'cb7'];
          return !cbxExtensions.includes(String(ruleVal).toLowerCase());
        }
        if (Array.isArray(value)) {
          return value.every(v => !ruleList.includes(isNumericIdField ? String(v) : String(v).toLowerCase()));
        }
        if (value instanceof Date && ruleVal instanceof Date) {
          return value.getTime() !== ruleVal.getTime();
        }
        return value !== ruleVal;

      case 'contains':
        if (Array.isArray(value)) {
          if (typeof ruleVal !== 'string') return false;
          return value.some(v => String(v).includes(ruleVal));
        }
        if (typeof value !== 'string') return false;
        if (typeof ruleVal !== 'string') return false;
        return value.includes(ruleVal);

      case 'does_not_contain':
        if (Array.isArray(value)) {
          if (typeof ruleVal !== 'string') return true;
          return value.every(v => !String(v).includes(ruleVal));
        }
        if (typeof value !== 'string') return true;
        if (typeof ruleVal !== 'string') return true;
        return !value.includes(ruleVal);

      case 'starts_with':
        if (Array.isArray(value)) {
          if (typeof ruleVal !== 'string') return false;
          return value.some(v => String(v).startsWith(ruleVal));
        }
        if (typeof value !== 'string') return false;
        if (typeof ruleVal !== 'string') return false;
        return value.startsWith(ruleVal);

      case 'ends_with':
        if (Array.isArray(value)) {
          if (typeof ruleVal !== 'string') return false;
          return value.some(v => String(v).endsWith(ruleVal));
        }
        if (typeof value !== 'string') return false;
        if (typeof ruleVal !== 'string') return false;
        return value.endsWith(ruleVal);

      case 'greater_than':
        if (value instanceof Date && ruleVal instanceof Date) {
          return value > ruleVal;
        }
        return Number(value) > Number(ruleVal);

      case 'greater_than_equal_to':
        if (value instanceof Date && ruleVal instanceof Date) {
          return value >= ruleVal;
        }
        return Number(value) >= Number(ruleVal);

      case 'less_than':
        if (value instanceof Date && ruleVal instanceof Date) {
          return value < ruleVal;
        }
        return Number(value) < Number(ruleVal);

      case 'less_than_equal_to':
        if (value instanceof Date && ruleVal instanceof Date) {
          return value <= ruleVal;
        }
        return Number(value) <= Number(ruleVal);

      case 'in_between':
        if (value == null || ruleStart == null || ruleEnd == null) return false;
        if (value instanceof Date && ruleStart instanceof Date && ruleEnd instanceof Date) {
          return value >= ruleStart && value <= ruleEnd;
        }
        return Number(value) >= Number(ruleStart) && Number(value) <= Number(ruleEnd);

      case 'is_empty':
        if (value == null) return true;
        if (typeof value === 'string') return value.trim() === '';
        if (Array.isArray(value)) return value.length === 0;
        return false;

      case 'is_not_empty':
        if (value == null) return false;
        if (typeof value === 'string') return value.trim() !== '';
        if (Array.isArray(value)) return value.length > 0;
        return true;

      case 'includes_all': {
        const bookList = getArrayField(rule.field);
        return ruleList.every(v => bookList.includes(v));
      }

      case 'excludes_all': {
        const bookList = getArrayField(rule.field);
        return ruleList.every(v => !bookList.includes(v));
      }

      case 'includes_any': {
        const bookList = getArrayField(rule.field);
        return ruleList.some(v => bookList.includes(v));
      }

      default:
        return false;
    }
  }

  private extractBookValue(book: Book, field: RuleField): unknown {
    switch (field) {
      case 'library':
        return book.libraryId;
      case 'shelf':
        return (book.shelves ?? []).map(s => s.id);
      case 'readStatus':
        return book.readStatus ?? 'UNSET';
      case 'fileType':
        return this.getBookTypeAsFileType(book);
      case 'fileSize':
        return book.fileSizeKb;
      case 'metadataScore':
        return book.metadataMatchScore;
      case 'personalRating':
        return book.personalRating;
      case 'title':
        return book.metadata?.title?.toLowerCase() ?? null;
      case 'subtitle':
        return book.metadata?.subtitle?.toLowerCase() ?? null;
      case 'authors':
        return (book.metadata?.authors ?? []).map(a => a.toLowerCase());
      case 'categories':
        return (book.metadata?.categories ?? []).map(c => c.toLowerCase());
      case 'moods':
        return (book.metadata?.moods ?? []).map(m => m.toLowerCase());
      case 'tags':
        return (book.metadata?.tags ?? []).map(t => t.toLowerCase());
      case 'publisher':
        return book.metadata?.publisher?.toLowerCase() ?? null;
      case 'publishedDate':
        return book.metadata?.publishedDate ? new Date(book.metadata.publishedDate) : null;
      case 'dateFinished':
        return book.dateFinished ? new Date(book.dateFinished) : null;
      case 'lastReadTime':
        return book.lastReadTime ? new Date(book.lastReadTime) : null;
      case 'addedOn':
        if (!book.addedOn) return null;
        const addedDate = new Date(book.addedOn);
        const today = new Date();
        const diffTime = Math.abs(today.getTime() - addedDate.getTime());
        const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
        return diffDays;
      case 'seriesName':
        return book.metadata?.seriesName?.toLowerCase() ?? null;
      case 'seriesNumber':
        return book.metadata?.seriesNumber;
      case 'seriesTotal':
        return book.metadata?.seriesTotal;
      case 'pageCount':
        return book.metadata?.pageCount;
      case 'language':
        return book.metadata?.language?.toLowerCase() ?? null;
      case 'amazonRating':
        return book.metadata?.amazonRating;
      case 'amazonReviewCount':
        return book.metadata?.amazonReviewCount;
      case 'goodreadsRating':
        return book.metadata?.goodreadsRating;
      case 'goodreadsReviewCount':
        return book.metadata?.goodreadsReviewCount;
      case 'hardcoverRating':
        return book.metadata?.hardcoverRating;
      case 'hardcoverReviewCount':
        return book.metadata?.hardcoverReviewCount;
      case 'lubimyczytacRating':
        return book.metadata?.lubimyczytacRating;
      case 'ranobedbRating':
        return book.metadata?.ranobedbRating;
      case 'incompleteSeries':
        return book.incompleteSeries ?? false;
      case 'seriesStatus':
        return this.getSeriesStatus(book);
      case 'externalId':
      case 'externalRating':
        // These are meta-fields handled specially in evaluateRule
        return null;
      default:
        return (book as Record<string, unknown>)[field];
    }
  }

  /**
   * Gets the series status for a book.
   * Returns 'completed' if seriesNumber === seriesTotal,
   * 'ongoing' if the book is in a series (has seriesName),
   * or an empty string if not in a series.
   */
  private getSeriesStatus(book: Book): string {
    const seriesName = book.metadata?.seriesName;
    if (!seriesName) {
      return ''; // Not in a series
    }

    const seriesNumber = book.metadata?.seriesNumber;
    const seriesTotal = book.metadata?.seriesTotal;

    // If both number and total are defined and equal, it's completed
    if (
      seriesNumber !== null && seriesNumber !== undefined &&
      seriesTotal !== null && seriesTotal !== undefined &&
      seriesNumber === seriesTotal
    ) {
      return 'completed';
    }

    // Otherwise, if it has a series name, it's ongoing
    return 'ongoing';
  }

  private getFileExtension(filePath?: string): string | null {
    if (!filePath) return null;
    const parts = filePath.split('.');
    if (parts.length < 2) return null;
    return parts.pop() ?? null;
  }

  /**
   * Get the book type as a file type string (lowercase)
   * Handles both book.bookType (for tests) and book.primaryFile.bookType
   */
  private getBookTypeAsFileType(book: Book): string | null {
    // Try to get bookType from the book object directly (for tests and backwards compatibility)
    const directBookType = (book as Record<string, unknown>)['bookType'];
    if (typeof directBookType === 'string') {
      return directBookType.toLowerCase();
    }

    // Try to get bookType from primaryFile
    const primaryFileType = book.primaryFile?.bookType;
    if (typeof primaryFileType === 'string') {
      return primaryFileType.toLowerCase();
    }

    // Fallback to extracting from fileName
    return this.getFileExtension(book.fileName)?.toLowerCase() ?? null;
  }
}
