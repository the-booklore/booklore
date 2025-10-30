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
  '#DC2626', // Red (rating 1)
  '#EA580C', // Red-orange (rating 2)
  '#F59E0B', // Orange (rating 3)
  '#EAB308', // Yellow-orange (rating 4)
  '#FACC15', // Yellow (rating 5)
  '#BEF264', // Yellow-green (rating 6)
  '#65A30D', // Green (rating 7)
  '#16A34A', // Green (rating 8)
  '#059669', // Teal-green (rating 9)
  '#2563EB'  // Blue (rating 10)
] as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: '#ffffff'
} as const;

const RATING_RANGES = [
  {range: '1', min: 1.0, max: 1.0},
  {range: '2', min: 2.0, max: 2.0},
  {range: '3', min: 3.0, max: 3.0},
  {range: '4', min: 4.0, max: 4.0},
  {range: '5', min: 5.0, max: 5.0},
  {range: '6', min: 6.0, max: 6.0},
  {range: '7', min: 7.0, max: 7.0},
  {range: '8', min: 8.0, max: 8.0},
  {range: '9', min: 9.0, max: 9.0},
  {range: '10', min: 10.0, max: 10.0},
  {range: 'No Rating', min: 0, max: 0}
] as const;

type RatingChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class PersonalRatingChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly personalRatingChartType = 'bar' as const;

  public readonly personalRatingChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {display: false},
      tooltip: {
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#666666',
        borderWidth: 1,
        callbacks: {
          title: (context) => `Personal Rating Range: ${context[0].label}`,
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
          text: 'Personal Rating Range',
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
          }
        }
      }
    }
  };

  private readonly personalRatingChartDataSubject = new BehaviorSubject<RatingChartData>({
    labels: [],
    datasets: [{
      label: 'Books by Personal Rating',
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly personalRatingChartData$: Observable<RatingChartData> =
    this.personalRatingChartDataSubject.asObservable();

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
          console.error('Error processing personal rating stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculatePersonalRatingStats();
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
      const colors = stats.map((_, index) => CHART_COLORS[index % CHART_COLORS.length]);

      this.personalRatingChartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books by Personal Rating',
          data: dataValues,
          backgroundColor: colors,
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating personal rating chart data:', error);
    }
  }

  private calculatePersonalRatingStats(): RatingStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    return this.processPersonalRatingStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private processPersonalRatingStats(books: Book[]): RatingStats[] {
    const rangeCounts = new Map<string, { count: number, totalRating: number }>();
    RATING_RANGES.forEach(range => rangeCounts.set(range.range, {count: 0, totalRating: 0}));

    books.forEach(book => {
      const personalRating = book.metadata?.personalRating;

      if (!personalRating || personalRating === 0) {
        const noRatingData = rangeCounts.get('No Rating')!;
        noRatingData.count++;
      } else {
        for (const range of RATING_RANGES) {
          if (range.range !== 'No Rating' && personalRating >= range.min && personalRating <= range.max) {
            const rangeData = rangeCounts.get(range.range)!;
            rangeData.count++;
            rangeData.totalRating += personalRating;
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
}
