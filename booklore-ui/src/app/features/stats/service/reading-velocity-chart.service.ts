import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, combineLatest, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book, ReadStatus} from '../../book/model/book.model';

interface ReadingVelocityStats {
  category: string;
  count: number;
  averagePages: number;
  averageRating: number;
  description: string;
}

const CHART_COLORS = [
  '#ff6b9d', '#45aaf2', '#96f7d2', '#feca57', '#ff9ff3',
  '#54a0ff', '#5f27cd', '#00d2d3', '#ff9f43', '#10ac84',
  '#ee5a6f', '#60a3bc', '#130f40', '#30336b', '#535c68'
] as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 2,
  hoverBorderWidth: 3,
  hoverBorderColor: '#ffffff'
} as const;

type VelocityChartData = ChartData<'polarArea', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class ReadingVelocityChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly velocityChartType = 'polarArea' as const;

  public readonly velocityChartOptions: ChartConfiguration<'polarArea'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      r: {
        beginAtZero: true,
        ticks: {
          color: '#ffffff',
          font: {size: 10},
          stepSize: 1,
          backdropColor: 'transparent'
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.15)'
        },
        angleLines: {
          color: 'rgba(255, 255, 255, 0.15)'
        },
        pointLabels: {
          color: '#ffffff',
          font: {size: 10}
        }
      }
    },
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          color: '#ffffff',
          font: {size: 10},
          padding: 10,
          usePointStyle: true,
          generateLabels: this.generateLegendLabels.bind(this)
        }
      },
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
        bodyFont: {size: 12},
        position: 'nearest',
        callbacks: {
          title: (context) => context[0]?.label || '',
          label: this.formatTooltipLabel.bind(this)
        }
      }
    },
    interaction: {
      intersect: false,
      mode: 'point'
    }
  };

  private readonly velocityChartDataSubject = new BehaviorSubject<VelocityChartData>({
    labels: [],
    datasets: [{
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly velocityChartData$: Observable<VelocityChartData> = this.velocityChartDataSubject.asObservable();

  constructor() {
    this.initializeChartDataSubscription();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeChartDataSubscription(): void {
    this.getReadingVelocityStats()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error processing reading velocity stats:', error);
          return [];
        })
      )
      .subscribe((stats) => this.updateChartData(stats));
  }

  private updateChartData(stats: ReadingVelocityStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      const labels = stats.map(s => s.category);
      const dataValues = stats.map(s => s.count);
      const colors = this.getColorsForData(stats.length);

      this.velocityChartDataSubject.next({
        labels,
        datasets: [{
          data: dataValues,
          backgroundColor: colors,
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating velocity chart data:', error);
    }
  }

  private getColorsForData(dataLength: number): string[] {
    const colors = [...CHART_COLORS];
    while (colors.length < dataLength) {
      colors.push(...CHART_COLORS);
    }
    return colors.slice(0, dataLength);
  }

  public getReadingVelocityStats(): Observable<ReadingVelocityStats[]> {
    return combineLatest([
      this.bookService.bookState$,
      this.libraryFilterService.selectedLibrary$
    ]).pipe(
      map(([state, selectedLibraryId]) => {
        if (!this.isValidBookState(state)) {
          return [];
        }

        const filteredBooks = this.filterBooksByLibrary(state.books!, String(selectedLibraryId));
        return this.processReadingVelocityStats(filteredBooks);
      }),
      catchError((error) => {
        console.error('Error getting reading velocity stats:', error);
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

  private processReadingVelocityStats(books: Book[]): ReadingVelocityStats[] {
    if (books.length === 0) {
      return [];
    }

    const velocityCategories = this.categorizeByReadingVelocity(books);
    return this.convertToVelocityStats(velocityCategories);
  }

  private categorizeByReadingVelocity(books: Book[]): Map<string, Book[]> {
    const categories = new Map<string, Book[]>();

    // Initialize categories
    categories.set('Speed Readers', []);
    categories.set('Consistent Readers', []);
    categories.set('Selective Readers', []);
    categories.set('Exploratory Readers', []);
    categories.set('Deep Readers', []);
    categories.set('Casual Readers', []);
    categories.set('Quality Seekers', []);

    const readBooks = books.filter(book =>
      book.readStatus === ReadStatus.READ &&
      book.metadata?.pageCount &&
      book.metadata.pageCount > 0
    );

    const completedBooks = readBooks.length;
    const totalBooks = books.length;
    const completionRate = completedBooks / totalBooks;

    const averagePageCount = readBooks.reduce((sum, book) => sum + (book.metadata?.pageCount || 0), 0) / readBooks.length || 0;
    const averageRating = this.calculateAverageRating(readBooks);

    for (const book of books) {
      const pageCount = book.metadata?.pageCount || 0;
      const hasHighRating = this.hasHighQualityRating(book);
      const isCompleted = book.readStatus === ReadStatus.READ;
      const isCurrentlyReading = book.readStatus === ReadStatus.READING;
      const progress = this.getReadingProgress(book);

      // Speed Readers: High completion rate + prefer shorter books
      if (completionRate > 0.6 && pageCount > 0 && pageCount < averagePageCount * 0.8) {
        categories.get('Speed Readers')!.push(book);
      }
      // Deep Readers: Prefer longer, high-quality books
      else if (pageCount > averagePageCount * 1.5 && hasHighRating) {
        categories.get('Deep Readers')!.push(book);
      }
      // Quality Seekers: Focus on highly-rated books regardless of length
      else if (hasHighRating && (isCompleted || progress > 0.5)) {
        categories.get('Quality Seekers')!.push(book);
      }
      // Exploratory Readers: Wide variety of books, many started but not finished
      else if (!isCompleted && (progress > 0.1 && progress < 0.8)) {
        categories.get('Exploratory Readers')!.push(book);
      }
      // Consistent Readers: Steady reading pattern, average book length
      else if (isCompleted && pageCount > averagePageCount * 0.8 && pageCount < averagePageCount * 1.2) {
        categories.get('Consistent Readers')!.push(book);
      }
      // Selective Readers: Few books but high completion rate
      else if (completionRate > 0.4 && totalBooks < 50) {
        categories.get('Selective Readers')!.push(book);
      }
      // Casual Readers: Everything else
      else {
        categories.get('Casual Readers')!.push(book);
      }
    }

    return categories;
  }

  private hasHighQualityRating(book: Book): boolean {
    const metadata = book.metadata;
    if (!metadata) return false;

    const goodreadsRating = metadata.goodreadsRating || 0;
    const amazonRating = metadata.amazonRating || 0;
    const personalRating = metadata.personalRating || 0;

    return goodreadsRating >= 4.0 || amazonRating >= 4.0 || personalRating >= 4;
  }

  private getReadingProgress(book: Book): number {
    if (book.readStatus === ReadStatus.READ) return 1.0;

    const epubProgress = book.epubProgress?.percentage || 0;
    const pdfProgress = book.pdfProgress?.percentage || 0;
    const cbxProgress = book.cbxProgress?.percentage || 0;
    const koreaderProgress = book.koreaderProgress?.percentage || 0;

    return Math.max(epubProgress, pdfProgress, cbxProgress, koreaderProgress) / 100;
  }

  private calculateAverageRating(books: Book[]): number {
    const ratingsBooks = books.filter(book => {
      const metadata = book.metadata;
      return metadata && (metadata.goodreadsRating || metadata.amazonRating || metadata.personalRating);
    });

    if (ratingsBooks.length === 0) return 0;

    const totalRating = ratingsBooks.reduce((sum, book) => {
      const metadata = book.metadata!;
      const rating = metadata.goodreadsRating || metadata.amazonRating || metadata.personalRating || 0;
      return sum + rating;
    }, 0);

    return totalRating / ratingsBooks.length;
  }

  private convertToVelocityStats(categoriesMap: Map<string, Book[]>): ReadingVelocityStats[] {
    const descriptions: Record<string, string> = {
      'Speed Readers': 'High completion rate with shorter books',
      'Consistent Readers': 'Steady reading pattern with average-length books',
      'Selective Readers': 'Few books but high completion rate',
      'Exploratory Readers': 'Wide variety, many started but not finished',
      'Deep Readers': 'Prefer longer, high-quality books',
      'Casual Readers': 'Mixed reading patterns',
      'Quality Seekers': 'Focus on highly-rated books'
    };

    return Array.from(categoriesMap.entries())
      .filter(([_, books]) => books.length > 0)
      .map(([category, books]) => {
        const completedBooks = books.filter(book => book.readStatus === ReadStatus.READ);
        const averagePages = books.reduce((sum, book) => sum + (book.metadata?.pageCount || 0), 0) / books.length;
        const averageRating = this.calculateAverageRating(books);

        return {
          category,
          count: books.length,
          averagePages: Math.round(averagePages),
          averageRating: Number(averageRating.toFixed(1)),
          description: descriptions[category] || 'Mixed reading patterns'
        };
      })
      .sort((a, b) => b.count - a.count);
  }

  private generateLegendLabels(chart: any) {
    const data = chart.data;
    if (!data.labels?.length || !data.datasets?.[0]?.data?.length) {
      return [];
    }

    const dataset = data.datasets[0];
    const dataValues = dataset.data as number[];

    return data.labels.map((label: string, index: number) => {
      const isVisible = typeof chart.getDataVisibility === 'function'
        ? chart.getDataVisibility(index)
        : !((chart.getDatasetMeta && chart.getDatasetMeta(0)?.data?.[index]?.hidden) || false);

      return {
        text: `${label} (${dataValues[index]})`,
        fillStyle: (dataset.backgroundColor as string[])[index],
        strokeStyle: '#ffffff',
        lineWidth: 1,
        hidden: !isVisible,
        index,
        fontColor: '#ffffff'
      };
    });
  }

  private formatTooltipLabel(context: any): string {
    const dataIndex = context.dataIndex;
    const velocityStats = this.getLastCalculatedStats();

    if (!velocityStats || dataIndex >= velocityStats.length) {
      return `${context.parsed} books`;
    }

    const stats = velocityStats[dataIndex];
    return `${stats.count} books | Avg. ${stats.averagePages} pages | Avg. rating: ${stats.averageRating}/5 | ${stats.description}`;
  }

  private lastCalculatedStats: ReadingVelocityStats[] = [];

  private getLastCalculatedStats(): ReadingVelocityStats[] {
    return this.lastCalculatedStats;
  }
}
