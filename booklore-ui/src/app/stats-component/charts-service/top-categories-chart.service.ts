import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, combineLatest, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError} from 'rxjs/operators';
import {ChartConfiguration, ChartData, ChartType} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

interface CategoryStats {
  category: string;
  count: number;
}

const CHART_COLORS = [
  '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FECA57',
  '#FF9FF3', '#54A0FF', '#5F27CD', '#00D2D3', '#FF9F43',
  '#FF6348', '#2ED573', '#3742FA', '#F368E0', '#FF3838',
  '#FF4757', '#5352ED', '#70A1FF', '#7F8FA6', '#40407A',
  '#2C2C54', '#40407A', '#706FD3', '#F97F51', '#F8B500'
] as const;

const HOVER_COLORS = [
  '#FF5252', '#26A69A', '#2196F3', '#66BB6A', '#FFB74D',
  '#E91E63', '#3F51B5', '#9C27B0', '#00BCD4', '#FF9800',
  '#F44336', '#4CAF50', '#2196F3', '#E91E63', '#FF5722',
  '#FF4081', '#3F51B5', '#5C6BC0', '#607D8B', '#303F9F',
  '#1A237E', '#303F9F', '#5E35B1', '#FF6F00', '#E65100'
] as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 1,
  hoverBorderColor: '#ffffff',
  hoverBorderWidth: 2
} as const;

type CategoriesChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class TopCategoriesChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly categoriesChartType: ChartType = 'bar';

  public readonly categoriesChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: 'y',
    plugins: {
      legend: {
        display: false
      },
      tooltip: {
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#666666',
        borderWidth: 1,
        callbacks: {
          title: () => '',
          label: (context) => {
            const label = context.label || '';
            const value = context.parsed.x;
            return `${label}: ${value} books`;
          }
        }
      }
    },
    scales: {
      x: {
        beginAtZero: true,
        ticks: {
          color: '#ffffff',
          font: {
            size: 12
          }
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        title: {
          display: true,
          text: 'Number of Books',
          color: '#ffffff',
          font: {
            size: 14,
            weight: 'bold'
          }
        }
      },
      y: {
        ticks: {
          color: '#ffffff',
          font: {
            size: 11
          },
          maxRotation: 0,
          callback: function (value, index) {
            const label = this.getLabelForValue(value as number);
            return label.length > 25 ? label.substring(0, 25) + '...' : label;
          }
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.05)'
        }
      }
    }
  };

  private readonly categoriesChartDataSubject = new BehaviorSubject<CategoriesChartData>({
    labels: [],
    datasets: [{
      label: 'Books',
      data: [],
      backgroundColor: [...CHART_COLORS],
      hoverBackgroundColor: [...HOVER_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly categoriesChartData$: Observable<CategoriesChartData> =
    this.categoriesChartDataSubject.asObservable();

  constructor() {
    this.initializeChartDataSubscription();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeChartDataSubscription(): void {
    this.getCategoryStats()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error processing category stats:', error);
          return [];
        })
      )
      .subscribe((stats) => this.updateChartData(stats));
  }

  private updateChartData(stats: CategoryStats[]): void {
    try {
      const reversedStats = [...stats].reverse();
      const labels = reversedStats.map(s => s.category);
      const dataValues = reversedStats.map(s => s.count);

      this.categoriesChartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books',
          data: dataValues,
          backgroundColor: [...CHART_COLORS],
          hoverBackgroundColor: [...HOVER_COLORS],
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating chart data:', error);
    }
  }

  public getCategoryStats(): Observable<CategoryStats[]> {
    return combineLatest([
      this.bookService.bookState$,
      this.libraryFilterService.selectedLibrary$
    ]).pipe(
      map(([state, selectedLibraryId]) => {
        if (!this.isValidBookState(state)) {
          return [];
        }

        const filteredBooks = this.filterBooksByLibrary(state.books!, selectedLibraryId);
        return this.processCategoryStats(filteredBooks);
      }),
      catchError((error) => {
        console.error('Error getting category stats:', error);
        return [];
      })
    );
  }

  public updateFromStats(stats: CategoryStats[]): void {
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

  private processCategoryStats(books: Book[]): CategoryStats[] {
    const categoryMap = new Map<string, number>();

    books.forEach(book => {
      if (book.metadata?.categories && Array.isArray(book.metadata.categories)) {
        book.metadata.categories.forEach(category => {
          if (category) {
            categoryMap.set(category, (categoryMap.get(category) || 0) + 1);
          }
        });
      }
    });

    return Array.from(categoryMap.entries())
      .map(([category, count]) => ({category, count}))
      .sort((a, b) => b.count - a.count)
      .slice(0, 15);
  }
}
