import { Injectable, inject } from '@angular/core';
import { Book } from '../../book/model/book.model';
import { GroupRule, Rule, RuleField } from '../component/magic-shelf-component';
import { IncompleteSeriesService } from './incomplete-series.service';

@Injectable({ providedIn: 'root' })
export class BookRuleEvaluatorService {
  private incompleteSeriesService = inject(IncompleteSeriesService);
  private allBooks: Book[] = [];

  // Static map for fast metadata field lookups
  private static readonly METADATA_FIELD_MAP: Record<string, (book: Book) => unknown> = {
    // IDs
    'isbn13': (book) => book.metadata?.isbn13,
    'isbn10': (book) => book.metadata?.isbn10,
    'asin': (book) => (book.metadata as Record<string, unknown>)?.['asin'],
    'goodreadsid': (book) => book.metadata?.goodreadsId,
    'comicvineid': (book) => book.metadata?.comicvineId,
    'hardcoverid': (book) => book.metadata?.hardcoverId,
    'hardcoverbookid': (book) => book.metadata?.hardcoverBookId,
    'googleid': (book) => book.metadata?.googleId,
    'lubimyczytacid': (book) => book.metadata?.lubimyczytacId,
    'ranobedbid': (book) => book.metadata?.ranobedbId,
    // Ratings
    'amazonrating': (book) => book.metadata?.amazonRating,
    'goodreadsrating': (book) => book.metadata?.goodreadsRating,
    'hardcoverrating': (book) => book.metadata?.hardcoverRating,
    'lubimyczytacrating': (book) => book.metadata?.lubimyczytacRating,
    'ranobedbrating': (book) => book.metadata?.ranobedbRating,
    'personalrating': (book) => book.personalRating,
    // Review Counts
    'amazonreviewcount': (book) => book.metadata?.amazonReviewCount,
    'goodreadsreviewcount': (book) => book.metadata?.goodreadsReviewCount,
    'hardcoverreviewcount': (book) => book.metadata?.hardcoverReviewCount,
    // Other Metadata
    'title': (book) => book.metadata?.title,
    'subtitle': (book) => book.metadata?.subtitle,
    'publisher': (book) => book.metadata?.publisher,
    'publisheddate': (book) => book.metadata?.publishedDate,
    'description': (book) => book.metadata?.description,
    'seriesname': (book) => book.metadata?.seriesName,
    'seriesnumber': (book) => book.metadata?.seriesNumber,
    'seriestotal': (book) => book.metadata?.seriesTotal,
    'pagecount': (book) => book.metadata?.pageCount,
    'language': (book) => book.metadata?.language,
    'authors': (book) => book.metadata?.authors && book.metadata.authors.length > 0 ? book.metadata.authors : null,
    'categories': (book) => book.metadata?.categories && book.metadata.categories.length > 0 ? book.metadata.categories : null,
    'moods': (book) => book.metadata?.moods && book.metadata.moods.length > 0 ? book.metadata.moods : null,
    'tags': (book) => book.metadata?.tags && book.metadata.tags.length > 0 ? book.metadata.tags : null,
    'agerating': (book) => book.metadata?.ageRating,
    'contentrating': (book) => book.metadata?.contentRating,
  };

  /**
   * Set all books for context-dependent rules like incompleteSeries
   */
  setAllBooks(books: Book[]): void {
    this.allBooks = books;
  }

  evaluateGroup(book: Book, group: GroupRule): boolean {
    // Short-circuit evaluation - stop as soon as we know the result
    if (group.join === 'and') {
      for (const rule of group.rules) {
        const result = 'type' in rule && rule.type === 'group'
          ? this.evaluateGroup(book, rule as GroupRule)
          : this.evaluateRule(book, rule as Rule);
        if (!result) return false; // AND: if any is false, return false immediately
      }
      return true;
    } else {
      for (const rule of group.rules) {
        const result = 'type' in rule && rule.type === 'group'
          ? this.evaluateGroup(book, rule as GroupRule)
          : this.evaluateRule(book, rule as Rule);
        if (result) return true; // OR: if any is true, return true immediately
      }
      return false;
    }
  }

