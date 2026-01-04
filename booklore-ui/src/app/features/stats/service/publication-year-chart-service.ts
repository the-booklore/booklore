import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData, ChartType, TooltipItem} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';
import {BookState} from '../../book/model/state/book-state.model';

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
    modeColorBG: mode === 'dark' ? 'rgba(0, 0, 0, 0.9)' : 'rgba(255, 255, 255, 0.9)',
    modeGridX: mode === 'dark' ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)',
    modeGridY: mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)',
  };
}

interface PublicationYearStats {
  year: string;
  count: number;
  decade: string;
}

const CHART_COLORS = {
  primary: '#4ECDC4',
  primaryBackground: 'rgba(78, 205, 196, 0.1)',
  border: themeTokens().modeColor
} as const;

const CHART_DEFAULTS = () => ({
  borderColor: CHART_COLORS.primary,
  backgroundColor: CHART_COLORS.primaryBackground,
  borderWidth: 2,
  pointBackgroundColor: CHART_COLORS.primary,
  pointBorderColor: themeTokens().modeColor,
  pointBorderWidth: 2,
  pointRadius: 4,
  pointHoverRadius: 6,
  fill: true,
  tension: 0.4
});

type YearChartData = ChartData<'line', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class PublicationYearChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();
  private themeObserver: MutationObserver | null = null;

  public readonly yearChartType = 'line' as const;

  public readonly yearChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {display: false},
      tooltip: {
        enabled: true,
        backgroundColor: themeTokens().modeColorBG,
        titleColor: themeTokens().modeColor,
        bodyColor: themeTokens().modeColor,
        borderColor: themeTokens().modeColor,
        borderWidth: 1,
        cornerRadius: 6,
        displayColors: true,
        padding: 12,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 13},
        position: 'nearest',
        callbacks: {
          title: (context) => `Year ${context[0].label}`,
          label: this.formatTooltipLabel.bind(this)
        }
      },
      datalabels: {
        display: true,
        color: themeTokens().modeColor,
        font: {
          size: 10,
          weight: 'bold'
        },
        align: 'top',
        offset: 8,
        formatter: (value: number) => value.toString()
      }
    },
    interaction: {
      intersect: false,
      mode: 'point'
    },
    scales: {
      x: {
        beginAtZero: true,
        ticks: {
          color: themeTokens().modeColor,
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          },
          maxRotation: 45,
          callback: function (value, index, values) {
            // Show every 5th year to avoid crowding
            return index % 5 === 0 ? this.getLabelForValue(value as number) : '';
          }
        },
        grid: {
          color: themeTokens().modeGridX
        },
        title: {
          display: true,
          text: 'Publication Year',
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
            size: 11.5
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

  private readonly yearChartDataSubject = new BehaviorSubject<YearChartData>({
    labels: [],
    datasets: [{
      label: 'Books Published',
      data: [],
      ...CHART_DEFAULTS()
    }]
  });

  public readonly yearChartData$: Observable<YearChartData> =
    this.yearChartDataSubject.asObservable();

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
          console.error('Error processing publication year stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculatePublicationYearStats();
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
    const options = this.yearChartOptions;
    
    if (options) {
      if (options.plugins) {
        if (options.plugins.tooltip) {
          options.plugins.tooltip.backgroundColor = tokens.modeColorBG;
          options.plugins.tooltip.titleColor = tokens.modeColor;
          options.plugins.tooltip.bodyColor = tokens.modeColor;
          options.plugins.tooltip.borderColor = tokens.modeColor;
        }
        
        const plugins = options.plugins as any;
        if (plugins.datalabels) {
          plugins.datalabels.color = tokens.modeColor;
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

    const currentData = this.yearChartDataSubject.getValue();
    if (currentData.datasets && currentData.datasets.length > 0) {
      const updatedDatasets = currentData.datasets.map(dataset => ({
        ...dataset,
        pointBorderColor: tokens.modeColor
      }));

      this.yearChartDataSubject.next({
        ...currentData,
        datasets: updatedDatasets
      });
    }
  }

  private updateChartData(stats: PublicationYearStats[]): void {
    try {
      const labels = stats.map(s => s.year);
      const dataValues = stats.map(s => s.count);

      this.yearChartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books Published',
          data: dataValues,
          ...CHART_DEFAULTS()
        }]
      });
    } catch (error) {
      console.error('Error updating chart data:', error);
    }
  }

  private calculatePublicationYearStats(): PublicationYearStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    return this.processPublicationYearStats(filteredBooks);
  }

  public updateFromStats(stats: PublicationYearStats[]): void {
    this.updateChartData(stats);
  }

  private isValidBookState(state: unknown): state is BookState {
    return (
      typeof state === 'object' &&
      state !== null &&
      'loaded' in state &&
      typeof (state as {loaded: boolean}).loaded === 'boolean' &&
      'books' in state &&
      Array.isArray((state as {books: unknown}).books) &&
      (state as {books: Book[]}).books.length > 0
    );
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private processPublicationYearStats(books: Book[]): PublicationYearStats[] {
    const yearMap = new Map<string, number>();

    books.forEach(book => {
      if (book.metadata?.publishedDate) {
        const year = this.extractYear(book.metadata.publishedDate);
        if (year && year >= 1800 && year <= new Date().getFullYear()) {
          const yearStr = year.toString();
          yearMap.set(yearStr, (yearMap.get(yearStr) || 0) + 1);
        }
      }
    });

    // Only return years that have books (no 0 entries)
    return Array.from(yearMap.entries())
      .filter(([year, count]) => count > 0)
      .map(([year, count]) => ({
        year,
        count,
        decade: `${Math.floor(parseInt(year) / 10) * 10}s`
      }))
      .sort((a, b) => parseInt(a.year) - parseInt(b.year));
  }

  private extractYear(dateString: string): number | null {
    const yearMatch = dateString.match(/(\d{4})/);
    return yearMatch ? parseInt(yearMatch[1]) : null;
  }

  private formatTooltipLabel(context: TooltipItem<any>): string {
    const value = context.parsed.y;
    return `${value} book${value === 1 ? '' : 's'} published`;
  }
}
