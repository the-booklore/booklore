import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, combineLatest, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book, ReadStatus} from '../../book/model/book.model';

interface SeriesCompletionStats {
  seriesName: string;
  totalBooks: number;
  ownedBooks: number;
  readBooks: number;
  completionPercentage: number;
  collectionPercentage: number;
  isComplete: boolean;
  averageRating: number;
}

const CHART_COLORS = [
  '#2ecc71', '#3498db', '#e74c3c', '#f39c12', '#9b59b6',
  '#1abc9c', '#34495e', '#e67e22', '#95a5a6', '#27ae60',
  '#2980b9', '#c0392b', '#d35400', '#8e44ad', '#16a085'
] as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: '#ffffff'
} as const;

type SeriesCompletionChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class SeriesCompletionProgressChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly seriesCompletionChartType = 'bar' as const;

  public readonly seriesCompletionChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: {
        ticks: {
          color: '#ffffff',
          font: { size: 10 },
          maxRotation: 45
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        title: {
          display: true,
          text: 'Series',
          color: '#ffffff',
          font: { size: 12 }
        }
      },
      y: {
        beginAtZero: true,
        max: 100,
        ticks: {
          color: '#ffffff',
          font: { size: 10 },
          callback: function(value) {
            return value + '%';
          }
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        title: {
          display: true,
          text: 'Completion %',
          color: '#ffffff',
          font: { size: 12 }
        }
      }
    },
    plugins: {
      legend: {
        display: true,
        position: 'top',
        labels: {
          color: '#ffffff',
          font: { size: 11 },
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
    }
  };

  private readonly seriesCompletionChartDataSubject = new BehaviorSubject<SeriesCompletionChartData>({
    labels: [],
    datasets: []
  });

  public readonly seriesCompletionChartData$: Observable<SeriesCompletionChartData> = this.seriesCompletionChartDataSubject.asObservable();

  constructor() {
    this.initializeChartDataSubscription();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeChartDataSubscription(): void {
    this.getSeriesCompletionStats()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error processing series completion stats:', error);
          return [];
        })
      )
      .subscribe((stats) => this.updateChartData(stats));
  }

  private updateChartData(stats: SeriesCompletionStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      const labels = stats.map(s => this.truncateSeriesName(s.seriesName, 20));

      const datasets = [
        {
          label: 'Reading Progress',
          data: stats.map(s => s.completionPercentage),
          backgroundColor: '#2ecc71',
          ...CHART_DEFAULTS
        },
        {
          label: 'Collection Progress',
          data: stats.map(s => s.collectionPercentage),
          backgroundColor: '#3498db',
          ...CHART_DEFAULTS
        }
      ];

      this.seriesCompletionChartDataSubject.next({
        labels,
        datasets
      });
    } catch (error) {
      console.error('Error updating series completion chart data:', error);
    }
  }

  private truncateSeriesName(name: string, maxLength: number): string {
    if (name.length <= maxLength) return name;
    return name.substring(0, maxLength - 3) + '...';
  }

  public getSeriesCompletionStats(): Observable<SeriesCompletionStats[]> {
    return combineLatest([
      this.bookService.bookState$,
      this.libraryFilterService.selectedLibrary$
    ]).pipe(
      map(([state, selectedLibraryId]) => {
        if (!this.isValidBookState(state)) {
          return [];
        }

        const filteredBooks = this.filterBooksByLibrary(state.books!, String(selectedLibraryId));
        return this.processSeriesCompletionStats(filteredBooks);
      }),
      catchError((error) => {
        console.error('Error getting series completion stats:', error);
        return [];
      })
    );
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private processSeriesCompletionStats(books: Book[]): SeriesCompletionStats[] {
    if (books.length === 0) {
      return [];
    }

    // Group books by series
    const seriesMap = new Map<string, Book[]>();

    books.forEach(book => {
      const seriesName = book.metadata?.seriesName;
      if (seriesName && seriesName.trim()) {
        if (!seriesMap.has(seriesName)) {
          seriesMap.set(seriesName, []);
        }
        seriesMap.get(seriesName)!.push(book);
      }
    });

    // Calculate completion stats for each series
    const seriesStats: SeriesCompletionStats[] = [];

    for (const [seriesName, seriesBooks] of seriesMap) {
      const stats = this.calculateSeriesStats(seriesName, seriesBooks);
      if (stats.totalBooks > 1) { // Only include actual series (more than 1 book)
        seriesStats.push(stats);
      }
    }

    return seriesStats
      .sort((a, b) => {
        // Sort by completion percentage, then by collection percentage
        if (a.completionPercentage !== b.completionPercentage) {
          return b.completionPercentage - a.completionPercentage;
        }
        return b.collectionPercentage - a.collectionPercentage;
      })
      .slice(0, 15); // Top 15 series
  }

  private calculateSeriesStats(seriesName: string, books: Book[]): SeriesCompletionStats {
    // Determine total books in series
    const seriesTotals = books
      .map(book => book.metadata?.seriesTotal)
      .filter((total): total is number => total != null && total > 0);

    const totalBooks = seriesTotals.length > 0
      ? Math.max(...seriesTotals)
      : books.length; // Fallback to owned books count

    const ownedBooks = books.length;
    const readBooks = books.filter(book => book.readStatus === ReadStatus.READ).length;

    // Calculate completion and collection percentages
    const completionPercentage = totalBooks > 0
      ? Math.round((readBooks / totalBooks) * 100)
      : 0;

    const collectionPercentage = totalBooks > 0
      ? Math.round((ownedBooks / totalBooks) * 100)
      : 100;

    const isComplete = ownedBooks >= totalBooks;

    // Calculate average rating
    const ratedBooks = books.filter(book => {
      const rating = book.metadata?.personalRating || book.metadata?.goodreadsRating;
      return rating && rating > 0;
    });

    const averageRating = ratedBooks.length > 0
      ? Number((ratedBooks.reduce((sum, book) => {
          const rating = book.metadata?.personalRating || book.metadata?.goodreadsRating || 0;
          return sum + rating;
        }, 0) / ratedBooks.length).toFixed(1))
      : 0;

    return {
      seriesName,
      totalBooks,
      ownedBooks,
      readBooks,
      completionPercentage,
      collectionPercentage,
      isComplete,
      averageRating
    };
  }

  private formatTooltipLabel(context: any): string {
    const dataIndex = context.dataIndex;
    const datasetLabel = context.dataset.label;
    const value = context.parsed.y;
    const stats = this.getLastCalculatedStats();

    if (!stats || dataIndex >= stats.length) {
      return `${datasetLabel}: ${value}%`;
    }

    const series = stats[dataIndex];

    if (datasetLabel === 'Reading Progress') {
      return `${value}% read (${series.readBooks}/${series.totalBooks} books) | Avg rating: ${series.averageRating}/5`;
    } else if (datasetLabel === 'Collection Progress') {
      return `${value}% collected (${series.ownedBooks}/${series.totalBooks} books) | ${series.isComplete ? 'Complete!' : 'Incomplete'}`;
    }

    return `${datasetLabel}: ${value}%`;
  }

  private lastCalculatedStats: SeriesCompletionStats[] = [];

  private getLastCalculatedStats(): SeriesCompletionStats[] {
    return this.lastCalculatedStats;
  }
}
