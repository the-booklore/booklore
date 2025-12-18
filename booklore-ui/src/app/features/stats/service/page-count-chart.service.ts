import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData, ChartType} from 'chart.js';

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
    modeColorBG: mode === 'dark' ? 'rgba(0, 0, 0, 0.8)' : 'rgba(255, 255, 255, 0.8)',
    modeBorderColor: mode === 'dark' ? '#ffffff' : '#000000',
    modeGridX: mode === 'dark' ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)',
    modeGridY: mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)',
  };
}

interface PageCountStats {
  category: string;
  count: number;
  avgPages: number;
  minPages: number;
  maxPages: number;
}

const CHART_COLORS = [
  '#81C784', '#4FC3F7', '#FFB74D', '#F06292', '#BA68C8'
] as const;

const CHART_DEFAULTS = () => ({
  borderColor: themeTokens().modeColor,
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: themeTokens().modeColor,
});

type PageCountChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class PageCountChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();
  private themeObserver: MutationObserver | null = null;

  public readonly pageCountChartType: ChartType = 'bar';

  public readonly pageCountChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
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
        padding: 12,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 12},
        callbacks: {
          title: (context) => context[0].label,
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
          },
          maxRotation: 45
        },
        grid: {
          color: themeTokens().modeGridX
        },
        title: {
          display: true,
          text: 'Page Count Category',
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

  private readonly pageCountChartDataSubject = new BehaviorSubject<PageCountChartData>({
    labels: [],
    datasets: [{
      label: 'Books by Page Count',
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS()
    }]
  });

  public readonly pageCountChartData$: Observable<PageCountChartData> =
    this.pageCountChartDataSubject.asObservable();

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
          console.error('Error processing page count stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculatePageCountStats();
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
    const options = this.pageCountChartOptions;
    
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

    const currentData = this.pageCountChartDataSubject.getValue();
    if (currentData.datasets && currentData.datasets.length > 0) {
      const updatedDatasets = currentData.datasets.map(dataset => ({
        ...dataset,
        borderColor: tokens.modeColor,
        hoverBorderColor: tokens.modeColor
      }));

      this.pageCountChartDataSubject.next({
        ...currentData,
        datasets: updatedDatasets
      });
    }
  }

  private updateChartData(stats: PageCountStats[]): void {
    try {
      const labels = stats.map(s => s.category);
      const dataValues = stats.map(s => s.count);

      this.pageCountChartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books by Page Count',
          data: dataValues,
          backgroundColor: [...CHART_COLORS],
          ...CHART_DEFAULTS()
        }]
      });
    } catch (error) {
      console.error('Error updating chart data:', error);
    }
  }

  private calculatePageCountStats(): PageCountStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    return this.processPageCountStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private processPageCountStats(books: Book[]): PageCountStats[] {
    const categories = [
      {name: 'Short (< 200)', min: 0, max: 199},
      {name: 'Medium (200-400)', min: 200, max: 400},
      {name: 'Long (401-600)', min: 401, max: 600},
      {name: 'Very Long (601-800)', min: 601, max: 800},
      {name: 'Epic (> 800)', min: 801, max: 9999}
    ];

    return categories.map(category => {
      const booksInCategory = books.filter(book => {
        const pageCount = book.metadata?.pageCount;
        return pageCount && pageCount >= category.min && pageCount <= category.max;
      });

      const pageCounts = booksInCategory
        .map(book => book.metadata?.pageCount || 0)
        .filter(count => count > 0);

      return {
        category: category.name,
        count: booksInCategory.length,
        avgPages: pageCounts.length > 0 ? Math.round(pageCounts.reduce((a, b) => a + b, 0) / pageCounts.length) : 0,
        minPages: pageCounts.length > 0 ? Math.min(...pageCounts) : 0,
        maxPages: pageCounts.length > 0 ? Math.max(...pageCounts) : 0
      };
    }).filter(stat => stat.count > 0);
  }
}
