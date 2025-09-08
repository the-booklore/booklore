import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book, ReadStatus} from '../../book/model/book.model';

interface VelocityTimelineData {
  month: string;
  booksCompleted: number;
  totalPages: number;
  averagePages: number;
  averageRating: number;
  avgPagesPerDay: number;
  readingVelocity: number; // Books per month
}

const CHART_COLORS = {
  booksCompleted: '#3498db',
  avgPagesPerDay: '#e74c3c',
  averageRating: '#f39c12',
  readingVelocity: '#2ecc71'
} as const;

type VelocityTimelineChartData = ChartData<'line', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class ReadingVelocityTimelineChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly velocityTimelineChartType = 'line' as const;

  public readonly velocityTimelineChartOptions: ChartConfiguration<'line'>['options'] = {
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
          },
          maxRotation: 45
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
          text: 'Books Completed',
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
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {
          drawOnChartArea: false
        },
        title: {
          display: true,
          text: 'Pages per Day',
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
          padding: 15,
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
        titleFont: { size: 14, weight: 'bold' },
        bodyFont: { size: 12 },
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
        tension: 0.2,
        borderWidth: 2
      }
    }
  };

  private readonly velocityTimelineChartDataSubject = new BehaviorSubject<VelocityTimelineChartData>({
    labels: [],
    datasets: []
  });

  public readonly velocityTimelineChartData$: Observable<VelocityTimelineChartData> = this.velocityTimelineChartDataSubject.asObservable();

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
          console.error('Error processing velocity timeline stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateVelocityTimelineStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: VelocityTimelineData[]): void {
    try {
      this.lastCalculatedStats = stats;
      const labels = stats.map(s => s.month);

      const datasets = [
        {
          label: 'Books Completed',
          data: stats.map(s => s.booksCompleted),
          borderColor: CHART_COLORS.booksCompleted,
          backgroundColor: CHART_COLORS.booksCompleted + '20',
          yAxisID: 'y',
          tension: 0.2,
          fill: false
        },
        {
          label: 'Avg Pages/Day',
          data: stats.map(s => s.avgPagesPerDay),
          borderColor: CHART_COLORS.avgPagesPerDay,
          backgroundColor: CHART_COLORS.avgPagesPerDay + '20',
          yAxisID: 'y1',
          tension: 0.2,
          fill: false
        },
        {
          label: 'Avg Rating (×2)',
          data: stats.map(s => s.averageRating * 2), // Scale for visibility
          borderColor: CHART_COLORS.averageRating,
          backgroundColor: CHART_COLORS.averageRating + '20',
          yAxisID: 'y',
          tension: 0.2,
          fill: false,
          borderDash: [5, 5]
        },
        {
          label: 'Reading Velocity',
          data: stats.map(s => s.readingVelocity),
          borderColor: CHART_COLORS.readingVelocity,
          backgroundColor: CHART_COLORS.readingVelocity + '20',
          yAxisID: 'y',
          tension: 0.2,
          fill: true,
          fillOpacity: 0.1
        }
      ];

      this.velocityTimelineChartDataSubject.next({
        labels,
        datasets
      });
    } catch (error) {
      console.error('Error updating velocity timeline chart data:', error);
    }
  }

  private calculateVelocityTimelineStats(): VelocityTimelineData[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, String(selectedLibraryId));
    return this.processVelocityTimelineStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private processVelocityTimelineStats(books: Book[]): VelocityTimelineData[] {
    if (books.length === 0) {
      return [];
    }

    // Filter completed books with finish dates
    const completedBooks = books.filter(book =>
      book.readStatus === ReadStatus.READ &&
      book.dateFinished
    );

    if (completedBooks.length === 0) {
      return [];
    }

    // Group books by month-year
    const monthlyData = new Map<string, Book[]>();

    for (const book of completedBooks) {
      const finishDate = new Date(book.dateFinished!);
      if (isNaN(finishDate.getTime())) continue;

      const monthKey = this.formatMonthYear(finishDate);
      if (!monthlyData.has(monthKey)) {
        monthlyData.set(monthKey, []);
      }
      monthlyData.get(monthKey)!.push(book);
    }

    // Convert to timeline data and sort chronologically
    const timelineData = Array.from(monthlyData.entries())
      .map(([monthKey, monthBooks]) => this.calculateMonthlyMetrics(monthKey, monthBooks))
      .sort((a, b) => new Date(a.month + '-01').getTime() - new Date(b.month + '-01').getTime())
      .slice(-24); // Last 24 months

    return timelineData;
  }

  private formatMonthYear(date: Date): string {
    return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0');
  }

  private calculateMonthlyMetrics(monthKey: string, books: Book[]): VelocityTimelineData {
    const totalPages = books.reduce((sum, book) => sum + (book.metadata?.pageCount || 0), 0);
    const averagePages = books.length > 0 ? Math.round(totalPages / books.length) : 0;

    // Calculate average rating
    const ratedBooks = books.filter(book => book.metadata?.personalRating || book.metadata?.goodreadsRating);
    const totalRating = ratedBooks.reduce((sum, book) => {
      const rating = book.metadata?.personalRating || book.metadata?.goodreadsRating || 0;
      return sum + rating;
    }, 0);
    const averageRating = ratedBooks.length > 0 ? Number((totalRating / ratedBooks.length).toFixed(1)) : 0;

    // Calculate days in month for pages per day calculation
    const [year, month] = monthKey.split('-').map(Number);
    const daysInMonth = new Date(year, month, 0).getDate();
    const avgPagesPerDay = Math.round(totalPages / daysInMonth);

    // Reading velocity is books per month
    const readingVelocity = books.length;

    return {
      month: this.formatDisplayMonth(monthKey),
      booksCompleted: books.length,
      totalPages,
      averagePages,
      averageRating,
      avgPagesPerDay,
      readingVelocity
    };
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
      case 'Books Completed':
        return `${value} books completed | ${monthStats.totalPages} total pages`;
      case 'Avg Pages/Day':
        return `${value} pages/day | ${monthStats.averagePages} avg pages/book`;
      case 'Avg Rating (×2)':
        const actualRating = value / 2;
        return `${actualRating.toFixed(1)}/5 avg rating | ${monthStats.booksCompleted} books rated`;
      case 'Reading Velocity':
        return `${value} books/month | ${monthStats.avgPagesPerDay} pages/day velocity`;
      default:
        return `${datasetLabel}: ${value}`;
    }
  }

  private lastCalculatedStats: VelocityTimelineData[] = [];

  private getLastCalculatedStats(): VelocityTimelineData[] {
    return this.lastCalculatedStats;
  }
}
