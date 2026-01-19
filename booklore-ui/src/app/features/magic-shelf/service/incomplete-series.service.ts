import { Injectable } from "@angular/core";
import { Book } from "../../book/model/book.model";

@Injectable({ providedIn: "root" })
export class IncompleteSeriesService {
  /**
   * Checks if a book is part of an incomplete series based on all books provided
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
      (b) => b.metadata?.seriesName?.toLowerCase() === seriesName.toLowerCase(),
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

    // Find the maximum series number
    const maxNumber = Math.max(...presentNumbers);

    // Get missing numbers
    const missingNumbers = this.findMissingNumbers(presentNumbers, maxNumber);

    // Series is incomplete if there are missing numbers
    return missingNumbers.length > 0;
  }

  /**
   * Finds missing series numbers between 1 and max
   * Handles both integer series (1, 2, 3) and decimal series (1, 1.5, 2, 2.5)
   */
  private findMissingNumbers(presentNumbers: number[], max: number): number[] {
    const hasDecimals = presentNumbers.some((n) => n % 1 !== 0);

    if (!hasDecimals) {
      // Integer series: check for gaps from 1 to max
      const missing: number[] = [];
      for (let i = 1; i <= Math.floor(max); i++) {
        if (!presentNumbers.includes(i)) {
          missing.push(i);
        }
      }
      return missing;
    } else {
      // Decimal series: more complex logic
      // For each integer position, check if there's at least one variant (integer or decimal)
      const missing: number[] = [];
      for (let i = 1; i <= Math.floor(max); i++) {
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
