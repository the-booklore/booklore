import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData, ChartType} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

interface PublicationYearStats {
  year: string;
  count: number;
  decade: string;
}

const CHART_COLORS = {
  primary: '#4ECDC4',
  primaryBackground: 'rgba(78, 205, 196, 0.1)',
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

type YearChartData = ChartData<'line', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class PublicationYearChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly yearChartType = 'line' as const;

  public readonly yearChartOptions: ChartConfiguration['options'] = {
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
          title: (context) => `Year ${context[0].label}`,
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
            // Show every 5th year to avoid crowding
            return index % 5 === 0 ? this.getLabelForValue(value as number) : '';
          }
        },
        grid: {color: 'rgba(255, 255, 255, 0.1)'},
        title: {
          display: true,
          text: 'Publication Year',
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
          text: 'Number of Books',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11.5
          }
        }
      }
    }
  };

  private readonly yearChartDataSubject = new BehaviorSubject<YearChartData>({
    labels: [],
    datasets: [{
      label: 'Books Published',
      data: [],
      ...CHART_DEFAULTS
    }]
  });

  public readonly yearChartData$: Observable<YearChartData> =
    this.yearChartDataSubject.asObservable();

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
          console.error('Error processing publication year stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculatePublicationYearStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: PublicationYearStats[]): void {
    try {
      const labels = stats.map(s => s.year);
      const dataValues = stats.map(s => s.count);

      this.yearChartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books Published',
          data: dataValues,
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating chart data:', error);
    }
  }

  private calculatePublicationYearStats(): PublicationYearStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    return this.processPublicationYearStats(filteredBooks);
  }

  public updateFromStats(stats: PublicationYearStats[]): void {
    this.updateChartData(stats);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private processPublicationYearStats(books: Book[]): PublicationYearStats[] {
    const yearMap = new Map<string, number>();

    books.forEach(book => {
      if (book.metadata?.publishedDate) {
        const year = this.extractYear(book.metadata.publishedDate);
        if (year && year >= 1800 && year <= new Date().getFullYear()) {
          const yearStr = year.toString();
          yearMap.set(yearStr, (yearMap.get(yearStr) || 0) + 1);
        }
      }
    });

    // Only return years that have books (no 0 entries)
    return Array.from(yearMap.entries())
      .filter(([year, count]) => count > 0)
      .map(([year, count]) => ({
        year,
        count,
        decade: `${Math.floor(parseInt(year) / 10) * 10}s`
      }))
      .sort((a, b) => parseInt(a.year) - parseInt(b.year));
  }

  private extractYear(dateString: string): number | null {
    const yearMatch = dateString.match(/(\d{4})/);
    return yearMatch ? parseInt(yearMatch[1]) : null;
  }

  private formatTooltipLabel(context: any): string {
    const value = context.parsed.y;
    return `${value} book${value === 1 ? '' : 's'} published`;
  }
}
