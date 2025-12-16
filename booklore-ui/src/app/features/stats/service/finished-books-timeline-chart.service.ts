import { inject, Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, EMPTY, Observable, Subject } from 'rxjs';
import { catchError, filter, first, switchMap, takeUntil } from 'rxjs/operators';
import { ChartConfiguration, ChartData } from 'chart.js';

import { LibraryFilterService } from './library-filter.service';
import { BookService } from '../../book/service/book.service';
import { Book, ReadStatus } from '../../book/model/book.model';

interface FinishedBooksStats {
  yearMonth: string;
  count: number;
  year: number;
  month: number;
}

const CHART_COLORS = {
  finishedBooks: '#2ecc71',
} as const;

type FinishedBooksTimelineChartData = ChartData<'line', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class FinishedBooksTimelineChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly finishedBooksTimelineChartType = 'line' as const;

  public readonly finishedBooksTimelineChartOptions: ChartConfiguration<'line'>['options'] = {
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
          text: 'Books Finished',
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
        bodyFont: { size: 12 }
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

  private readonly finishedBooksTimelineChartDataSubject = new BehaviorSubject<FinishedBooksTimelineChartData>({
    labels: [],
    datasets: []
  });

  public readonly finishedBooksTimelineChartData$: Observable<FinishedBooksTimelineChartData> = 
    this.finishedBooksTimelineChartDataSubject.asObservable();

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
          console.error('Error processing finished books timeline stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateFinishedBooksTimelineStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: FinishedBooksStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      const labels = stats.map(s => s.yearMonth);

      const datasets = [
        {
          label: 'Books Finished',
          data: stats.map(s => s.count),
          borderColor: CHART_COLORS.finishedBooks,
          backgroundColor: CHART_COLORS.finishedBooks + '20',
          yAxisID: 'y',
          tension: 0.2,
          fill: false
        }
      ];

      this.finishedBooksTimelineChartDataSubject.next({
        labels,
        datasets
      });
    } catch (error) {
      console.error('Error updating finished books timeline chart data:', error);
    }
  }

  private calculateFinishedBooksTimelineStats(): FinishedBooksStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, String(selectedLibraryId));
    return this.processFinishedBooksStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private processFinishedBooksStats(books: Book[]): FinishedBooksStats[] {
    const yearMonthMap = new Map<string, number>();

    books.forEach(book => {
      if (book.readStatus === ReadStatus.READ && book.dateFinished) {
        const finishedDate = new Date(book.dateFinished);
        const yearMonth = `${finishedDate.getFullYear()}-${(finishedDate.getMonth() + 1).toString().padStart(2, '0')}`;
        yearMonthMap.set(yearMonth, (yearMonthMap.get(yearMonth) || 0) + 1);
      }
    });

    // Get the range of months
    const monthsWithData = Array.from(yearMonthMap.keys()).sort();

    if (monthsWithData.length === 0) {
      return [];
    }

    // Fill in all missing months between first and last data points
    const startDate = this.parseYearMonth(monthsWithData[0]);
    const endDate = this.parseYearMonth(monthsWithData[monthsWithData.length - 1]);

    const completeStats: FinishedBooksStats[] = [];
    let currentDate = new Date(startDate.year, startDate.month - 1, 1);
    const endDateObj = new Date(endDate.year, endDate.month - 1, 1);

    while (currentDate <= endDateObj) {
      const year = currentDate.getFullYear();
      const month = currentDate.getMonth() + 1;
      const yearMonth = `${year}-${month.toString().padStart(2, '0')}`;

      completeStats.push({
        yearMonth,
        count: yearMonthMap.get(yearMonth) || 0,
        year,
        month
      });

      // Move to next month
      currentDate.setMonth(currentDate.getMonth() + 1);
    }

    return completeStats.sort((a, b) => a.yearMonth.localeCompare(b.yearMonth));
  }

  private parseYearMonth(yearMonthStr: string): { year: number, month: number } {
    const [year, month] = yearMonthStr.split('-').map(Number);
    return { year, month };
  }

  private lastCalculatedStats: FinishedBooksStats[] = [];
}