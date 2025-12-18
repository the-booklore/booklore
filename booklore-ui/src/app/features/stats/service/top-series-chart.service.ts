import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

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
    modeBorderColor: mode === 'dark' ? '#ffffff' : '#000000',
    modeGridX: mode === 'dark' ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)',
    modeGridY: mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)',
  };
}

interface SeriesStats {
  seriesName: string;
  bookCount: number;
}

const CHART_COLORS = [
  '#4e79a7', '#f28e2c', '#e15759', '#76b7b2',
  '#59a14f', '#edc949', '#af7aa1', '#ff9da7',
  '#9c755f', '#bab0ab', '#5778a4', '#e69138',
  '#d62728', '#6aa7b8', '#54a24b', '#fdd247',
  '#b07aa1', '#ff9d9a', '#9e6762', '#c9b2d6'
] as const;

const CHART_DEFAULTS = () => ({
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: themeTokens().modeColor,
});

type SeriesChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class TopSeriesChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();
  private themeObserver: MutationObserver | null = null;

  public readonly seriesChartType = 'bar' as const;

  public readonly seriesChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: 'y',
    scales: {
      x: {
        beginAtZero: true,
        ticks: {
          color: themeTokens().modeColor,
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          },
          precision: 0
        },
        grid: {
          color: themeTokens().modeGridX
        },
        title: {
          display: true,
          text: 'Number of Books',
          color: themeTokens().modeColor,
          font: {
            family: "'Inter', sans-serif",
            size: 12
          }
        }
      },
      y: {
        ticks: {
          color: themeTokens().modeColor,
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
          maxTicksLimit: 20
        },
        grid: {
          color: themeTokens().modeGridY
        }
      }
    },
    plugins: {
      legend: {
        display: false
      },
      tooltip: {
        backgroundColor: themeTokens().modeColorBG,
        titleColor: themeTokens().modeColor,
        bodyColor: themeTokens().modeColor,
        borderColor: themeTokens().modeBorderColor,
        borderWidth: 1,
        cornerRadius: 6,
        displayColors: true,
        padding: 12,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 12},
        callbacks: {
          title: (context) => {
            const dataIndex = context[0].dataIndex;
            const stats = this.getLastCalculatedStats();
            return stats[dataIndex]?.seriesName || 'Unknown Series';
          },
          label: this.formatTooltipLabel.bind(this)
        }
      },
      datalabels: {
        display: true,
        color: themeTokens().modeColor,
        font: {
          size: 10,
          family: "'Inter', sans-serif",
          weight: 'bold'
        },
        align: 'center',
        offset: 8,
        formatter: (value: number) => value.toString()
      }
    },
    interaction: {
      intersect: false,
      mode: 'point'
    }
  };

  private readonly seriesChartDataSubject = new BehaviorSubject<SeriesChartData>({
    labels: [],
    datasets: []
  });

  public readonly seriesChartData$: Observable<SeriesChartData> = this.seriesChartDataSubject.asObservable();

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
          console.error('Error processing top series stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateTopSeriesStats();
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
    const options = this.seriesChartOptions;
    
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

    const currentData = this.seriesChartDataSubject.getValue();
    if (currentData.datasets && currentData.datasets.length > 0) {
      const updatedDatasets = currentData.datasets.map(dataset => ({
        ...dataset,
        borderColor: tokens.modeColor,
        hoverBorderColor: tokens.modeColor
      }));

      this.seriesChartDataSubject.next({
        ...currentData,
        datasets: updatedDatasets
      });
    }
  }

  private updateChartData(stats: SeriesStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      const labels = stats.map(s => this.truncateTitle(s.seriesName, 30));
      const dataValues = stats.map(s => s.bookCount);
      const colors = this.getColorsForData(stats.length);

      this.seriesChartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books',
          data: dataValues,
          backgroundColor: colors,
          borderColor: colors,
          ...CHART_DEFAULTS()
        }]
      });
    } catch (error) {
      console.error('Error updating series chart data:', error);
    }
  }

  private calculateTopSeriesStats(): SeriesStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, String(selectedLibraryId));
    return this.processTopSeriesStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private processTopSeriesStats(books: Book[]): SeriesStats[] {
    if (books.length === 0) {
      return [];
    }

    const seriesMap = this.buildSeriesMap(books);
    return this.convertMapToStats(seriesMap);
  }

  private buildSeriesMap(books: Book[]): Map<string, number> {
    const seriesMap = new Map<string, number>();

    for (const book of books) {
      const seriesName = book.metadata?.seriesName;
      if (seriesName && seriesName.trim()) {
        const normalizedName = seriesName.trim();
        seriesMap.set(normalizedName, (seriesMap.get(normalizedName) || 0) + 1);
      }
    }

    return seriesMap;
  }

  private convertMapToStats(seriesMap: Map<string, number>): SeriesStats[] {
    return Array.from(seriesMap.entries())
      .map(([seriesName, bookCount]) => ({
        seriesName,
        bookCount
      }))
      .sort((a, b) => b.bookCount - a.bookCount)
      .slice(0, 20);
  }

  private getColorsForData(dataLength: number): string[] {
    const colors = [...CHART_COLORS];
    while (colors.length < dataLength) {
      colors.push(...CHART_COLORS);
    }
    return colors.slice(0, dataLength);
  }

  private formatTooltipLabel(context: any): string {
    const dataIndex = context.dataIndex;
    const stats = this.getLastCalculatedStats();

    if (!stats || dataIndex >= stats.length) {
      return `${context.parsed.x} books`;
    }

    const series = stats[dataIndex];
    const bookCount = series.bookCount;
    const bookText = bookCount === 1 ? 'book' : 'books';

    return `${bookCount} ${bookText}`;
  }

  private lastCalculatedStats: SeriesStats[] = [];

  private getLastCalculatedStats(): SeriesStats[] {
    return this.lastCalculatedStats;
  }

  private truncateTitle(title: string, maxLength: number): string {
    return title.length > maxLength ? title.substring(0, maxLength) + '...' : title;
  }
}
