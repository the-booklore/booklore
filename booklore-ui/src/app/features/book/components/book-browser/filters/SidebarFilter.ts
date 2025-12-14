import {Observable, combineLatest, of} from 'rxjs';
import {map} from 'rxjs/operators';
import {BookFilter} from './BookFilter';
import {BookState} from '../../../model/state/book-state.model';
import {fileSizeRanges, matchScoreRanges, pageCountRanges, ratingRanges} from '../book-filter/book-filter.component';
import {Book, ReadStatus} from '../../../model/book.model';
import {BookFilterMode} from '../../../../settings/user-management/user.service';

export function isRatingInRange(rating: number | undefined | null, rangeId: string): boolean {
  if (rating == null) return false;
  const range = ratingRanges.find(r => r.id === rangeId);
  if (!range) return false;
  return rating >= range.min && rating < range.max;
}

export function isRatingInRange10(rating: number | undefined | null, rangeId: string): boolean {
  if (rating == null) return false;
  return `${Math.round(rating)}` === rangeId;
}

export function isFileSizeInRange(fileSizeKb: number | undefined, rangeId: string): boolean {
  if (fileSizeKb == null) return false;
  const range = fileSizeRanges.find(r => r.id === rangeId);
  if (!range) return false;
  return fileSizeKb >= range.min && fileSizeKb < range.max;
}

export function isPageCountInRange(pageCount: number | undefined, rangeId: string): boolean {
  if (pageCount == null) return false;
  const range = pageCountRanges.find(r => r.id === rangeId);
  if (!range) return false;
  return pageCount >= range.min && pageCount < range.max;
}

export function isMatchScoreInRange(score: number | undefined | null, rangeId: string): boolean {
  if (score == null) return false;
  const normalizedScore = score > 1 ? score / 100 : score;
  const range = matchScoreRanges.find(r => r.id === rangeId);
  if (!range) return false;
  return normalizedScore >= range.min && normalizedScore < range.max;
}

export function doesBookMatchReadStatus(book: Book, selected: string[]): boolean {
  const status = book.readStatus ?? ReadStatus.UNSET;
  return selected.includes(status);
}

export class SideBarFilter implements BookFilter {

  constructor(private selectedFilter$: Observable<any>, private selectedFilterMode$: Observable<BookFilterMode>) {
  }

  filter(bookState: BookState): Observable<BookState> {
    return combineLatest([this.selectedFilter$, this.selectedFilterMode$]).pipe(
      map(([activeFilters, mode]) => {
        if (!activeFilters) return bookState;
        const filteredBooks = (bookState.books || []).filter(book => {
          const matches = Object.entries(activeFilters).map(([filterType, filterValues]) => {
            if (!Array.isArray(filterValues) || filterValues.length === 0) {
              return mode === 'or';
            }
            switch (filterType) {
              case 'author':
                return mode === 'or'
                  ? filterValues.some(val => book.metadata?.authors?.includes(val))
                  : filterValues.every(val => book.metadata?.authors?.includes(val));
              case 'category':
                return mode === 'or'
                  ? filterValues.some(val => book.metadata?.categories?.includes(val))
                  : filterValues.every(val => book.metadata?.categories?.includes(val));
              case 'mood':
                return mode === 'or'
                  ? filterValues.some(val => book.metadata?.moods?.includes(val))
                  : filterValues.every(val => book.metadata?.moods?.includes(val));
              case 'tag':
                return mode === 'or'
                  ? filterValues.some(val => book.metadata?.tags?.includes(val))
                  : filterValues.every(val => book.metadata?.tags?.includes(val));
              case 'publisher':
                return mode === 'or'
                  ? filterValues.some(val => book.metadata?.publisher === val)
                  : filterValues.every(val => book.metadata?.publisher === val);
              case 'series':
                return mode === 'or'
                  ? filterValues.some(val => book.metadata?.seriesName === val)
                  : filterValues.every(val => book.metadata?.seriesName === val);
              case 'readStatus':
                return doesBookMatchReadStatus(book, filterValues);
              case 'amazonRating':
                return filterValues.some(range => isRatingInRange(book.metadata?.amazonRating, range));
              case 'goodreadsRating':
                return filterValues.some(range => isRatingInRange(book.metadata?.goodreadsRating, range));
              case 'hardcoverRating':
                return filterValues.some(range => isRatingInRange(book.metadata?.hardcoverRating, range));
              case 'personalRating':
                return filterValues.some(range => isRatingInRange10(book.personalRating, range));
              case 'publishedDate':
                const bookYear = book.metadata?.publishedDate
                  ? new Date(book.metadata.publishedDate).getFullYear()
                  : null;
                return bookYear ? filterValues.some(val => val == bookYear || val == bookYear.toString()) : false;
              case 'fileSize':
                return filterValues.some(range => isFileSizeInRange(book.fileSizeKb, range));
              case 'shelfStatus':
                const shelved = book.shelves && book.shelves.length > 0 ? 'shelved' : 'unshelved';
                return filterValues.includes(shelved);
              case 'pageCount':
                return filterValues.some(range => isPageCountInRange(book.metadata?.pageCount!, range));
              case 'language':
                return filterValues.includes(book.metadata?.language);
              case 'matchScore':
                return filterValues.some(range => isMatchScoreInRange(book.metadataMatchScore, range));
              case 'bookType':
                return filterValues.includes(book.bookType);
              default:
                return false;
            }
          });
          return mode === 'or' ? matches.some(m => m) : matches.every(m => m);
        });
        return {...bookState, books: filteredBooks};
      })
    );
  }
}
