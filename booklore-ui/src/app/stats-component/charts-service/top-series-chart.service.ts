import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

interface SeriesStats {
  seriesName: string;
  bookCount: number;
}

const CHART_COLORS = [
  '#4e79a7', '#f28e2c', '#e15759', '#76b7b2',
  '#59a14f', '#edc949', '#af7aa1', '#ff9da7',
  '#9c755f', '#bab0ab', '#5778a4', '#e69138',
  '#d62728', '#6aa7b8', '#54a24b', '#fdd247',
  '#b07aa1', '#ff9d9a', '#9e6762', '#c9b2d6'
] as const;

const CHART_DEFAULTS = {
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: '#ffffff'
} as const;

type SeriesChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class TopSeriesChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly seriesChartType = 'bar' as const;

  public readonly seriesChartOptions: ChartConfiguration<'bar'>['options'] = {
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
          precision: 0
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        title: {
          display: true,
          text: 'Number of Books',
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
            size: 11
          },
          maxTicksLimit: 20
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
            return stats[dataIndex]?.seriesName || 'Unknown Series';
          },
          label: this.formatTooltipLabel.bind(this)
        }
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
        formatter: (value: number) => value.toString()
      }
    },
    interaction: {
      intersect: false,
      mode: 'point'
    }
  };

  private readonly seriesChartDataSubject = new BehaviorSubject<SeriesChartData>({
    labels: [],
    datasets: []
  });

  public readonly seriesChartData$: Observable<SeriesChartData> = this.seriesChartDataSubject.asObservable();

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
          console.error('Error processing top series stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateTopSeriesStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: SeriesStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      const labels = stats.map(s => this.truncateTitle(s.seriesName, 30));
      const dataValues = stats.map(s => s.bookCount);
      const colors = this.getColorsForData(stats.length);

      this.seriesChartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books',
          data: dataValues,
          backgroundColor: colors,
          borderColor: colors,
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating series chart data:', error);
    }
  }

  private calculateTopSeriesStats(): SeriesStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, String(selectedLibraryId));
    return this.processTopSeriesStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private processTopSeriesStats(books: Book[]): SeriesStats[] {
    if (books.length === 0) {
      return [];
    }

    const seriesMap = this.buildSeriesMap(books);
    return this.convertMapToStats(seriesMap);
  }

  private buildSeriesMap(books: Book[]): Map<string, number> {
    const seriesMap = new Map<string, number>();

    for (const book of books) {
      const seriesName = book.metadata?.seriesName;
      if (seriesName && seriesName.trim()) {
        const normalizedName = seriesName.trim();
        seriesMap.set(normalizedName, (seriesMap.get(normalizedName) || 0) + 1);
      }
    }

    return seriesMap;
  }

  private convertMapToStats(seriesMap: Map<string, number>): SeriesStats[] {
    return Array.from(seriesMap.entries())
      .map(([seriesName, bookCount]) => ({
        seriesName,
        bookCount
      }))
      .sort((a, b) => b.bookCount - a.bookCount)
      .slice(0, 20);
  }

  private getColorsForData(dataLength: number): string[] {
    const colors = [...CHART_COLORS];
    while (colors.length < dataLength) {
      colors.push(...CHART_COLORS);
    }
    return colors.slice(0, dataLength);
  }

  private formatTooltipLabel(context: any): string {
    const dataIndex = context.dataIndex;
    const stats = this.getLastCalculatedStats();

    if (!stats || dataIndex >= stats.length) {
      return `${context.parsed.x} books`;
    }

    const series = stats[dataIndex];
    const bookCount = series.bookCount;
    const bookText = bookCount === 1 ? 'book' : 'books';

    return `${bookCount} ${bookText}`;
  }

  private lastCalculatedStats: SeriesStats[] = [];

  private getLastCalculatedStats(): SeriesStats[] {
    return this.lastCalculatedStats;
  }

  private truncateTitle(title: string, maxLength: number): string {
    return title.length > maxLength ? title.substring(0, maxLength) + '...' : title;
  }
}
