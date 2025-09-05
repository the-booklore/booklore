import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData, ChartType} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

interface PageCountStats {
  category: string;
  count: number;
  avgPages: number;
  minPages: number;
  maxPages: number;
}

const CHART_COLORS = [
  '#81C784', '#4FC3F7', '#FFB74D', '#F06292', '#BA68C8'
] as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: '#ffffff'
} as const;

type PageCountChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class PageCountChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly pageCountChartType: ChartType = 'bar';

  public readonly pageCountChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
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
        padding: 12,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 12},
        callbacks: {
          title: (context) => context[0].label,
          label: (context) => {
            const value = context.parsed.y;
            return `${value} book${value === 1 ? '' : 's'}`;
          }
        }
      }
    },
    scales: {
      x: {
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
          maxRotation: 45
        },
        grid: {color: 'rgba(255, 255, 255, 0.1)'},
        title: {
          display: true,
          text: 'Page Count Category',
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
            size: 11
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

  private readonly pageCountChartDataSubject = new BehaviorSubject<PageCountChartData>({
    labels: [],
    datasets: [{
      label: 'Books by Page Count',
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly pageCountChartData$: Observable<PageCountChartData> =
    this.pageCountChartDataSubject.asObservable();

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
          console.error('Error processing page count stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculatePageCountStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: PageCountStats[]): void {
    try {
      const labels = stats.map(s => s.category);
      const dataValues = stats.map(s => s.count);

      this.pageCountChartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books by Page Count',
          data: dataValues,
          backgroundColor: [...CHART_COLORS],
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating chart data:', error);
    }
  }

  private calculatePageCountStats(): PageCountStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    return this.processPageCountStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private processPageCountStats(books: Book[]): PageCountStats[] {
    const categories = [
      {name: 'Short (< 200)', min: 0, max: 199},
      {name: 'Medium (200-400)', min: 200, max: 400},
      {name: 'Long (401-600)', min: 401, max: 600},
      {name: 'Very Long (601-800)', min: 601, max: 800},
      {name: 'Epic (> 800)', min: 801, max: 9999}
    ];

    return categories.map(category => {
      const booksInCategory = books.filter(book => {
        const pageCount = book.metadata?.pageCount;
        return pageCount && pageCount >= category.min && pageCount <= category.max;
      });

      const pageCounts = booksInCategory
        .map(book => book.metadata?.pageCount || 0)
        .filter(count => count > 0);

      return {
        category: category.name,
        count: booksInCategory.length,
        avgPages: pageCounts.length > 0 ? Math.round(pageCounts.reduce((a, b) => a + b, 0) / pageCounts.length) : 0,
        minPages: pageCounts.length > 0 ? Math.min(...pageCounts) : 0,
        maxPages: pageCounts.length > 0 ? Math.max(...pageCounts) : 0
      };
    }).filter(stat => stat.count > 0);
  }
}
