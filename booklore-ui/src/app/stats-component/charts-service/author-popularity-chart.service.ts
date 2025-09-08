import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book, ReadStatus} from '../../book/model/book.model';
import {ChartConfiguration, ChartData, ChartType} from 'chart.js';

interface AuthorStats {
  author: string;
  bookCount: number;
  averageRating: number;
  totalPages: number;
  readStatusCounts: Record<ReadStatus, number>;
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

type AuthorChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class AuthorPopularityChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly authorChartType = 'bar' as const;

  public readonly authorChartOptions: ChartConfiguration<'bar'>['options'] = {
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
          text: 'Authors',
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
            return stats[dataIndex]?.author || 'Unknown Author';
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

  private readonly authorChartDataSubject = new BehaviorSubject<AuthorChartData>({
    labels: [],
    datasets: Object.values(ReadStatus).map(status => ({
      label: this.formatReadStatusLabel(status),
      data: [],
      backgroundColor: READ_STATUS_COLORS[status],
      ...CHART_DEFAULTS
    }))
  });

  public readonly authorChartData$: Observable<AuthorChartData> =
    this.authorChartDataSubject.asObservable();

  private lastCalculatedStats: AuthorStats[] = [];

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
          console.error('Error processing author stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateAuthorStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: AuthorStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      const topAuthors = stats
        .sort((a, b) => b.bookCount - a.bookCount)
        .slice(0, 25);

      const labels = topAuthors.map(s => {
        return s.author.length > 20
          ? s.author.substring(0, 15) + '..'
          : s.author;
      });

      const datasets = Object.values(ReadStatus).map(status => ({
        label: this.formatReadStatusLabel(status),
        data: topAuthors.map(s => s.readStatusCounts[status] || 0),
        backgroundColor: READ_STATUS_COLORS[status],
        ...CHART_DEFAULTS
      }));

      this.authorChartDataSubject.next({
        labels,
        datasets
      });
    } catch (error) {
      console.error('Error updating author chart data:', error);
    }
  }

  private calculateAuthorStats(): AuthorStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    return this.processAuthorStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private processAuthorStats(books: Book[]): AuthorStats[] {
    const authorMap = new Map<string, {
      bookCount: number;
      totalRating: number;
      ratingCount: number;
      totalPages: number;
      readStatusCounts: Record<ReadStatus, number>;
    }>();

    books.forEach(book => {
      const authors = book.metadata?.authors || ['Unknown Author'];

      authors.forEach(author => {
        if (!authorMap.has(author)) {
          authorMap.set(author, {
            bookCount: 0,
            totalRating: 0,
            ratingCount: 0,
            totalPages: 0,
            readStatusCounts: Object.values(ReadStatus).reduce((acc, status) => {
              acc[status] = 0;
              return acc;
            }, {} as Record<ReadStatus, number>)
          });
        }

        const stats = authorMap.get(author)!;
        stats.bookCount++;

        if (book.metadata?.pageCount) {
          stats.totalPages += book.metadata.pageCount;
        }

        const rawStatus = book.readStatus;
        const readStatus: ReadStatus = Object.values(ReadStatus).includes(rawStatus as ReadStatus)
          ? (rawStatus as ReadStatus)
          : ReadStatus.UNSET;
        stats.readStatusCounts[readStatus]++;

        const ratings = [];
        if (book.metadata?.goodreadsRating) ratings.push(book.metadata.goodreadsRating);
        if (book.metadata?.amazonRating) ratings.push(book.metadata.amazonRating);
        if (book.metadata?.hardcoverRating) ratings.push(book.metadata.hardcoverRating);
        if (book.metadata?.personalRating) ratings.push(book.metadata.personalRating);

        if (ratings.length > 0) {
          const avgRating = ratings.reduce((sum, rating) => sum + rating, 0) / ratings.length;
          stats.totalRating += avgRating;
          stats.ratingCount++;
        }
      });
    });

    return Array.from(authorMap.entries()).map(([author, stats]) => ({
      author,
      bookCount: stats.bookCount,
      averageRating: stats.ratingCount > 0 ? stats.totalRating / stats.ratingCount : 0,
      totalPages: stats.totalPages,
      readStatusCounts: stats.readStatusCounts
    })).filter(stat => stat.bookCount > 0);
  }

  private formatTooltipLabel(context: any): string {
    const dataIndex = context.dataIndex;
    const stats = this.getLastCalculatedStats();

    if (!stats || dataIndex >= stats.length) {
      return `${context.parsed.y} books`;
    }

    const author = stats[dataIndex];
    const value = context.parsed.y;
    const datasetLabel = context.dataset.label;

    if (context.dataset.label === 'Read' && author.averageRating > 0) {
      return `${datasetLabel}: ${value} | Avg Rating: ${author.averageRating.toFixed(1)}`;
    } else {
      return `${datasetLabel}: ${value}`;
    }
  }

  private formatReadStatusLabel(status: ReadStatus): string {
    return status.split('_').map(word =>
      word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    ).join(' ');
  }

  private getLastCalculatedStats(): AuthorStats[] {
    return this.lastCalculatedStats;
  }
}
