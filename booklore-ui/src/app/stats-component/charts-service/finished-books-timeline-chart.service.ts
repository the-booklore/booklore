import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData, ChartType} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

interface FinishedBooksStats {
  yearMonth: string;
  count: number;
  year: number;
  month: number;
}

const CHART_COLORS = {
  primary: '#EF476F',
  primaryBackground: 'rgba(239, 71, 111, 0.1)',
  border: '#ffffff'
} as const;

const CHART_DEFAULTS = {
  borderColor: CHART_COLORS.primary,
  backgroundColor: CHART_COLORS.primaryBackground,
  borderWidth: 2,
  pointBackgroundColor: CHART_COLORS.primary,
  pointBorderColor: CHART_COLORS.border,
  pointBorderWidth: 2,
  pointRadius: 4,
  pointHoverRadius: 6,
  fill: true,
  tension: 0.4
} as const;

type FinishedBooksChartData = ChartData<'line', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class FinishedBooksTimelineChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly finishedBooksChartType = 'line' as const;

  public readonly finishedBooksChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {display: false},
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.9)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#ffffff',
        borderWidth: 1,
        cornerRadius: 6,
        displayColors: true,
        padding: 12,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 13},
        position: 'nearest',
        callbacks: {
          title: (context) => {
            return context[0].label; // Already formatted as "Month Year"
          },
          label: this.formatTooltipLabel.bind(this)
        }
      },
      datalabels: {
        display: true,
        color: '#ffffff',
        font: {
          size: 10,
          weight: 'bold'
        },
        align: 'top',
        offset: 8,
        formatter: (value: number) => value.toString()
      }
    },
    interaction: {
      intersect: false,
      mode: 'point'
    },
    scales: {
      x: {
        beginAtZero: true,
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          },
          maxRotation: 45,
          callback: function (value, index, values) {
            const totalLabels = values.length;
            const skipInterval = totalLabels > 24 ? Math.ceil(totalLabels / 12) : totalLabels > 12 ? 2 : 1;
            return index % skipInterval === 0 ? this.getLabelForValue(value as number) : '';
          }
        },
        grid: {color: 'rgba(255, 255, 255, 0.1)'},
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
        beginAtZero: true,
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          },
          stepSize: 1
        },
        grid: {color: 'rgba(255, 255, 255, 0.05)'},
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
    }
  };

  private readonly finishedBooksChartDataSubject = new BehaviorSubject<FinishedBooksChartData>({
    labels: [],
    datasets: [{
      label: 'Books Finished',
      data: [],
      ...CHART_DEFAULTS
    }]
  });

  public readonly finishedBooksChartData$: Observable<FinishedBooksChartData> =
    this.finishedBooksChartDataSubject.asObservable();

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
          console.error('Error processing finished books stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateFinishedBooksStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: FinishedBooksStats[]): void {
    try {
      const labels = stats.map(s => {
        const [year, month] = s.yearMonth.split('-');
        const monthName = new Date(parseInt(year), parseInt(month) - 1).toLocaleString('default', { month: 'short' });
        return `${monthName} ${year}`;
      });
      const dataValues = stats.map(s => s.count);

      this.finishedBooksChartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books Finished',
          data: dataValues,
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating chart data:', error);
    }
  }

  private calculateFinishedBooksStats(): FinishedBooksStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    return this.processFinishedBooksStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private processFinishedBooksStats(books: Book[]): FinishedBooksStats[] {
    const yearMonthMap = new Map<string, number>();

    books.forEach(book => {
      if (book.dateFinished) {
        const finishedDate = new Date(book.dateFinished);
        const yearMonth = `${finishedDate.getFullYear()}-${(finishedDate.getMonth() + 1).toString().padStart(2, '0')}`;
        yearMonthMap.set(yearMonth, (yearMonthMap.get(yearMonth) || 0) + 1);
      }
    });

    return Array.from(yearMonthMap.entries())
      .filter(([yearMonth, count]) => count > 0)
      .map(([yearMonth, count]) => ({
        yearMonth,
        count,
        year: parseInt(yearMonth.split('-')[0]),
        month: parseInt(yearMonth.split('-')[1])
      }))
      .sort((a, b) => a.yearMonth.localeCompare(b.yearMonth));
  }

  private formatTooltipLabel(context: any): string {
    const value = context.parsed.y;
    return `${value} book${value === 1 ? '' : 's'} finished`;
  }
}
