import {Injectable} from '@angular/core';
import {Book} from './book/model/book.model';
import {GroupRule, Rule, RuleField, RuleOperator} from './magic-shelf-component/magic-shelf-component';

@Injectable({providedIn: 'root'})
export class BookRuleEvaluatorService {

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

    const normalize = (val: any): any => {
      if (val === null || val === undefined) return val;
      if (val instanceof Date) return val;
      if (typeof val === 'string') {
        const date = new Date(val);
        if (!isNaN(date.getTime())) return date;
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
          return [String(book.readStatus).toLowerCase()];
        case 'fileType':
          return [String(this.getFileExtension(book.fileName) ?? '').toLowerCase()];
        case 'library':
          return [String(book.libraryId)];
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
        default:
          return [];
      }
    };

    const ruleList = Array.isArray(rule.value)
      ? rule.value.map(v => String(v).toLowerCase())
      : (rule.value ? [String(rule.value).toLowerCase()] : []);

    switch (rule.operator) {
      case 'equals':
        if (Array.isArray(value)) {
          return value.some(v => ruleList.includes(v));
        }
        if (value instanceof Date && ruleVal instanceof Date) {
          return value.getTime() === ruleVal.getTime();
        }
        return value === ruleVal;

      case 'not_equals':
        if (Array.isArray(value)) {
          return value.every(v => !ruleList.includes(v));
        }
        if (value instanceof Date && ruleVal instanceof Date) {
          return value.getTime() !== ruleVal.getTime();
        }
        return value !== ruleVal;

      case 'contains':
        if (typeof value !== 'string') return false;
        if (typeof ruleVal !== 'string') return false;
        return value.includes(ruleVal);

      case 'does_not_contain':
        if (typeof value !== 'string') return true;
        if (typeof ruleVal !== 'string') return true;
        return !value.includes(ruleVal);

      case 'starts_with':
        if (typeof value !== 'string') return false;
        if (typeof ruleVal !== 'string') return false;
        return value.startsWith(ruleVal);

      case 'ends_with':
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

  private extractBookValue(book: Book, field: RuleField): any {
    switch (field) {
      case 'library':
        return book.libraryId;
      case 'readStatus':
        return book.readStatus;
      case 'fileType':
        return this.getFileExtension(book.fileName)?.toLowerCase() ?? null;
      case 'fileSize':
        return book.fileSizeKb;
      case 'metadataScore':
        return book.metadataMatchScore;
      case 'personalRating':
        return book.metadata?.personalRating;
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
      default:
        return (book as any)[field];
    }
  }

  private getFileExtension(filePath?: string): string | null {
    if (!filePath) return null;
    const parts = filePath.split('.');
    if (parts.length < 2) return null;
    return parts.pop() ?? null;
  }
}
