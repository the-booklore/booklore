import { beforeEach, describe, expect, it } from "vitest";
import { TestBed } from "@angular/core/testing";
import { IncompleteSeriesService } from "./incomplete-series.service";
import { Book } from "../../book/model/book.model";

describe("IncompleteSeriesService", () => {
  let service: IncompleteSeriesService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(IncompleteSeriesService);
  });

  it("should be created", () => {
    expect(service).toBeTruthy();
  });

  it("should return false for books without series", () => {
    const book: Book = {
      id: 1,
      metadata: {},
    } as Book;

    const result = service.isBookInIncompleteSeries(book, [book]);
    expect(result).toBe(false);
  });

  it("should return false for complete integer series (1,2,3,4,5)", () => {
    const books: Book[] = [
      {
        id: 1,
        metadata: { seriesName: "Test Series", seriesNumber: 1 },
      } as Book,
      {
        id: 2,
        metadata: { seriesName: "Test Series", seriesNumber: 2 },
      } as Book,
      {
        id: 3,
        metadata: { seriesName: "Test Series", seriesNumber: 3 },
      } as Book,
      {
        id: 4,
        metadata: { seriesName: "Test Series", seriesNumber: 4 },
      } as Book,
      {
        id: 5,
        metadata: { seriesName: "Test Series", seriesNumber: 5 },
      } as Book,
    ];

    const result = service.isBookInIncompleteSeries(books[0], books);
    expect(result).toBe(false);
  });

  it("should return true for incomplete integer series (1,2,5 missing 3,4)", () => {
    const books: Book[] = [
      {
        id: 1,
        metadata: { seriesName: "Test Series", seriesNumber: 1 },
      } as Book,
      {
        id: 2,
        metadata: { seriesName: "Test Series", seriesNumber: 2 },
      } as Book,
      {
        id: 5,
        metadata: { seriesName: "Test Series", seriesNumber: 5 },
      } as Book,
    ];

    const result = service.isBookInIncompleteSeries(books[0], books);
    expect(result).toBe(true);
  });

  it("should return true for incomplete series with gap at start (3,4,5 missing 1,2)", () => {
    const books: Book[] = [
      {
        id: 3,
        metadata: { seriesName: "Test Series", seriesNumber: 3 },
      } as Book,
      {
        id: 4,
        metadata: { seriesName: "Test Series", seriesNumber: 4 },
      } as Book,
      {
        id: 5,
        metadata: { seriesName: "Test Series", seriesNumber: 5 },
      } as Book,
    ];

    const result = service.isBookInIncompleteSeries(books[0], books);
    expect(result).toBe(true);
  });

  it("should handle decimal series numbers (1, 1.5, 2, 2.22)", () => {
    const books: Book[] = [
      {
        id: 1,
        metadata: { seriesName: "Test Series", seriesNumber: 1 },
      } as Book,
      {
        id: 2,
        metadata: { seriesName: "Test Series", seriesNumber: 1.5 },
      } as Book,
      {
        id: 3,
        metadata: { seriesName: "Test Series", seriesNumber: 2 },
      } as Book,
      {
        id: 4,
        metadata: { seriesName: "Test Series", seriesNumber: 2.22 },
      } as Book,
    ];

    // Complete for decimal series - all integer positions covered
    const result = service.isBookInIncompleteSeries(books[0], books);
    expect(result).toBe(false);
  });

  it("should detect incomplete decimal series (1, 1.5, 3.5 missing position 2)", () => {
    const books: Book[] = [
      {
        id: 1,
        metadata: { seriesName: "Test Series", seriesNumber: 1 },
      } as Book,
      {
        id: 2,
        metadata: { seriesName: "Test Series", seriesNumber: 1.5 },
      } as Book,
      {
        id: 3,
        metadata: { seriesName: "Test Series", seriesNumber: 3.5 },
      } as Book,
    ];

    const result = service.isBookInIncompleteSeries(books[0], books);
    expect(result).toBe(true);
  });

  it("should handle different series names case-insensitively", () => {
    const books: Book[] = [
      {
        id: 1,
        metadata: { seriesName: "Test Series", seriesNumber: 1 },
      } as Book,
      {
        id: 2,
        metadata: { seriesName: "test series", seriesNumber: 2 },
      } as Book,
      {
        id: 3,
        metadata: { seriesName: "TEST SERIES", seriesNumber: 5 },
      } as Book,
    ];

    const result = service.isBookInIncompleteSeries(books[0], books);
    expect(result).toBe(true); // Missing 3, 4
  });

  it("should return false for books without series numbers", () => {
    const books: Book[] = [
      { id: 1, metadata: { seriesName: "Test Series" } } as Book,
      { id: 2, metadata: { seriesName: "Test Series" } } as Book,
    ];

    const result = service.isBookInIncompleteSeries(books[0], books);
    expect(result).toBe(false);
  });

  it("should handle single book in series", () => {
    const books: Book[] = [
      {
        id: 1,
        metadata: { seriesName: "Test Series", seriesNumber: 1 },
      } as Book,
    ];

    // Single book at position 1 is complete (no missing numbers)
    const result = service.isBookInIncompleteSeries(books[0], books);
    expect(result).toBe(false);
  });

  it("should handle single book not at position 1", () => {
    const books: Book[] = [
      {
        id: 1,
        metadata: { seriesName: "Test Series", seriesNumber: 5 },
      } as Book,
    ];

    // Single book at position 5 is incomplete (missing 1-4)
    const result = service.isBookInIncompleteSeries(books[0], books);
    expect(result).toBe(true);
  });

  it("should not cross-pollute different series", () => {
    const books: Book[] = [
      { id: 1, metadata: { seriesName: "Series A", seriesNumber: 1 } } as Book,
      { id: 2, metadata: { seriesName: "Series A", seriesNumber: 3 } } as Book,
      { id: 3, metadata: { seriesName: "Series B", seriesNumber: 2 } } as Book,
    ];

    const resultA = service.isBookInIncompleteSeries(books[0], books);
    expect(resultA).toBe(true); // Series A missing position 2

    const resultB = service.isBookInIncompleteSeries(books[2], books);
    expect(resultB).toBe(true); // Series B missing position 1
  });
});
