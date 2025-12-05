import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';
import {ChartConfiguration, ChartData, ChartType} from 'chart.js';

interface FinishedBooksStats {
  yearMonth: string;
  count: number;
  year: number;
  month: number;
}

const CHART_COLORS = {
  primary: '#3B82F6',
  primaryHover: '#2563EB',
  grid: 'rgba(156, 163, 175, 0.2)',
  text: '#6B7280'
} as const;

type FinishedBooksChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class FinishedBooksTimelineChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType: ChartType = 'bar';

  public readonly chartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false
      },
      tooltip: {
        backgroundColor: 'rgba(17, 24, 39, 0.9)',
        titleFont: {family: "'Inter', sans-serif", size: 12},
        bodyFont: {family: "'Inter', sans-serif", size: 11},
        padding: 10,
        cornerRadius: 6,
        callbacks: {
          label: (context) => `Books finished: ${context.parsed.y}`
        }
      }
    },
    scales: {
      x: {
        grid: {color: CHART_COLORS.grid},
        ticks: {
          font: {family: "'Inter', sans-serif", size: 10},
          color: CHART_COLORS.text,
          maxRotation: 45
        }
      },
      y: {
        beginAtZero: true,
        grid: {color: CHART_COLORS.grid},
        ticks: {
          font: {family: "'Inter', sans-serif", size: 10},
          color: CHART_COLORS.text,
          stepSize: 1
        }
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<FinishedBooksChartData>({
    labels: [],
    datasets: []
  });
  public readonly chartData$: Observable<FinishedBooksChartData> = this.chartDataSubject.asObservable();

  constructor() {
    this.initializeChartData();
  }

  private initializeChartData(): void {
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
        const currentState = this.bookService.getCurrentBookState();
        const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

        if (!currentState?.loaded || !currentState?.books) {
          return;
        }

        const filteredBooks = selectedLibraryId
          ? currentState.books.filter(book => book.libraryId === selectedLibraryId)
          : currentState.books;

        const stats = this.processFinishedBooksStats(filteredBooks);
        const chartData = this.createChartData(stats);
        this.chartDataSubject.next(chartData);
      });
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

    const monthsWithData = Array.from(yearMonthMap.keys()).sort();

    if (monthsWithData.length === 0) {
      return [];
    }

    const startDate = this.parseYearMonth(monthsWithData[0]);
    const endDate = this.parseYearMonth(monthsWithData[monthsWithData.length - 1]);

    const completeStats: FinishedBooksStats[] = [];
    const currentDate = new Date(startDate.year, startDate.month - 1, 1);
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

      currentDate.setMonth(currentDate.getMonth() + 1);
    }

    return completeStats.sort((a, b) => a.yearMonth.localeCompare(b.yearMonth));
  }

  private parseYearMonth(yearMonthStr: string): {year: number; month: number} {
    const [year, month] = yearMonthStr.split('-').map(Number);
    return {year, month};
  }

  private createChartData(stats: FinishedBooksStats[]): FinishedBooksChartData {
    const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

    return {
      labels: stats.map(s => `${monthNames[s.month - 1]} ${s.year}`),
      datasets: [{
        data: stats.map(s => s.count),
        backgroundColor: CHART_COLORS.primary,
        hoverBackgroundColor: CHART_COLORS.primaryHover,
        borderRadius: 4,
        barThickness: 'flex',
        maxBarThickness: 40
      }]
    };
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}

