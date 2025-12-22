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

const CHART_DEFAULTS = () => ({
  borderColor: themeTokens().modeColor,
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: themeTokens().modeColor,
});

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
  private themeObserver: MutationObserver | null = null;

  public readonly personalRatingChartType = 'bar' as const;

  public readonly personalRatingChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {display: false},
      tooltip: {
        backgroundColor: themeTokens().modeColorBG,
        titleColor: themeTokens().modeColor,
        bodyColor: themeTokens().modeColor,
        borderColor: themeTokens().modeBorderColor,
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
          text: 'Personal Rating Range',
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
      ...CHART_DEFAULTS()
    }]
  });

  public readonly personalRatingChartData$: Observable<RatingChartData> =
    this.personalRatingChartDataSubject.asObservable();

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
    const options = this.personalRatingChartOptions;
    
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

    const currentData = this.personalRatingChartDataSubject.getValue();
    if (currentData.datasets && currentData.datasets.length > 0) {
      const updatedDatasets = currentData.datasets.map(dataset => ({
        ...dataset,
        borderColor: tokens.modeColor,
        hoverBorderColor: tokens.modeColor
      }));

      this.personalRatingChartDataSubject.next({
        ...currentData,
        datasets: updatedDatasets
      });
    }
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
          ...CHART_DEFAULTS()
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
      const personalRating = book.personalRating;

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
