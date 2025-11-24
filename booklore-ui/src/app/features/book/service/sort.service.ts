import {Injectable} from '@angular/core';
import {Book} from '../model/book.model';
import {SortDirection, SortOption} from "../model/sort.model";

@Injectable({
  providedIn: 'root',
})
export class SortService {

  private naturalCompare(a: string, b: string): number {
    if (a == null && b == null) return 0;
    if (a == null) return 1;
    if (b == null) return -1;

    const aStr = a.toString();
    const bStr = b.toString();

    const chunkRegex = /(\d+|\D+)/g;

    const aChunks = aStr.match(chunkRegex) || [aStr];
    const bChunks = bStr.match(chunkRegex) || [bStr];

    const maxLength = Math.max(aChunks.length, bChunks.length);

    for (let i = 0; i < maxLength; i++) {
      const aChunk = aChunks[i] || '';
      const bChunk = bChunks[i] || '';

      if (aChunk === '' && bChunk === '') continue;

      const aIsNumeric = /^\d+$/.test(aChunk);
      const bIsNumeric = /^\d+$/.test(bChunk);

      if (aIsNumeric && bIsNumeric) {
        const aNum = parseInt(aChunk, 10);
        const bNum = parseInt(bChunk, 10);
        if (aNum !== bNum) {
          return aNum - bNum;
        }
      } else {
        const comparison = aChunk.localeCompare(bChunk);
        if (comparison !== 0) {
          return comparison;
        }
      }
    }

    return aChunks.length - bChunks.length;
  }

  private readonly fieldExtractors: Record<string, (book: Book) => unknown> = {
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
    publishedDate: (book) => {
      const date = book.metadata?.publishedDate;
      return date === null || date === undefined ? null : new Date(date).getTime();
    },
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
    fileSizeKb: (book) => book.fileSizeKb || null,
    fileName:(book) => book.fileName,
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
        // For titleSeries: [seriesName, seriesNumber]
        const nameCompare = this.naturalCompare(aValue[0] || '', bValue[0] || '');
        result = nameCompare !== 0 ? nameCompare : (aValue[1] - bValue[1]);
      } else if (typeof aValue === 'string' && typeof bValue === 'string') {
        result = this.naturalCompare(aValue, bValue);
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
