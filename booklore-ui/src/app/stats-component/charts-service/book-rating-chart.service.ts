import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';
import {ChartConfiguration, ChartData, ChartType} from 'chart.js';

interface RatingStats {
  ratingRange: string;
  count: number;
  averageRating: number;
}

const CHART_COLORS = [
  '#DC2626', // Red (1.0-1.9)
  '#EA580C', // Red-orange (2.0-2.9)
  '#F59E0B', // Orange (3.0-3.9)
  '#16A34A', // Green (4.0-4.5)
  '#2563EB'  // Blue (4.6-5.0)
] as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: '#ffffff'
} as const;

const RATING_RANGES = [
  {range: '1.0-1.9', min: 1.0, max: 1.9},
  {range: '2.0-2.9', min: 2.0, max: 2.9},
  {range: '3.0-3.9', min: 3.0, max: 3.9},
  {range: '4.0-4.5', min: 4.0, max: 4.5},
  {range: '4.6-5.0', min: 4.6, max: 5.0},
  {range: 'No Rating', min: 0, max: 0}
] as const;

type RatingChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class BookRatingChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly ratingChartType: ChartType = 'bar';

  public readonly ratingChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false,
        labels: {
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        }
      },
      tooltip: {
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#666666',
        borderWidth: 1,
        callbacks: {
          title: (context) => `External Rating Range: ${context[0].label}`,
          label: (context) => {
            const value = context.parsed.y;
            return `${value} book${value === 1 ? '' : 's'}`;
          }
        }
      }
    },
    scales: {
      x: {
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {color: 'rgba(255, 255, 255, 0.1)'},
        title: {
          display: true,
          text: 'External Rating Range',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          }
        }
      },
      y: {
        beginAtZero: true,
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
          stepSize: 1
        },
        grid: {color: 'rgba(255, 255, 255, 0.05)'},
        title: {
          display: true,
          text: 'Number of Books',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          },
        }
      }
    }
  };

  private readonly ratingChartDataSubject = new BehaviorSubject<RatingChartData>({
    labels: [],
    datasets: [{
      label: 'Books by External Rating',
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly ratingChartData$: Observable<RatingChartData> =
    this.ratingChartDataSubject.asObservable();

  constructor() {
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded),
        first(),
        switchMap(() =>
          this.libraryFilterService.selectedLibrary$.pipe(
            takeUntil(this.destroy$)
          )
        ),
        catchError((error) => {
          console.error('Error processing rating stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateRatingStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: RatingStats[]): void {
    try {
      const labels = stats.map(s => s.ratingRange);
      const dataValues = stats.map(s => s.count);

      this.ratingChartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books by External Rating',
          data: dataValues,
          backgroundColor: [...CHART_COLORS],
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating chart data:', error);
    }
  }

  private calculateRatingStats(): RatingStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    return this.processRatingStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private processRatingStats(books: Book[]): RatingStats[] {
    const rangeCounts = new Map<string, { count: number, totalRating: number }>();
    RATING_RANGES.forEach(range => rangeCounts.set(range.range, {count: 0, totalRating: 0}));

    books.forEach(book => {
      const rating = this.getBookRating(book);

      if (rating === 0) {
        const noRatingData = rangeCounts.get('No Rating')!;
        noRatingData.count++;
      } else {
        for (const range of RATING_RANGES) {
          if (range.range !== 'No Rating' && rating >= range.min && rating <= range.max) {
            const rangeData = rangeCounts.get(range.range)!;
            rangeData.count++;
            rangeData.totalRating += rating;
            break;
          }
        }
      }
    });

    return RATING_RANGES.map(range => {
      const data = rangeCounts.get(range.range)!;
      return {
        ratingRange: range.range,
        count: data.count,
        averageRating: data.count > 0 ? data.totalRating / data.count : 0
      };
    }).filter(stat => stat.ratingRange !== 'No Rating');
  }

  private getBookRating(book: Book): number {
    const ratings = [];

    if (book.metadata?.goodreadsRating) ratings.push(book.metadata.goodreadsRating);
    if (book.metadata?.amazonRating) ratings.push(book.metadata.amazonRating);
    if (book.metadata?.hardcoverRating) ratings.push(book.metadata.hardcoverRating);

    if (ratings.length > 0) {
      return ratings.reduce((sum, rating) => sum + rating, 0) / ratings.length;
    }

    if (book.metadata?.rating) return book.metadata.rating;
    return 0;
  }
}
