import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, combineLatest, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

interface SeriesLengthStats {
  category: string;
  count: number;
  percentage: number;
  seriesNames: string[];
  averageBooksOwned: number;
}

const CHART_COLORS = [
  '#3498db', '#2ecc71', '#e74c3c', '#f39c12', '#9b59b6',
  '#1abc9c', '#34495e', '#e67e22', '#95a5a6', '#27ae60'
] as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 2,
  hoverBorderWidth: 3,
  hoverBorderColor: '#ffffff'
} as const;

type SeriesLengthChartData = ChartData<'doughnut', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class SeriesLengthDistributionChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly seriesLengthChartType = 'doughnut' as const;

  public readonly seriesLengthChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          color: '#ffffff',
          font: { size: 11 },
          padding: 12,
          usePointStyle: true,
          generateLabels: this.generateLegendLabels.bind(this)
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
      mode: 'point'
    },
    cutout: '50%'
  };

  private readonly seriesLengthChartDataSubject = new BehaviorSubject<SeriesLengthChartData>({
    labels: [],
    datasets: [{
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly seriesLengthChartData$: Observable<SeriesLengthChartData> = this.seriesLengthChartDataSubject.asObservable();

  constructor() {
    this.initializeChartDataSubscription();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeChartDataSubscription(): void {
    this.getSeriesLengthStats()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error processing series length stats:', error);
          return [];
        })
      )
      .subscribe((stats) => this.updateChartData(stats));
  }

  private updateChartData(stats: SeriesLengthStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      const labels = stats.map(s => s.category);
      const dataValues = stats.map(s => s.count);
      const colors = this.getColorsForData(stats.length);

      this.seriesLengthChartDataSubject.next({
        labels,
        datasets: [{
          data: dataValues,
          backgroundColor: colors,
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating series length chart data:', error);
    }
  }

  private getColorsForData(dataLength: number): string[] {
    const colors = [...CHART_COLORS];
    while (colors.length < dataLength) {
      colors.push(...CHART_COLORS);
    }
    return colors.slice(0, dataLength);
  }

  public getSeriesLengthStats(): Observable<SeriesLengthStats[]> {
    return combineLatest([
      this.bookService.bookState$,
      this.libraryFilterService.selectedLibrary$
    ]).pipe(
      map(([state, selectedLibraryId]) => {
        if (!this.isValidBookState(state)) {
          return [];
        }

        const filteredBooks = this.filterBooksByLibrary(state.books!, String(selectedLibraryId));
        return this.processSeriesLengthStats(filteredBooks);
      }),
      catchError((error) => {
        console.error('Error getting series length stats:', error);
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

  private processSeriesLengthStats(books: Book[]): SeriesLengthStats[] {
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

    // Categorize series by length
    const categories = new Map<string, { count: number, seriesNames: string[], totalBooksOwned: number }>();

    // Initialize categories
    const categoryRanges = [
      { name: 'Duologies (2 books)', min: 2, max: 2 },
      { name: 'Trilogies (3 books)', min: 3, max: 3 },
      { name: 'Short Series (4-5 books)', min: 4, max: 5 },
      { name: 'Medium Series (6-10 books)', min: 6, max: 10 },
      { name: 'Long Series (11-20 books)', min: 11, max: 20 },
      { name: 'Epic Series (21+ books)', min: 21, max: Infinity },
      { name: 'Unknown Length', min: 0, max: 0 }
    ];

    categoryRanges.forEach(range => {
      categories.set(range.name, { count: 0, seriesNames: [], totalBooksOwned: 0 });
    });

    // Categorize each series
    for (const [seriesName, seriesBooks] of seriesMap) {
      if (seriesBooks.length < 2) continue; // Skip single books

      // Determine series total length
      const seriesTotals = seriesBooks
        .map(book => book.metadata?.seriesTotal)
        .filter((total): total is number => typeof total === 'number' && total > 0);

      const seriesLength = seriesTotals.length > 0
        ? Math.max(...seriesTotals)
        : 0; // Unknown length

      let categoryName = 'Unknown Length';

      if (seriesLength > 0) {
        for (const range of categoryRanges) {
          if (seriesLength >= range.min && seriesLength <= range.max) {
            categoryName = range.name;
            break;
          }
        }
      }

      const categoryData = categories.get(categoryName)!;
      categoryData.count++;
      categoryData.seriesNames.push(seriesName);
      categoryData.totalBooksOwned += seriesBooks.length;
    }

    // Convert to stats array
    const totalSeries = Array.from(categories.values()).reduce((sum, cat) => sum + cat.count, 0);

    return Array.from(categories.entries())
      .filter(([_, data]) => data.count > 0)
      .map(([category, data]) => ({
        category,
        count: data.count,
        percentage: Number(((data.count / totalSeries) * 100).toFixed(1)),
        seriesNames: data.seriesNames,
        averageBooksOwned: data.count > 0 ? Math.round(data.totalBooksOwned / data.count) : 0
      }))
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
    const stats = this.getLastCalculatedStats();

    if (!stats || dataIndex >= stats.length) {
      return `${context.parsed} series`;
    }

    const categoryStats = stats[dataIndex];
    const total = context.chart.data.datasets[0].data.reduce((a: number, b: number) => a + b, 0);
    const percentage = ((categoryStats.count / total) * 100).toFixed(1);

    return `${categoryStats.count} series (${percentage}%) | Avg ${categoryStats.averageBooksOwned} books owned | Examples: ${categoryStats.seriesNames.slice(0, 3).join(', ')}${categoryStats.seriesNames.length > 3 ? '...' : ''}`;
  }

  private lastCalculatedStats: SeriesLengthStats[] = [];

  private getLastCalculatedStats(): SeriesLengthStats[] {
    return this.lastCalculatedStats;
  }
}
