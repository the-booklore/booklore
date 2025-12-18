import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, combineLatest, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError} from 'rxjs/operators';
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
    modeGrid: mode === 'dark' ? 'rgba(255, 255, 255, 0.2)' : 'rgba(0, 0, 0, 0.2)',
  };
}

interface SeriesStats {
  category: string;
  count: number;
  percentage: number;
  description: string;
}

const CHART_COLORS = [
  '#ff6b6b', '#4ecdc4', '#45b7d1', '#96ceb4', '#ffeaa7',
  '#dda0dd', '#98d8c8', '#ff7675', '#74b9ff', '#fdcb6e'
] as const;

const CHART_DEFAULTS = {
  borderColor: themeTokens().modeColor,
  borderWidth: 2,
  hoverBorderWidth: 3,
  hoverBorderColor: themeTokens().modeColor
} as const;

type SeriesChartData = ChartData<'polarArea', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class SeriesStandaloneChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();
  private themeObserver: MutationObserver | null = null;

  public readonly seriesChartType = 'polarArea' as const;

  public readonly seriesChartOptions: ChartConfiguration<'polarArea'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      r: {
        beginAtZero: true,
        ticks: {
          color: themeTokens().modeColor,
          font: {size: 10},
          stepSize: 1
        },
        grid: {
          color: themeTokens().modeGrid
        },
        angleLines: {
          color: themeTokens().modeGrid
        },
        pointLabels: {
          color: themeTokens().modeColor,
          font: {size: 11}
        }
      }
    },
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          color: themeTokens().modeColor,
          font: {size: 11},
          padding: 12,
          usePointStyle: true,
          generateLabels: this.generateLegendLabels.bind(this)
        }
      },
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
          title: (context) => context[0]?.label || '',
          label: this.formatTooltipLabel
        }
      }
    },
    interaction: {
      intersect: false,
      mode: 'point'
    }
  };

  private readonly seriesChartDataSubject = new BehaviorSubject<SeriesChartData>({
    labels: [],
    datasets: [{
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly seriesChartData$: Observable<SeriesChartData> = this.seriesChartDataSubject.asObservable();

  constructor() {
    this.initThemeObserver();
    this.initializeChartDataSubscription();
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
        if (options.plugins.legend?.labels) {
          options.plugins.legend.labels.color = tokens.modeColor;
        }
        if (options.plugins.tooltip) {
          options.plugins.tooltip.backgroundColor = tokens.modeColorBG;
          options.plugins.tooltip.titleColor = tokens.modeColor;
          options.plugins.tooltip.bodyColor = tokens.modeColor;
          options.plugins.tooltip.borderColor = tokens.modeColor;
        }
      }

      if (options.scales && options.scales['r']) {
        const rScale = options.scales['r'];
        if (rScale.ticks) {
          rScale.ticks.color = tokens.modeColor;
        }
        if (rScale.grid) {
          rScale.grid.color = tokens.modeGrid;
        }
        if (rScale.angleLines) {
          rScale.angleLines.color = tokens.modeGrid;
        }
        if (rScale.pointLabels) {
          rScale.pointLabels.color = tokens.modeColor;
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

  private initializeChartDataSubscription(): void {
    this.getSeriesStats()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error processing series stats:', error);
          return [];
        })
      )
      .subscribe((stats) => this.updateChartData(stats));
  }

  private updateChartData(stats: SeriesStats[]): void {
    try {
      const labels = stats.map(s => s.category);
      const dataValues = stats.map(s => s.count);
      const colors = this.getColorsForData(stats.length);

      this.seriesChartDataSubject.next({
        labels,
        datasets: [{
          data: dataValues,
          backgroundColor: colors,
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating series chart data:', error);
    }
  }

  private getColorsForData(dataLength: number): string[] {
    const colors = [...CHART_COLORS];
    while (colors.length < dataLength) {
      colors.push(...CHART_COLORS);
    }
    return colors.slice(0, dataLength);
  }

  public getSeriesStats(): Observable<SeriesStats[]> {
    return combineLatest([
      this.bookService.bookState$,
      this.libraryFilterService.selectedLibrary$
    ]).pipe(
      map(([state, selectedLibraryId]) => {
        if (!this.isValidBookState(state)) {
          return [];
        }

        const filteredBooks = this.filterBooksByLibrary(state.books!, String(selectedLibraryId));
        return this.processSeriesStats(filteredBooks);
      }),
      catchError((error) => {
        console.error('Error getting series stats:', error);
        return [];
      })
    );
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private processSeriesStats(books: Book[]): SeriesStats[] {
    if (books.length === 0) {
      return [];
    }

    const stats = this.categorizeBooks(books);
    return this.convertToStats(stats, books.length);
  }

  private categorizeBooks(books: Book[]): Map<string, { count: number; description: string }> {
    const categories = new Map<string, { count: number; description: string }>();

    // Initialize categories
    categories.set('Standalone Books', { count: 0, description: 'Books not part of any series' });
    categories.set('Series Books', { count: 0, description: 'Books that are part of a series' });
    categories.set('Complete Series', { count: 0, description: 'Books in complete series' });
    categories.set('Incomplete Series', { count: 0, description: 'Books in incomplete series' });
    categories.set('Unknown Series Status', { count: 0, description: 'Books with unclear series information' });

    // Group books by series
    const seriesMap = new Map<string, Book[]>();
    const standaloneBooks: Book[] = [];

    for (const book of books) {
      const seriesName = book.metadata?.seriesName;
      if (seriesName && seriesName.trim()) {
        if (!seriesMap.has(seriesName)) {
          seriesMap.set(seriesName, []);
        }
        seriesMap.get(seriesName)!.push(book);
      } else {
        standaloneBooks.push(book);
      }
    }

    // Count standalone books
    categories.get('Standalone Books')!.count = standaloneBooks.length;

    // Process series books
    let seriesBooks = 0;
    let completeSeriesBooks = 0;
    let incompleteSeriesBooks = 0;
    let unknownSeriesBooks = 0;

    for (const [seriesName, seriesBookList] of seriesMap) {
      seriesBooks += seriesBookList.length;

      // Check if series is complete
      const seriesTotal = seriesBookList[0]?.metadata?.seriesTotal;
      const hasSeriesNumbers = seriesBookList.every(book => book.metadata?.seriesNumber);

      if (seriesTotal && hasSeriesNumbers) {
        const uniqueNumbers = new Set(seriesBookList.map(book => book.metadata?.seriesNumber));
        if (uniqueNumbers.size === seriesTotal) {
          completeSeriesBooks += seriesBookList.length;
        } else {
          incompleteSeriesBooks += seriesBookList.length;
        }
      } else {
        unknownSeriesBooks += seriesBookList.length;
      }
    }

    categories.get('Series Books')!.count = seriesBooks;
    categories.get('Complete Series')!.count = completeSeriesBooks;
    categories.get('Incomplete Series')!.count = incompleteSeriesBooks;
    categories.get('Unknown Series Status')!.count = unknownSeriesBooks;

    return categories;
  }

  private convertToStats(categoriesMap: Map<string, { count: number; description: string }>, totalBooks: number): SeriesStats[] {
    return Array.from(categoriesMap.entries())
      .filter(([_, data]) => data.count > 0) // Only include categories with books
      .map(([category, data]) => ({
        category,
        count: data.count,
        percentage: Number(((data.count / totalBooks) * 100).toFixed(1)),
        description: data.description
      }))
      .sort((a, b) => b.count - a.count);
  }

  private generateLegendLabels(chart: any) {
    const data = chart.data;
    if (!data.labels?.length || !data.datasets?.[0]?.data?.length) {
      return [];
    }

    const dataset = data.datasets[0];
    const dataValues = dataset.data as number[];
    const tokens = themeTokens();

    return data.labels.map((label: string, index: number) => {
      const isVisible = typeof chart.getDataVisibility === 'function'
        ? chart.getDataVisibility(index)
        : !((chart.getDatasetMeta && chart.getDatasetMeta(0)?.data?.[index]?.hidden) || false);

      return {
        text: `${label} (${dataValues[index]})`,
        fillStyle: (dataset.backgroundColor as string[])[index],
        strokeStyle: tokens.modeColor,
        lineWidth: 1,
        hidden: !isVisible,
        index,
        fontColor: tokens.modeColor
      };
    });
  }

  private formatTooltipLabel(context: any): string {
    const dataIndex = context.dataIndex;
    const dataset = context.dataset;
    const value = dataset.data[dataIndex] as number;
    const label = context.chart.data.labels?.[dataIndex] || 'Unknown';
    const total = (dataset.data as number[]).reduce((a: number, b: number) => a + b, 0);
    const percentage = ((value / total) * 100).toFixed(1);
    return `${label}: ${value} books (${percentage}%)`;
  }
}

