import { Injectable } from "@angular/core";
import { Book } from "../../book/model/book.model";

@Injectable({ providedIn: "root" })
export class IncompleteSeriesService {
  /**
   * Computes incomplete series status for all books in O(N) time
   * @param allBooks All books in the library/collection
   * @returns Map of book IDs to their incomplete series status
   */
  computeIncompleteSeriesForAll(allBooks: Book[]): Map<number, boolean> {
    const result = new Map<number, boolean>();

    if (!allBooks || allBooks.length === 0) {
      return result;
    }

    const seriesMap = new Map<string, Book[]>();

    for (const book of allBooks) {
      const seriesName = book.metadata?.seriesName;
      if (!seriesName) {
        result.set(book.id, false); // Books without series are not incomplete
        continue;
      }

      const key = seriesName.trim().toLowerCase();
      if (!seriesMap.has(key)) {
        seriesMap.set(key, []);
      }
      seriesMap.get(key)!.push(book);
    }

    const incompleteSeriesNames = new Set<string>();

    for (const [seriesKey, seriesBooks] of seriesMap.entries()) {
      const presentNumbers = seriesBooks
        .map((b) => b.metadata?.seriesNumber)
        .filter((n): n is number => n != null);

      if (presentNumbers.length === 0) {
        continue;
      }

      const minNumber = Math.min(...presentNumbers);
      const maxNumber = Math.max(...presentNumbers);
      
      // Series can start at 0 or 1 (with small tolerance for float comparison)
      const startsAtZeroOrOne = Math.abs(minNumber - 0.0) < 0.01 || Math.abs(minNumber - 1.0) < 0.01;
      const missingNumbers = this.findMissingNumbers(presentNumbers, minNumber, maxNumber);

      if (!startsAtZeroOrOne || missingNumbers.length > 0) {
        incompleteSeriesNames.add(seriesKey);
      }
    }

    for (const book of allBooks) {
      const seriesName = book.metadata?.seriesName;
      const seriesNumber = book.metadata?.seriesNumber;
      if (seriesName && seriesNumber != null) {
        const isIncomplete = incompleteSeriesNames.has(
          seriesName.trim().toLowerCase(),
        );
        result.set(book.id, isIncomplete);
      } else {
        // Align with backend: books without series name or series number are not considered incomplete
        result.set(book.id, false);
      }
    }

    return result;
  }

  /**
   * Checks if a book is part of an incomplete series based on all books provided
   * @deprecated Use computeIncompleteSeriesForAll for better performance
   * @param book The book to check
   * @param allBooks All books in the library/collection
   * @returns true if the book belongs to an incomplete series
   */
  isBookInIncompleteSeries(book: Book, allBooks: Book[]): boolean {
    const seriesName = book.metadata?.seriesName;
    if (!seriesName) {
      return false; // Books without series are not "incomplete"
    }

    // Get all books in the same series
    const seriesBooks = allBooks.filter(
      (b) => b.metadata?.seriesName?.trim().toLowerCase() === seriesName.trim().toLowerCase(),
    );

    if (seriesBooks.length === 0) {
      return false;
    }

    // Extract all series numbers from books in this series
    const presentNumbers = seriesBooks
      .map((b) => b.metadata?.seriesNumber)
      .filter((n): n is number => n != null);

    if (presentNumbers.length === 0) {
      return false; // Can't determine if incomplete without series numbers
    }

    // Find the minimum and maximum series numbers
    const minNumber = Math.min(...presentNumbers);
    const maxNumber = Math.max(...presentNumbers);

    // Series can start at 0 or 1 (with small tolerance for float comparison)
    const startsAtZeroOrOne = Math.abs(minNumber - 0.0) < 0.01 || Math.abs(minNumber - 1.0) < 0.01;

    // Get missing numbers
    const missingNumbers = this.findMissingNumbers(presentNumbers, minNumber, maxNumber);

    // Series is incomplete if it doesn't start at 0 or 1, or has missing numbers
    return !startsAtZeroOrOne || missingNumbers.length > 0;
  }

  /**
   * Finds missing series numbers between min and max
   * Handles both integer series (0, 1, 2 or 1, 2, 3) and decimal series (0, 0.5, 1, 1.5 or 1, 1.5, 2, 2.5)
   */
  private findMissingNumbers(presentNumbers: number[], min: number, max: number): number[] {
    const hasDecimals = presentNumbers.some((n) => n % 1 !== 0);
    const startInt = Math.ceil(min); // Use ceil to handle min values like 0.5 correctly
    const endInt = Math.floor(max);

    if (!hasDecimals) {
      // Integer series: check for gaps from min to max
      const missing: number[] = [];
      for (let i = startInt; i <= endInt; i++) {
        if (!presentNumbers.includes(i)) {
          missing.push(i);
        }
      }
      return missing;
    } else {
      // Decimal series: more complex logic
      // For each integer position, check if there's at least one variant (integer or decimal)
      const missing: number[] = [];
      for (let i = startInt; i <= endInt; i++) {
        const hasVariant = presentNumbers.some(
          (n) => Math.floor(n) === i || n === i,
        );
        if (!hasVariant) {
          missing.push(i);
        }
      }
      return missing;
    }
  }
}
