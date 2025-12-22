import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';
import {ChartConfiguration, ChartData, ChartType} from 'chart.js';

function hasClass(cls: string): boolean {
  return document.documentElement.classList.contains(cls);
}

type ThemeMode = 'dark' | 'light';

function themeMode(): ThemeMode {
  return hasClass('p-dark') ? 'dark' : 'light';
}

function themeTokens() {
  const mode = themeMode();
  return {
    mode,
    modeColor: mode === 'dark' ? '#ffffff' : '#000000',
    modeColorBG: mode === 'dark' ? 'rgba(0, 0, 0, 0.8)' : 'rgba(255, 255, 255, 0.8)',
    modeBorderColor: mode === 'dark' ? '#666666' : '#444444',
    modeGridX: mode === 'dark' ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)',
    modeGridY: mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)',
  };
}

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

const CHART_DEFAULTS = () => ({
  borderColor: themeTokens().modeColor,
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: themeTokens().modeColor,
});

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
  private themeObserver: MutationObserver | null = null;

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
        backgroundColor: themeTokens().modeColorBG,
        titleColor: themeTokens().modeColor,
        bodyColor: themeTokens().modeColor,
        borderColor: themeTokens().modeBorderColor,
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
          color: themeTokens().modeColor,
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {
          color: themeTokens().modeGridX
        },
        title: {
          display: true,
          text: 'External Rating Range',
          color: themeTokens().modeColor,
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          }
        }
      },
      y: {
        beginAtZero: true,
        ticks: {
          color: themeTokens().modeColor,
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
          stepSize: 1
        },
        grid: {
          color: themeTokens().modeGridY
        },
        title: {
          display: true,
          text: 'Number of Books',
          color: themeTokens().modeColor,
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
      ...CHART_DEFAULTS()
    }]
  });

  public readonly ratingChartData$: Observable<RatingChartData> =
    this.ratingChartDataSubject.asObservable();

  constructor() {
  	this.initThemeObserver();
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
    if (this.themeObserver) {
      this.themeObserver.disconnect();
    }
  }

  private initThemeObserver(): void {
    this.themeObserver = new MutationObserver((mutations) => {
      let shouldUpdate = false;
      for (const mutation of mutations) {
        if (mutation.type === 'attributes' && mutation.attributeName === 'class') {
          shouldUpdate = true;
          break;
        }
      }
      if (shouldUpdate) {
        this.updateChartTheme();
      }
    });

    this.themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class']
    });
  }

  private updateChartTheme(): void {
    const tokens = themeTokens();
    const options = this.ratingChartOptions;
    
    if (options) {
      if (options.plugins) {
        if (options.plugins.tooltip) {
          options.plugins.tooltip.backgroundColor = tokens.modeColorBG;
          options.plugins.tooltip.titleColor = tokens.modeColor;
          options.plugins.tooltip.bodyColor = tokens.modeColor;
          options.plugins.tooltip.borderColor = tokens.modeBorderColor;
        }
      }

      if (options.scales) {
        const xScale = options.scales['x'] as any;
        if (xScale) {
          if (xScale.ticks) xScale.ticks.color = tokens.modeColor;
          if (xScale.grid) xScale.grid.color = tokens.modeGridX;
          if (xScale.title) xScale.title.color = tokens.modeColor;
        }

        const yScale = options.scales['y'] as any;
        if (yScale) {
          if (yScale.ticks) yScale.ticks.color = tokens.modeColor;
          if (yScale.grid) yScale.grid.color = tokens.modeGridY;
          if (yScale.title) yScale.title.color = tokens.modeColor;
        }
      }
    }

    const currentData = this.ratingChartDataSubject.getValue();
    if (currentData.datasets && currentData.datasets.length > 0) {
      const updatedDatasets = currentData.datasets.map(dataset => ({
        ...dataset,
        borderColor: tokens.modeColor,
        hoverBorderColor: tokens.modeColor
      }));

      this.ratingChartDataSubject.next({
        ...currentData,
        datasets: updatedDatasets
      });
    }
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
          ...CHART_DEFAULTS()
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
