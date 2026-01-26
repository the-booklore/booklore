import {beforeEach, describe, expect, it, vi} from 'vitest';
import {SortService} from './sort.service';
import {Book} from '../model/book.model';
import {SortDirection} from '../model/sort.model';

function makeBook(partial: Partial<Book>): Book {
  return {
    id: 1,
    bookType: "PDF",
    libraryId: 1,
    libraryName: "Lib",
    ...partial,
  } as Book;
}

describe('SortService', () => {
  let service: SortService;
  let books: Book[];

  beforeEach(() => {
    service = new SortService();
    books = [
      makeBook({
        id: 1,
        fileName: 'Book10.pdf',
        metadata: {
          bookId: 1,
          title: 'Book 10',
          authors: ['Alice'],
          publishedDate: '2020-01-01',
          seriesName: 'Series A',
          seriesNumber: 2,
          rating: 4.5,
          reviewCount: 10,
        },
        personalRating: 3,
        addedOn: '2021-01-01T00:00:00Z',
        lastReadTime: '2022-01-01T00:00:00Z',
        fileSizeKb: 1000,
      }),
      makeBook({
        id: 2,
        fileName: 'Book2.pdf',
        metadata: {
          bookId: 2,
          title: 'Book 2',
          authors: ['Bob'],
          publishedDate: '2019-01-01',
          seriesName: 'Series A',
          seriesNumber: 1,
          rating: 4.8,
          reviewCount: 20,
        },
        personalRating: 5,
        addedOn: '2020-01-01T00:00:00Z',
        lastReadTime: '2023-01-01T00:00:00Z',
        fileSizeKb: 2000,
      }),
      makeBook({
        id: 3,
        fileName: 'Book1.pdf',
        metadata: {
          bookId: 3,
          title: 'Book 1',
          authors: ['Alice'],
          publishedDate: '2021-01-01',
          rating: 4.0,
          reviewCount: 5,
        },
        personalRating: 4,
        addedOn: '2022-01-01T00:00:00Z',
        lastReadTime: '2021-01-01T00:00:00Z',
        fileSizeKb: 500,
      }),
    ];
  });

  it('should sort by title (natural order)', () => {
    const sorted = service.applySort(books, {field: 'title', direction: SortDirection.ASCENDING, label: 'Title'});
    expect(sorted.map(b => b.metadata?.title)).toEqual(['Book 1', 'Book 2', 'Book 10']);
  });

  it('should sort by author', () => {
    const sorted = service.applySort(books, {field: 'author', direction: SortDirection.ASCENDING, label: 'Author'});
    expect(sorted.map(b => b.metadata?.authors?.[0])).toEqual(['Alice', 'Alice', 'Bob']);
  });

  it('should sort by author surname', () => {
    const testBooks = [
      makeBook({ id: 1, metadata: { bookId: 1, title: 'T1', authors: ['Terry Pratchett'] } }),
      makeBook({ id: 2, metadata: { bookId: 2, title: 'T2', authors: ['Neil Gaiman'] } }),
      makeBook({ id: 3, metadata: { bookId: 3, title: 'T3', authors: ['Stephen King'] } }),
    ];

    const sorted = service.applySort(testBooks, { field: 'authorSurnameVorname', direction: SortDirection.ASCENDING, label: 'Author (Surname)' });
    expect(sorted.map(b => b.metadata?.authors?.[0])).toEqual(['Neil Gaiman', 'Stephen King', 'Terry Pratchett']);
  });

  it('should sort by publishedDate descending', () => {
    const sorted = service.applySort(books, {field: 'publishedDate', direction: SortDirection.DESCENDING, label: 'Published Date'});
    expect(sorted.map(b => b.metadata?.title)).toEqual(['Book 1', 'Book 10', 'Book 2']);
  });

  it('should sort by series (titleSeries)', () => {
    const sorted = service.applySort(books, {field: 'titleSeries', direction: SortDirection.ASCENDING, label: 'Series'});
    expect(sorted.map(b => b.metadata?.title)).toEqual(['Book 1', 'Book 2', 'Book 10']);
  });

  it('should sort by personalRating descending', () => {
    const sorted = service.applySort(books, {field: 'personalRating', direction: SortDirection.DESCENDING, label: 'Personal Rating'});
    expect(sorted.map(b => b.personalRating)).toEqual([5, 4, 3]);
  });

  it('should sort by fileName (natural order)', () => {
    const sorted = service.applySort(books, {field: 'fileName', direction: SortDirection.ASCENDING, label: 'File Name'});
    expect(sorted.map(b => b.fileName)).toEqual(['Book1.pdf', 'Book2.pdf', 'Book10.pdf']);
  });

  it('should sort by fileSizeKb ascending', () => {
    const sorted = service.applySort(books, {field: 'fileSizeKb', direction: SortDirection.ASCENDING, label: 'File Size'});
    expect(sorted.map(b => b.fileSizeKb)).toEqual([500, 1000, 2000]);
  });

  it('should sort by addedOn descending', () => {
    const sorted = service.applySort(books, {field: 'addedOn', direction: SortDirection.DESCENDING, label: 'Added On'});
    expect(sorted.map(b => b.addedOn)).toEqual([
      '2022-01-01T00:00:00Z',
      '2021-01-01T00:00:00Z',
      '2020-01-01T00:00:00Z',
    ]);
  });

  it('should handle missing extractor gracefully', () => {
    const spy = vi.spyOn(console, 'warn').mockImplementation(() => {
    });
    const sorted = service.applySort(books, {field: 'nonexistent', direction: SortDirection.ASCENDING, label: 'Nonexistent'} as any);
    expect(sorted).toBe(books);
    expect(spy).toHaveBeenCalled();
    spy.mockRestore();
  });

  it('should return original array if no sort option', () => {
    const sorted = service.applySort(books, null);
    expect(sorted).toBe(books);
  });

  it('should sort by random (order is not predictable)', () => {
    const sorted = service.applySort(books, {field: 'random', direction: SortDirection.ASCENDING, label: 'Random'});
    expect(sorted.length).toBe(3);
    // Can't assert order, but should be a permutation of the input
    expect(sorted.map(b => b.id).sort()).toEqual([1, 2, 3]);
  });
});
