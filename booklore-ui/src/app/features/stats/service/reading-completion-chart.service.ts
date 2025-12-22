import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, map, switchMap, takeUntil} from 'rxjs/operators';
import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book, ReadStatus} from '../../book/model/book.model';
import {ChartConfiguration, ChartData} from 'chart.js';

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

interface CompletionStats {
  category: string;
  readStatusCounts: Record<ReadStatus, number>;
  total: number;
}

const READ_STATUS_COLORS: Record<ReadStatus, string> = {
  [ReadStatus.READ]: '#2ecc71',
  [ReadStatus.READING]: '#f39c12',
  [ReadStatus.RE_READING]: '#9b59b6',
  [ReadStatus.PARTIALLY_READ]: '#e67e22',
  [ReadStatus.PAUSED]: '#34495e',
  [ReadStatus.UNREAD]: '#4169e1',
  [ReadStatus.WONT_READ]: '#95a5a6',
  [ReadStatus.ABANDONED]: '#e74c3c',
  [ReadStatus.UNSET]: '#3498db'
};

const CHART_DEFAULTS = () => ({
  borderColor: themeTokens().modeColor,
  hoverBorderWidth: 1,
  hoverBorderColor: themeTokens().modeColor,
});

type CompletionChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class ReadingCompletionChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();
  private themeObserver: MutationObserver | null = null;

  public readonly completionChartType = 'bar' as const;

  public readonly completionChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: {
        stacked: true,
        ticks: {
          color: themeTokens().modeColor,
          font: {size: 10},
          maxRotation: 45,
          minRotation: 0
        },
        grid: {
          color: themeTokens().modeGridX
        },
        title: {
          display: true,
          text: 'Categories',
          color: themeTokens().modeColor,
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
        }
      },
      y: {
        stacked: true,
        beginAtZero: true,
        ticks: {
          color: themeTokens().modeColor,
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
          stepSize: 1,
          maxTicksLimit: 25
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
    },
    plugins: {
      legend: {
        display: true,
        position: 'top',
        labels: {
          color: themeTokens().modeColor,
          font: {
            family: "'Inter', sans-serif",
            size: 10
          },
          padding: 15,
          boxWidth: 12
        }
      },
      tooltip: {
        backgroundColor: themeTokens().modeColorBG,
        titleColor: themeTokens().modeColor,
        bodyColor: themeTokens().modeColor,
        borderColor: themeTokens().modeColor,
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
            return stats[dataIndex]?.category || 'Unknown Category';
          },
          label: this.formatTooltipLabel.bind(this)
        }
      }
    },
    interaction: {
      intersect: false,
      mode: 'index'
    }
  };

  private readonly completionChartDataSubject = new BehaviorSubject<CompletionChartData>({
    labels: [],
    datasets: Object.values(ReadStatus).map(status => ({
      label: this.formatReadStatusLabel(status),
      data: [],
      backgroundColor: READ_STATUS_COLORS[status],
      ...CHART_DEFAULTS()
    }))
  });

  public readonly completionChartData$: Observable<CompletionChartData> =
    this.completionChartDataSubject.asObservable();

  private lastCalculatedStats: CompletionStats[] = [];

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
          console.error('Error processing completion stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateCompletionStats();
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
    const options = this.completionChartOptions;
    if (options) {
      if (options.plugins) {
        if (options.plugins.tooltip) {
          options.plugins.tooltip.backgroundColor = tokens.modeColorBG;
          options.plugins.tooltip.titleColor = tokens.modeColor;
          options.plugins.tooltip.bodyColor = tokens.modeColor;
          options.plugins.tooltip.borderColor = tokens.modeColor;
        }
        if (options.plugins.legend?.labels) {
          options.plugins.legend.labels.color = tokens.modeColor;
        }
      }
      if (options.scales) {
        if (options.scales['x']) {
          if (options.scales['x'].ticks) options.scales['x'].ticks.color = tokens.modeColor;
          if (options.scales['x'].grid) options.scales['x'].grid.color = tokens.modeGridX;
          if (options.scales['x'].title) options.scales['x'].title.color = tokens.modeColor;
        }
        if (options.scales['y']) {
          if (options.scales['y'].ticks) options.scales['y'].ticks.color = tokens.modeColor;
          if (options.scales['y'].grid) options.scales['y'].grid.color = tokens.modeGridY;
          if (options.scales['y'].title) options.scales['y'].title.color = tokens.modeColor;
        }
      }
    }
    const currentData = this.completionChartDataSubject.getValue();
    if (currentData.datasets && currentData.datasets.length > 0) {
      const updatedDatasets = currentData.datasets.map(dataset => ({
        ...dataset,
        borderColor: tokens.modeColor,
        hoverBorderColor: tokens.modeColor
      }));

      this.completionChartDataSubject.next({
        ...currentData,
        datasets: updatedDatasets
      });
    }
  }

  private updateChartData(stats: CompletionStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      const topCategories = stats
        .sort((a, b) => b.total - a.total)
        .slice(0, 25);

      const labels = topCategories.map(s => {
        return s.category.length > 20
          ? s.category.substring(0, 15) + '..'
          : s.category;
      });

      const datasets = Object.values(ReadStatus).map(status => ({
        label: this.formatReadStatusLabel(status),
        data: topCategories.map(s => s.readStatusCounts[status] || 0),
        backgroundColor: READ_STATUS_COLORS[status],
        ...CHART_DEFAULTS()
      }));

      this.completionChartDataSubject.next({
        labels,
        datasets
      });
    } catch (error) {
      console.error('Error updating completion chart data:', error);
    }
  }

  private calculateCompletionStats(): CompletionStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    return this.processCompletionStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private processCompletionStats(books: Book[]): CompletionStats[] {
    const categoryMap = new Map<string, {
      readStatusCounts: Record<ReadStatus, number>;
    }>();

    books.forEach(book => {
      const categories = book.metadata?.categories || ['Uncategorized'];

      categories.forEach(category => {
        if (!categoryMap.has(category)) {
          categoryMap.set(category, {
            readStatusCounts: Object.values(ReadStatus).reduce((acc, status) => {
              acc[status] = 0;
              return acc;
            }, {} as Record<ReadStatus, number>)
          });
        }

        const stats = categoryMap.get(category)!;
        const rawStatus = book.readStatus;
        const readStatus: ReadStatus = Object.values(ReadStatus).includes(rawStatus as ReadStatus)
          ? (rawStatus as ReadStatus)
          : ReadStatus.UNSET;
        stats.readStatusCounts[readStatus]++;
      });
    });

    return Array.from(categoryMap.entries()).map(([category, stats]) => {
      const total = Object.values(stats.readStatusCounts).reduce((sum, count) => sum + count, 0);
      return {
        category,
        readStatusCounts: stats.readStatusCounts,
        total
      };
    }).filter(stat => stat.total > 0);
  }

  private formatReadStatusLabel(status: ReadStatus): string {
    return status.split('_').map(word =>
      word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    ).join(' ');
  }

  private formatTooltipLabel(context: any): string {
    const dataIndex = context.dataIndex;
    const stats = this.getLastCalculatedStats();

    if (!stats || dataIndex >= stats.length) {
      return `${context.parsed.y} books`;
    }

    const category = stats[dataIndex];
    const value = context.parsed.y;
    const datasetLabel = context.dataset.label;

    return `${datasetLabel}: ${value}`;
  }

  private getLastCalculatedStats(): CompletionStats[] {
    return this.lastCalculatedStats;
  }
}
