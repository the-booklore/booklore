import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, map, switchMap, takeUntil} from 'rxjs/operators';
import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book, ReadStatus} from '../../book/model/book.model';
import {ChartConfiguration, ChartData} from 'chart.js';

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

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  hoverBorderWidth: 1,
  hoverBorderColor: '#ffffff'
} as const;

type CompletionChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class ReadingCompletionChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly completionChartType = 'bar' as const;

  public readonly completionChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: {
        stacked: true,
        ticks: {
          color: '#ffffff',
          font: {size: 10},
          maxRotation: 45,
          minRotation: 0
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        title: {
          display: true,
          text: 'Categories',
          color: '#ffffff',
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
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
          stepSize: 1,
          maxTicksLimit: 25
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.05)'
        },
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
    },
    plugins: {
      legend: {
        display: true,
        position: 'top',
        labels: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 10
          },
          padding: 15,
          boxWidth: 12
        }
      },
      tooltip: {
        backgroundColor: 'rgba(0, 0, 0, 0.9)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#ffffff',
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
      ...CHART_DEFAULTS
    }))
  });

  public readonly completionChartData$: Observable<CompletionChartData> =
    this.completionChartDataSubject.asObservable();

  private lastCalculatedStats: CompletionStats[] = [];

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
        ...CHART_DEFAULTS
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
