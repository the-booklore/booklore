import {Injectable} from '@angular/core';
import {Book} from '../model/book.model';
import {SortDirection, SortOption} from "../model/sort.model";

@Injectable({
  providedIn: 'root',
})
export class SortService {

  private readonly fieldExtractors: Record<string, (book: Book) => any> = {
    title: (book) => (book.seriesCount ? (book.metadata?.seriesName?.toLowerCase() || null) : null)
      ?? (book.metadata?.title?.toLowerCase() || null),
    titleSeries: (book) => {
      const title = book.metadata?.title?.toLowerCase() || '';
      const series = book.metadata?.seriesName?.toLowerCase();
      const seriesNumber = book.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
      if (series) {
        return [series, seriesNumber];
      }
      return [title, Number.MAX_SAFE_INTEGER];
    },
    author: (book) => book.metadata?.authors?.map(a => a.toLowerCase()).join(", ") || null,
    publishedDate: (book) => book.metadata?.publishedDate === null ? null : new Date(book.metadata?.publishedDate!).getTime(),
    publisher: (book) => book.metadata?.publisher || null,
    pageCount: (book) => book.metadata?.pageCount || null,
    rating: (book) => book.metadata?.rating || null,
    personalRating: (book) => book.metadata?.personalRating || null,
    reviewCount: (book) => book.metadata?.reviewCount || null,
    amazonRating: (book) => book.metadata?.amazonRating || null,
    amazonReviewCount: (book) => book.metadata?.amazonReviewCount || null,
    goodreadsRating: (book) => book.metadata?.goodreadsRating || null,
    goodreadsReviewCount: (book) => book.metadata?.goodreadsReviewCount || null,
    hardcoverRating: (book) => book.metadata?.hardcoverRating || null,
    hardcoverReviewCount: (book) => book.metadata?.hardcoverReviewCount || null,
    locked: (book) =>
      Object.keys(book.metadata ?? {})
        .filter((key) => key.endsWith('Locked'))
        .every((key) => book.metadata?.[key] === true),
    lastReadTime: (book) => book.lastReadTime ? new Date(book.lastReadTime).getTime() : null,
    addedOn: (book) => book.addedOn ? new Date(book.addedOn).getTime() : null,
    fileSizeKb: (book) => book.fileSizeKb || null
  };

  applySort(books: Book[], selectedSort: SortOption | null): Book[] {
    if (!selectedSort) return books;

    const {field, direction} = selectedSort;
    const extractor = this.fieldExtractors[field];

    if (!extractor) {
      console.warn(`[SortService] No extractor for field: ${field}`);
      return books;
    }

    return books.slice().sort((a, b) => {
      const aValue = extractor(a);
      const bValue = extractor(b);

      let result: number;

      if (Array.isArray(aValue) && Array.isArray(bValue)) {
        const nameCompare = aValue[0]?.localeCompare?.(bValue[0]) ?? 0;
        result = nameCompare !== 0 ? nameCompare : (aValue[1] - bValue[1]);
      } else if (typeof aValue === 'string' && typeof bValue === 'string') {
        result = aValue.localeCompare(bValue);
      } else if (typeof aValue === 'number' && typeof bValue === 'number') {
        result = aValue - bValue;
      } else {
        // Handle nulls or mismatches
        if (aValue == null && bValue != null) return 1;
        if (aValue != null && bValue == null) return -1;
        return 0;
      }

      return direction === SortDirection.ASCENDING ? result : -result;
    });
  }
}