  private evaluateRule(book: Book, rule: Rule): boolean {
    // Cache metadata access
    const metadata = book.metadata;
    
    // Ultra-fast path for empty checks - direct property access, no function calls
    if (rule.operator === 'is_empty' || rule.operator === 'is_not_empty') {
      let isEmpty: boolean;
      
      // Direct property access for common fields
      switch (rule.field) {
        case 'title':
          isEmpty = !metadata?.title;
          break;
        case 'subtitle':
          isEmpty = !metadata?.subtitle;
          break;
        case 'publisher':
          isEmpty = !metadata?.publisher;
          break;
        case 'seriesName':
          isEmpty = !metadata?.seriesName;
          break;
        case 'language':
          isEmpty = !metadata?.language;
          break;
        case 'authors':
          isEmpty = !metadata?.authors || metadata.authors.length === 0;
          break;
        case 'categories':
          isEmpty = !metadata?.categories || metadata.categories.length === 0;
          break;
        case 'moods':
          isEmpty = !metadata?.moods || metadata.moods.length === 0;
          break;
        case 'tags':
          isEmpty = !metadata?.tags || metadata.tags.length === 0;
          break;
        case 'isbn10':
          isEmpty = !metadata?.isbn10;
          break;
        case 'isbn13':
          isEmpty = !metadata?.isbn13;
          break;
        default: {
          // Fall back to extractBookValue for other fields
          const rawValue = this.extractBookValue(book, rule.field);
          isEmpty = rawValue == null || rawValue === '' || (Array.isArray(rawValue) && rawValue.length === 0);
        }
      }
      
      return rule.operator === 'is_empty' ? isEmpty : !isEmpty;
    }

    // Special handling for metadata field - direct property access for maximum speed
    if (rule.field === 'metadata') {
      const metadataField = String(rule.value).toLowerCase();
      let fieldValue: unknown;

      // Direct property access - faster than function map
      switch (metadataField) {
        case 'isbn13':
          fieldValue = metadata?.isbn13;
          break;
        case 'isbn10':
          fieldValue = metadata?.isbn10;
          break;
        case 'asin':
          fieldValue = (metadata as Record<string, unknown>)?.['asin'];
          break;
        case 'goodreadsid':
          fieldValue = metadata?.goodreadsId;
          break;
        case 'comicvineid':
          fieldValue = metadata?.comicvineId;
          break;
        case 'hardcoverid':
          fieldValue = metadata?.hardcoverId;
          break;
        case 'hardcoverbookid':
          fieldValue = metadata?.hardcoverBookId;
          break;
        case 'googleid':
          fieldValue = metadata?.googleId;
          break;
        case 'lubimyczytacid':
          fieldValue = metadata?.lubimyczytacId;
          break;
        case 'ranobedbid':
          fieldValue = metadata?.ranobedbId;
          break;
        case 'amazonrating':
          fieldValue = metadata?.amazonRating;
          break;
        case 'goodreadsrating':
          fieldValue = metadata?.goodreadsRating;
          break;
        case 'hardcoverrating':
          fieldValue = metadata?.hardcoverRating;
          break;
        case 'lubimyczytacrating':
          fieldValue = metadata?.lubimyczytacRating;
          break;
        case 'ranobedbrating':
          fieldValue = metadata?.ranobedbRating;
          break;
        case 'personalrating':
          fieldValue = book.personalRating;
          break;
        case 'amazonreviewcount':
          fieldValue = metadata?.amazonReviewCount;
          break;
        case 'goodreadsreviewcount':
          fieldValue = metadata?.goodreadsReviewCount;
          break;
        case 'hardcoverreviewcount':
          fieldValue = metadata?.hardcoverReviewCount;
          break;
        case 'title':
          fieldValue = metadata?.title;
          break;
        case 'subtitle':
          fieldValue = metadata?.subtitle;
          break;
        case 'publisher':
          fieldValue = metadata?.publisher;
          break;
        case 'publisheddate':
          fieldValue = metadata?.publishedDate;
          break;
        case 'description':
          fieldValue = metadata?.description;
          break;
        case 'seriesname':
          fieldValue = metadata?.seriesName;
          break;
        case 'seriesnumber':
          fieldValue = metadata?.seriesNumber;
          break;
        case 'seriestotal':
          fieldValue = metadata?.seriesTotal;
          break;
        case 'pagecount':
          fieldValue = metadata?.pageCount;
          break;
        case 'language':
          fieldValue = metadata?.language;
          break;
        case 'authors':
          fieldValue = metadata?.authors && metadata.authors.length > 0 ? metadata.authors : null;
          break;
        case 'categories':
          fieldValue = metadata?.categories && metadata.categories.length > 0 ? metadata.categories : null;
          break;
        case 'moods':
          fieldValue = metadata?.moods && metadata.moods.length > 0 ? metadata.moods : null;
          break;
        case 'tags':
          fieldValue = metadata?.tags && metadata.tags.length > 0 ? metadata.tags : null;
          break;
        case 'agerating':
          fieldValue = metadata?.ageRating;
          break;
        case 'contentrating':
          fieldValue = metadata?.contentRating;
          break;
        default:
          fieldValue = null;
      }

      // HAS/MISSING for metadata fields: simply check if field is not null/undefined
      // This matches backend behavior - any non-null value (including empty strings) counts as "has"
      const isEmpty = fieldValue === null || fieldValue === undefined;

      switch (rule.operator) {
        case 'has':
        case 'equals':
          return !isEmpty;
        case 'missing':
        case 'not_equals':
          return isEmpty;
        default:
          return false;
      }
    }

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

    // Fast path for date fields - parse dates only when needed
    const isDateField = rule.field === 'publishedDate' || rule.field === 'dateFinished' || rule.field === 'lastReadTime';
    
    // Optimize value conversion: only convert to lowercase for string comparisons
    let value: unknown;
    let ruleVal: unknown = rule.value;
    let ruleStart: unknown = rule.valueStart;
    let ruleEnd: unknown = rule.valueEnd;
    
    if (isDateField) {
      value = rawValue;
      if (typeof rule.value === 'string') ruleVal = new Date(rule.value);
      if (typeof rule.valueStart === 'string') ruleStart = new Date(rule.valueStart);
      if (typeof rule.valueEnd === 'string') ruleEnd = new Date(rule.valueEnd);
    } else {
      // Only convert to lowercase for actual string operations (contains, starts_with, etc)
      const needsLowercase = rule.operator === 'contains' || rule.operator === 'does_not_contain' || 
                              rule.operator === 'starts_with' || rule.operator === 'ends_with';
      
      if (needsLowercase) {
        value = typeof rawValue === 'string' ? rawValue.toLowerCase() : rawValue;
        ruleVal = typeof rule.value === 'string' ? rule.value.toLowerCase() : rule.value;
      } else {
        value = rawValue;
      }
    }

    const isNumericIdField = rule.field === 'library' || rule.field === 'shelf';

    switch (rule.operator) {
      case 'equals':
        // Special handling for fileType with CBX books
        if (rule.field === 'fileType' && value === 'cbx') {
          const cbxExtensions = ['cbx', 'cbr', 'cbz', 'cb7'];
          return cbxExtensions.includes(String(ruleVal).toLowerCase());
        }
        if (Array.isArray(value)) {
          // Optimize: avoid creating ruleList array, compare directly
          if (Array.isArray(rule.value)) {
            const ruleValueArray = rule.value as unknown[];
            return value.some(v => {
              const vStr = String(v);
              return ruleValueArray.some((rv: unknown) => 
                isNumericIdField ? vStr === String(rv) : vStr.toLowerCase() === String(rv).toLowerCase()
              );
            });
          } else {
            const ruleStr = String(rule.value);
            return value.some(v => 
              isNumericIdField ? String(v) === ruleStr : String(v).toLowerCase() === ruleStr.toLowerCase()
            );
          }
        }
        if (value instanceof Date && ruleVal instanceof Date) {
          return value.getTime() === ruleVal.getTime();
        }
        // Case-insensitive string comparison
        if (typeof value === 'string' && typeof ruleVal === 'string') {
          return value.toLowerCase() === ruleVal.toLowerCase();
        }
        return value === ruleVal;

      case 'not_equals':
        // Special handling for fileType with CBX books
        if (rule.field === 'fileType' && value === 'cbx') {
          const cbxExtensions = ['cbx', 'cbr', 'cbz', 'cb7'];
          return !cbxExtensions.includes(String(ruleVal).toLowerCase());
        }
        if (Array.isArray(value)) {
          // Optimize: avoid creating ruleList array, compare directly
          if (Array.isArray(rule.value)) {
            const ruleValueArray = rule.value as unknown[];
            return value.every(v => {
              const vStr = String(v);
              return ruleValueArray.every((rv: unknown) => 
                isNumericIdField ? vStr !== String(rv) : vStr.toLowerCase() !== String(rv).toLowerCase()
              );
            });
          } else {
            const ruleStr = String(rule.value);
            return value.every(v => 
              isNumericIdField ? String(v) !== ruleStr : String(v).toLowerCase() !== ruleStr.toLowerCase()
            );
          }
        }
        if (value instanceof Date && ruleVal instanceof Date) {
          return value.getTime() !== ruleVal.getTime();
        }
        // Case-insensitive string comparison
        if (typeof value === 'string' && typeof ruleVal === 'string') {
          return value.toLowerCase() !== ruleVal.toLowerCase();
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

      case 'includes_all': {
        const ruleValues = Array.isArray(rule.value) ? rule.value : [rule.value];
        const isNumericField = rule.field === 'library' || rule.field === 'shelf';
        
        // Fast path: avoid creating arrays for scalar fields
        if (rule.field === 'authors' || rule.field === 'categories' || rule.field === 'moods' || rule.field === 'tags') {
          const arr = this.extractBookValue(book, rule.field) as string[];
          return ruleValues.every(rv =>
            arr.some(item => item.toLowerCase() === String(rv).toLowerCase())
          );
        } else if (rule.field === 'title' || rule.field === 'subtitle' || rule.field === 'publisher' || rule.field === 'seriesName' || rule.field === 'language') {
          const val = this.extractBookValue(book, rule.field) as string | null;
          const lowerVal = val?.toLowerCase() ?? '';
          return ruleValues.every(rv => lowerVal === String(rv).toLowerCase());
        }
        
        const bookList = this.getArrayField(book, rule.field);
        return ruleValues.every(rv => {
          const rvStr = isNumericField ? String(rv) : String(rv).toLowerCase();
          return bookList.includes(rvStr);
        });
      }

      case 'excludes_all': {
        const ruleValues = Array.isArray(rule.value) ? rule.value : [rule.value];
        const isNumericField = rule.field === 'library' || rule.field === 'shelf';
        
        // Fast path: avoid creating arrays for scalar fields
        if (rule.field === 'authors' || rule.field === 'categories' || rule.field === 'moods' || rule.field === 'tags') {
          const arr = this.extractBookValue(book, rule.field) as string[];
          return ruleValues.every(rv =>
            !arr.some(item => item.toLowerCase() === String(rv).toLowerCase())
          );
        } else if (rule.field === 'title' || rule.field === 'subtitle' || rule.field === 'publisher' || rule.field === 'seriesName' || rule.field === 'language') {
          const val = this.extractBookValue(book, rule.field) as string | null;
          const lowerVal = val?.toLowerCase() ?? '';
          return ruleValues.every(rv => lowerVal !== String(rv).toLowerCase());
        }
        
        const bookList = this.getArrayField(book, rule.field);
        return ruleValues.every(rv => {
          const rvStr = isNumericField ? String(rv) : String(rv).toLowerCase();
          return !bookList.includes(rvStr);
        });
      }

      case 'includes_any': {
        const ruleValues = Array.isArray(rule.value) ? rule.value : [rule.value];
        const isNumericField = rule.field === 'library' || rule.field === 'shelf';
        
        // Fast path: avoid creating arrays for scalar fields
        if (rule.field === 'authors' || rule.field === 'categories' || rule.field === 'moods' || rule.field === 'tags') {
          const arr = this.extractBookValue(book, rule.field) as string[];
          return ruleValues.some(rv =>
            arr.some(item => item.toLowerCase() === String(rv).toLowerCase())
          );
        } else if (rule.field === 'title' || rule.field === 'subtitle' || rule.field === 'publisher' || rule.field === 'seriesName' || rule.field === 'language') {
          const val = this.extractBookValue(book, rule.field) as string | null;
          const lowerVal = val?.toLowerCase() ?? '';
          return ruleValues.some(rv => lowerVal === String(rv).toLowerCase());
        }
        
        const bookList = this.getArrayField(book, rule.field);
        return ruleValues.some(rv => {
          const rvStr = isNumericField ? String(rv) : String(rv).toLowerCase();
          return bookList.includes(rvStr);
        });
      }

      default:
        return false;
    }
  }

  private extractBookValue(book: Book, field: RuleField): unknown {
    const metadata = book.metadata;
    
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
        return metadata?.title ?? null;
      case 'subtitle':
        return metadata?.subtitle ?? null;
      case 'authors':
        return metadata?.authors ?? [];
      case 'categories':
        return metadata?.categories ?? [];
      case 'moods':
        return metadata?.moods ?? [];
      case 'tags':
        return metadata?.tags ?? [];
      case 'publisher':
        return metadata?.publisher ?? null;
      case 'publishedDate':
        return metadata?.publishedDate ? new Date(metadata.publishedDate) : null;
      case 'dateFinished':
        return book.dateFinished ? new Date(book.dateFinished) : null;
      case 'lastReadTime':
        return book.lastReadTime ? new Date(book.lastReadTime) : null;
      case 'addedOn':
        if (!book.addedOn) return null;
        const addedDate = new Date(book.addedOn);
        const today = new Date();
        const diffTime = today.getTime() - addedDate.getTime();
        const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
        return diffDays;
      case 'seriesName':
        return metadata?.seriesName ?? null;
      case 'seriesNumber':
        return metadata?.seriesNumber;
      case 'seriesTotal':
        return metadata?.seriesTotal;
      case 'pageCount':
        return metadata?.pageCount;
      case 'language':
        return metadata?.language ?? null;
      case 'amazonRating':
        return metadata?.amazonRating;
      case 'amazonReviewCount':
        return metadata?.amazonReviewCount;
      case 'goodreadsRating':
        return metadata?.goodreadsRating;
      case 'goodreadsReviewCount':
        return metadata?.goodreadsReviewCount;
      case 'hardcoverRating':
        return metadata?.hardcoverRating;
      case 'hardcoverReviewCount':
        return metadata?.hardcoverReviewCount;
      case 'lubimyczytacRating':
        return metadata?.lubimyczytacRating;
      case 'ranobedbRating':
        return metadata?.ranobedbRating;
      case 'incompleteSeries':
        return book.incompleteSeries ?? false;
      case 'seriesStatus':
        return this.getSeriesStatus(book);
      case 'metadata':
        return null;
      case 'isbn10':
        return metadata?.isbn10;
      case 'isbn13':
        return metadata?.isbn13;
      case 'asin':
        return (metadata as Record<string, unknown>)?.['asin'];
      case 'goodreadsId':
        return metadata?.goodreadsId;
      case 'comicvineId':
        return metadata?.comicvineId;
      case 'hardcoverId':
        return metadata?.hardcoverId;
      case 'hardcoverBookId':
        return metadata?.hardcoverBookId;
      case 'googleId':
        return metadata?.googleId;
      case 'lubimyczytacId':
        return metadata?.lubimyczytacId;
      case 'ranobedbId':
        return metadata?.ranobedbId;
      default:
        return (book as Record<string, unknown>)[field];
    }
  }

