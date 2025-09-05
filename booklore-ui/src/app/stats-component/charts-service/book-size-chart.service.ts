import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

interface BookSizeStats {
  title: string;
  sizeMB: number;
  bookType: string;
  pageCount?: number;
}

const BOOK_TYPE_COLORS = {
  'PDF': '#e74c3c',
  'EPUB': '#3498db',
  'CBZ': '#27a153',
  'CBX': '#d4b50f',
  'CBR': '#e67e22',
  'CB7': '#9b59b6'
} as const;

const CHART_DEFAULTS = {
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: '#ffffff'
} as const;

type BookSizeChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class BookSizeChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly bookSizeChartType = 'bar' as const;

  public readonly bookSizeChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: 'y',
    scales: {
      x: {
        beginAtZero: true,
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          },
          callback: function (value) {
            return value + ' MB';
          }
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        title: {
          display: true,
          text: 'File Size (MB)',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 12
          }
        }
      },
      y: {
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          },
          maxTicksLimit: 25
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.05)'
        }
      }
    },
    plugins: {
      legend: {
        display: false
      },
      datalabels: {
        display: true,
        color: '#ffffff',
        font: {
          size: 10,
          family: "'Inter', sans-serif",
          weight: 'bold'
        },
        align: 'center',
        offset: 8,
        formatter: (value: number) => `${Math.round(value)} MB`
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
            return stats[dataIndex]?.title || 'Unknown';
          },
          label: this.formatTooltipLabel.bind(this)
        }
      }
    },
    interaction: {
      intersect: false,
      mode: 'point'
    }
  };

  private readonly bookSizeChartDataSubject = new BehaviorSubject<BookSizeChartData>({
    labels: [],
    datasets: []
  });

  public readonly bookSizeChartData$: Observable<BookSizeChartData> = this.bookSizeChartDataSubject.asObservable();

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
          console.error('Error processing book size stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateBookSizeStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: BookSizeStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      const labels = stats.map(s => this.truncateTitle(s.title, 30));
      const dataValues = stats.map(s => s.sizeMB);
      const colors = stats.map(s => BOOK_TYPE_COLORS[s.bookType as keyof typeof BOOK_TYPE_COLORS] || '#95a5a6');

      this.bookSizeChartDataSubject.next({
        labels,
        datasets: [{
          label: 'File Size',
          data: dataValues,
          backgroundColor: colors,
          borderColor: colors,
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating book size chart data:', error);
    }
  }

  private calculateBookSizeStats(): BookSizeStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, String(selectedLibraryId));
    return this.processBookSizeStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private processBookSizeStats(books: Book[]): BookSizeStats[] {
    if (books.length === 0) {
      return [];
    }

    const booksWithSize = books
      .filter(book => book.fileSizeKb && book.fileSizeKb > 0)
      .map(book => ({
        title: book.metadata?.title || book.fileName || 'Unknown Title',
        sizeMB: Number((book.fileSizeKb! / 1024).toFixed(2)),
        bookType: book.bookType,
        pageCount: book.metadata?.pageCount || undefined
      }))
      .sort((a, b) => b.sizeMB - a.sizeMB)
      .slice(0, 20);

    return booksWithSize;
  }

  private formatTooltipLabel(context: any): string {
    const dataIndex = context.dataIndex;
    const stats = this.getLastCalculatedStats();

    if (!stats || dataIndex >= stats.length) {
      return `${context.parsed.x} MB`;
    }

    const book = stats[dataIndex];
    const sizeInfo = `${book.sizeMB} MB`;
    const typeInfo = `Format: ${book.bookType}`;
    const pageInfo = book.pageCount ? `Pages: ${book.pageCount}` : 'Pages: Unknown';

    return `${sizeInfo} | ${typeInfo} | ${pageInfo}`;
  }

  private lastCalculatedStats: BookSizeStats[] = [];

  private getLastCalculatedStats(): BookSizeStats[] {
    return this.lastCalculatedStats;
  }

  private truncateTitle(title: string, maxLength: number): string {
    return title.length > maxLength ? title.substring(0, maxLength) + '...' : title;
  }
}
