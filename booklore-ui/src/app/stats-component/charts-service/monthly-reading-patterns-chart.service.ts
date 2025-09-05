import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book, ReadStatus} from '../../book/model/book.model';

interface MonthlyPattern {
  month: string;
  booksStarted: number;
  booksFinished: number;
  booksAdded: number;
  averageProgress: number;
  totalReadingTime: number;
  genreDiversity: number;
}

const CHART_COLORS = {
  booksStarted: '#3498db',
  booksFinished: '#2ecc71',
  booksAdded: '#f39c12',
  averageProgress: '#e74c3c',
  genreDiversity: '#9b59b6'
} as const;

type MonthlyPatternsChartData = ChartData<'line', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class MonthlyReadingPatternsChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly monthlyPatternsChartType = 'line' as const;

  public readonly monthlyPatternsChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: {
        type: 'category',
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        title: {
          display: true,
          text: 'Month',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          }
        }
      },
      y: {
        type: 'linear',
        display: true,
        position: 'left',
        beginAtZero: true,
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        title: {
          display: true,
          text: 'Book Count',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          }
        }
      },
      y1: {
        type: 'linear',
        display: true,
        position: 'right',
        beginAtZero: true,
        max: 100,
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
          callback: function(value) {
            return value + '%';
          }
        },
        grid: {
          drawOnChartArea: false
        },
        title: {
          display: true,
          text: 'Progress %',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          }
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
            size: 11.5
          },
          padding: 12,
          usePointStyle: true
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
          title: (context) => context[0]?.label || '',
          label: this.formatTooltipLabel.bind(this)
        }
      }
    },
    interaction: {
      intersect: false,
      mode: 'index'
    },
    elements: {
      point: {
        radius: 4,
        hoverRadius: 6
      },
      line: {
        tension: 0.3,
        borderWidth: 2
      }
    }
  };

  private readonly monthlyPatternsChartDataSubject = new BehaviorSubject<MonthlyPatternsChartData>({
    labels: [],
    datasets: []
  });

  public readonly monthlyPatternsChartData$: Observable<MonthlyPatternsChartData> = this.monthlyPatternsChartDataSubject.asObservable();

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
          console.error('Error processing monthly patterns stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateMonthlyPatternsStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: MonthlyPattern[]): void {
    try {
      this.lastCalculatedStats = stats;
      const labels = stats.map(s => s.month);

      const datasets = [
        {
          label: 'Books Started',
          data: stats.map(s => s.booksStarted),
          borderColor: CHART_COLORS.booksStarted,
          backgroundColor: CHART_COLORS.booksStarted + '20',
          yAxisID: 'y',
          tension: 0.3,
          fill: false,
          pointStyle: 'circle'
        },
        {
          label: 'Books Finished',
          data: stats.map(s => s.booksFinished),
          borderColor: CHART_COLORS.booksFinished,
          backgroundColor: CHART_COLORS.booksFinished + '20',
          yAxisID: 'y',
          tension: 0.3,
          fill: false,
          pointStyle: 'triangle'
        },
        {
          label: 'Books Added',
          data: stats.map(s => s.booksAdded),
          borderColor: CHART_COLORS.booksAdded,
          backgroundColor: CHART_COLORS.booksAdded + '20',
          yAxisID: 'y',
          tension: 0.3,
          fill: false,
          pointStyle: 'rect',
          borderDash: [3, 3]
        },
        {
          label: 'Avg Progress %',
          data: stats.map(s => s.averageProgress),
          borderColor: CHART_COLORS.averageProgress,
          backgroundColor: CHART_COLORS.averageProgress + '20',
          yAxisID: 'y1',
          tension: 0.3,
          fill: true,
          fillOpacity: 0.1,
          pointStyle: 'star'
        },
        {
          label: 'Genre Diversity',
          data: stats.map(s => s.genreDiversity),
          borderColor: CHART_COLORS.genreDiversity,
          backgroundColor: CHART_COLORS.genreDiversity + '20',
          yAxisID: 'y',
          tension: 0.3,
          fill: false,
          pointStyle: 'cross',
          borderDash: [5, 2, 2, 2]
        }
      ];

      this.monthlyPatternsChartDataSubject.next({
        labels,
        datasets
      });
    } catch (error) {
      console.error('Error updating monthly patterns chart data:', error);
    }
  }

  private calculateMonthlyPatternsStats(): MonthlyPattern[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, String(selectedLibraryId));
    return this.processMonthlyPatternsStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private processMonthlyPatternsStats(books: Book[]): MonthlyPattern[] {
    if (books.length === 0) {
      return [];
    }

    // Generate last 12 months
    const monthlyPatterns: MonthlyPattern[] = [];
    const currentDate = new Date();

    for (let i = 11; i >= 0; i--) {
      const monthDate = new Date(currentDate.getFullYear(), currentDate.getMonth() - i, 1);
      const monthKey = this.formatMonthYear(monthDate);
      const displayMonth = this.formatDisplayMonth(monthKey);

      const pattern = this.calculateMonthlyPattern(books, monthDate, displayMonth);
      monthlyPatterns.push(pattern);
    }

    return monthlyPatterns;
  }

  private calculateMonthlyPattern(books: Book[], monthDate: Date, displayMonth: string): MonthlyPattern {
    const year = monthDate.getFullYear();
    const month = monthDate.getMonth();
    const startOfMonth = new Date(year, month, 1);
    const endOfMonth = new Date(year, month + 1, 0, 23, 59, 59);

    // Books started this month (estimated by reading status changes)
    const booksStarted = this.getBooksStartedInMonth(books, startOfMonth, endOfMonth);

    // Books finished this month
    const booksFinished = books.filter(book => {
      if (!book.dateFinished) return false;
      const finishDate = new Date(book.dateFinished);
      return finishDate >= startOfMonth && finishDate <= endOfMonth;
    }).length;

    // Books added this month
    const booksAdded = books.filter(book => {
      if (!book.addedOn) return false;
      const addedDate = new Date(book.addedOn);
      return addedDate >= startOfMonth && addedDate <= endOfMonth;
    }).length;

    // Average progress of books being read
    const averageProgress = this.calculateAverageProgress(books, endOfMonth);

    // Estimated total reading time (placeholder calculation)
    const totalReadingTime = this.estimateMonthlyReadingTime(books, startOfMonth, endOfMonth);

    // Genre diversity (unique categories being read)
    const genreDiversity = this.calculateGenreDiversity(books, startOfMonth, endOfMonth);

    return {
      month: displayMonth,
      booksStarted,
      booksFinished,
      booksAdded,
      averageProgress,
      totalReadingTime,
      genreDiversity
    };
  }

  private getBooksStartedInMonth(books: Book[], startOfMonth: Date, endOfMonth: Date): number {
    // Estimate books started by looking at currently reading books and their last read time
    return books.filter(book => {
      if (book.readStatus !== ReadStatus.READING && book.readStatus !== ReadStatus.PARTIALLY_READ) {
        return false;
      }

      if (book.lastReadTime) {
        const lastReadDate = new Date(book.lastReadTime);
        return lastReadDate >= startOfMonth && lastReadDate <= endOfMonth;
      }

      // Fallback: if added this month and currently reading, assume started this month
      if (book.addedOn) {
        const addedDate = new Date(book.addedOn);
        return addedDate >= startOfMonth && addedDate <= endOfMonth;
      }

      return false;
    }).length;
  }

  private calculateAverageProgress(books: Book[], asOfDate: Date): number {
    const booksInProgress = books.filter(book =>
      book.readStatus === ReadStatus.READING ||
      book.readStatus === ReadStatus.PARTIALLY_READ
    );

    if (booksInProgress.length === 0) return 0;

    const totalProgress = booksInProgress.reduce((sum, book) => {
      const progress = this.getBookProgress(book);
      return sum + progress;
    }, 0);

    return Math.round(totalProgress / booksInProgress.length);
  }

  private getBookProgress(book: Book): number {
    const epubProgress = book.epubProgress?.percentage || 0;
    const pdfProgress = book.pdfProgress?.percentage || 0;
    const cbxProgress = book.cbxProgress?.percentage || 0;
    const koreaderProgress = book.koreaderProgress?.percentage || 0;

    return Math.max(epubProgress, pdfProgress, cbxProgress, koreaderProgress);
  }

  private estimateMonthlyReadingTime(books: Book[], startOfMonth: Date, endOfMonth: Date): number {
    // Simplified estimation based on completed books and their page counts
    const booksFinishedThisMonth = books.filter(book => {
      if (!book.dateFinished) return false;
      const finishDate = new Date(book.dateFinished);
      return finishDate >= startOfMonth && finishDate <= endOfMonth;
    });

    const totalPages = booksFinishedThisMonth.reduce((sum, book) =>
      sum + (book.metadata?.pageCount || 0), 0
    );

    // Assume 1 page per minute reading speed
    return Math.round(totalPages);
  }

  private calculateGenreDiversity(books: Book[], startOfMonth: Date, endOfMonth: Date): number {
    const relevantBooks = books.filter(book => {
      // Books finished, started, or being read during this month
      const wasFinished = book.dateFinished &&
        new Date(book.dateFinished) >= startOfMonth &&
        new Date(book.dateFinished) <= endOfMonth;

      const isReading = book.readStatus === ReadStatus.READING ||
        book.readStatus === ReadStatus.PARTIALLY_READ;

      return wasFinished || isReading;
    });

    const uniqueGenres = new Set<string>();

    relevantBooks.forEach(book => {
      if (book.metadata?.categories) {
        book.metadata.categories.forEach(category => {
          uniqueGenres.add(category.toLowerCase());
        });
      }
    });

    return uniqueGenres.size;
  }

  private formatMonthYear(date: Date): string {
    return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0');
  }

  private formatDisplayMonth(monthKey: string): string {
    const [year, month] = monthKey.split('-');
    const monthNames = [
      'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
      'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'
    ];
    return `${monthNames[parseInt(month) - 1]} ${year}`;
  }

  private formatTooltipLabel(context: any): string {
    const datasetLabel = context.dataset.label;
    const value = context.parsed.y;
    const dataIndex = context.dataIndex;
    const stats = this.getLastCalculatedStats();

    if (!stats || dataIndex >= stats.length) {
      return `${datasetLabel}: ${value}`;
    }

    const monthStats = stats[dataIndex];

    switch (datasetLabel) {
      case 'Books Started':
        return `${value} books started | ${monthStats.genreDiversity} genres explored`;
      case 'Books Finished':
        return `${value} books completed | ${monthStats.totalReadingTime} est. minutes read`;
      case 'Books Added':
        return `${value} books added to library`;
      case 'Avg Progress %':
        return `${value}% average progress on current reads`;
      case 'Genre Diversity':
        return `${value} unique genres being read`;
      default:
        return `${datasetLabel}: ${value}`;
    }
  }

  private lastCalculatedStats: MonthlyPattern[] = [];

  private getLastCalculatedStats(): MonthlyPattern[] {
    return this.lastCalculatedStats;
  }
}