  /**
   * Gets the series status for a book.
   * Returns 'reading' if the book is in a series and any book in that series has readStatus of READ or READING,
   * 'completed' if seriesNumber === seriesTotal,
   * 'ongoing' if the book is in a series (has seriesName),
   * or an empty string if not in a series.
   */
  private getSeriesStatus(book: Book): string {
    const metadata = book.metadata;
    const seriesName = metadata?.seriesName;
    if (!seriesName) {
      return ''; // Not in a series
    }

    // Check if any book in this series is being read or has been read
    const lowerSeriesName = seriesName.toLowerCase();
    const isSeriesBeingRead = this.allBooks.some(b => {
      const bSeriesName = b.metadata?.seriesName;
      return bSeriesName && bSeriesName.toLowerCase() === lowerSeriesName &&
        (b.readStatus === 'READ' || b.readStatus === 'READING');
    });
    
    if (isSeriesBeingRead) {
      return 'reading';
    }

    const seriesNumber = metadata?.seriesNumber;
    const seriesTotal = metadata?.seriesTotal;

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



  /**
   * Get array field values for array-based operators
   */
  private getArrayField(book: Book, field: RuleField): string[] {
    const metadata = book.metadata;
    
    switch (field) {
      case 'authors':
        return (metadata?.authors ?? []).map(a => a.toLowerCase());
      case 'categories':
        return (metadata?.categories ?? []).map(c => c.toLowerCase());
      case 'moods':
        return (metadata?.moods ?? []).map(m => m.toLowerCase());
      case 'tags':
        return (metadata?.tags ?? []).map(t => t.toLowerCase());
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
        return metadata?.language ? [metadata.language.toLowerCase()] : [];
      case 'title':
        return metadata?.title ? [metadata.title.toLowerCase()] : [];
      case 'subtitle':
        return metadata?.subtitle ? [metadata.subtitle.toLowerCase()] : [];
      case 'publisher':
        return metadata?.publisher ? [metadata.publisher.toLowerCase()] : [];
      case 'seriesName':
        return metadata?.seriesName ? [metadata.seriesName.toLowerCase()] : [];
      case 'incompleteSeries':
        return [String(book.incompleteSeries ?? false)];
      case 'seriesStatus':
        return [this.getSeriesStatus(book)];
      default:
        return [];
    }
  }
}
