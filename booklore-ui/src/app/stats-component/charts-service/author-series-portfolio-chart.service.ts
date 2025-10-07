import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, combineLatest, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

interface AuthorSeriesStats {
  category: string;
  count: number;
  authorNames: string[];
  totalSeries: number;
  averageSeriesLength: number;
  description: string;
}

const CHART_COLORS = [
  '#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6',
  '#e67e22', '#1abc9c', '#34495e', '#95a5a6', '#27ae60',
  '#2980b9', '#c0392b', '#d35400', '#8e44ad', '#16a085'
] as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 2,
  hoverBorderWidth: 3,
  hoverBorderColor: '#ffffff'
} as const;

type AuthorSeriesChartData = ChartData<'polarArea', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class AuthorSeriesPortfolioChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly authorSeriesChartType = 'polarArea' as const;

  public readonly authorSeriesChartOptions: ChartConfiguration<'polarArea'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      r: {
        beginAtZero: true,
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
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
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
        }
      }
    },
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
          padding: 10,
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
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 12},
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

  private readonly authorSeriesChartDataSubject = new BehaviorSubject<AuthorSeriesChartData>({
    labels: [],
    datasets: [{
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly authorSeriesChartData$: Observable<AuthorSeriesChartData> = this.authorSeriesChartDataSubject.asObservable();

  constructor() {
    this.initializeChartDataSubscription();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeChartDataSubscription(): void {
    this.getAuthorSeriesStats()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error processing author series stats:', error);
          return [];
        })
      )
      .subscribe((stats) => this.updateChartData(stats));
  }

  private updateChartData(stats: AuthorSeriesStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      const labels = stats.map(s => s.category);
      const dataValues = stats.map(s => s.count);
      const colors = this.getColorsForData(stats.length);

      this.authorSeriesChartDataSubject.next({
        labels,
        datasets: [{
          data: dataValues,
          backgroundColor: colors,
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating author series chart data:', error);
    }
  }

  private getColorsForData(dataLength: number): string[] {
    const colors = [...CHART_COLORS];
    while (colors.length < dataLength) {
      colors.push(...CHART_COLORS);
    }
    return colors.slice(0, dataLength);
  }

  public getAuthorSeriesStats(): Observable<AuthorSeriesStats[]> {
    return combineLatest([
      this.bookService.bookState$,
      this.libraryFilterService.selectedLibrary$
    ]).pipe(
      map(([state, selectedLibraryId]) => {
        if (!this.isValidBookState(state)) {
          return [];
        }

        const filteredBooks = this.filterBooksByLibrary(state.books!, String(selectedLibraryId));
        return this.processAuthorSeriesStats(filteredBooks);
      }),
      catchError((error) => {
        console.error('Error getting author series stats:', error);
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

  private processAuthorSeriesStats(books: Book[]): AuthorSeriesStats[] {
    if (books.length === 0) {
      return [];
    }

    const authorSeriesMap = new Map<string, Map<string, Book[]>>();

    books.forEach(book => {
      if (!book.metadata?.authors || !book.metadata.seriesName) return;

      book.metadata.authors.forEach(author => {
        if (!authorSeriesMap.has(author)) {
          authorSeriesMap.set(author, new Map());
        }

        const authorSeries = authorSeriesMap.get(author)!;
        const seriesName = book.metadata!.seriesName!;

        if (!authorSeries.has(seriesName)) {
          authorSeries.set(seriesName, []);
        }
        authorSeries.get(seriesName)!.push(book);
      });
    });

    const authorPatterns = new Map<string, { authors: string[], totalSeries: number, totalSeriesLength: number }>();

    const categories = [
      'Single Series Masters',
      'Prolific Series Writers',
      'Multi-Series Creators',
      'Series Experimenters',
      'Franchise Builders',
      'Diverse Portfolio Authors'
    ];

    categories.forEach(cat => {
      authorPatterns.set(cat, {authors: [], totalSeries: 0, totalSeriesLength: 0});
    });

    for (const [author, authorSeries] of authorSeriesMap) {
      const seriesCount = authorSeries.size;
      const seriesLengths: number[] = [];
      let totalBooks = 0;

      for (const [_, seriesBooks] of authorSeries) {
        const seriesTotals = seriesBooks
          .map(book => book.metadata?.seriesTotal)
          .filter((total): total is number => total != null && total > 0);

        const seriesLength = seriesTotals.length > 0
          ? Math.max(...seriesTotals)
          : seriesBooks.length;

        seriesLengths.push(seriesLength);
        totalBooks += seriesBooks.length;
      }

      const averageSeriesLength = seriesLengths.reduce((sum, length) => sum + length, 0) / seriesLengths.length;
      const maxSeriesLength = Math.max(...seriesLengths);

      let category: string;

      if (seriesCount === 1 && maxSeriesLength >= 10) {
        category = 'Single Series Masters';
      } else if (seriesCount >= 5 && averageSeriesLength >= 5) {
        category = 'Prolific Series Writers';
      } else if (seriesCount >= 3 && seriesCount <= 4) {
        category = 'Multi-Series Creators';
      } else if (seriesCount >= 2 && averageSeriesLength <= 3) {
        category = 'Series Experimenters';
      } else if (seriesCount >= 2 && maxSeriesLength >= 15) {
        category = 'Franchise Builders';
      } else {
        category = 'Diverse Portfolio Authors';
      }

      const categoryData = authorPatterns.get(category)!;
      categoryData.authors.push(author);
      categoryData.totalSeries += seriesCount;
      categoryData.totalSeriesLength += Math.round(averageSeriesLength);
    }

    const descriptions: Record<string, string> = {
      'Single Series Masters': 'Authors focused on one long-running series',
      'Prolific Series Writers': 'Authors with multiple substantial series',
      'Multi-Series Creators': 'Authors balancing several series',
      'Series Experimenters': 'Authors exploring shorter series formats',
      'Franchise Builders': 'Authors creating extensive fictional universes',
      'Diverse Portfolio Authors': 'Authors with varied series approaches'
    };

    return Array.from(authorPatterns.entries())
      .filter(([_, data]) => data.authors.length > 0)
      .map(([category, data]) => ({
        category,
        count: data.authors.length,
        authorNames: data.authors,
        totalSeries: data.totalSeries,
        averageSeriesLength: data.authors.length > 0
          ? Math.round(data.totalSeriesLength / data.authors.length)
          : 0,
        description: descriptions[category] || 'Various series approaches'
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
      return `${context.parsed} authors`;
    }

    const categoryStats = stats[dataIndex];
    const exampleAuthors = categoryStats.authorNames.slice(0, 3).join(', ');
    const moreAuthors = categoryStats.authorNames.length > 3 ? `... +${categoryStats.authorNames.length - 3} more` : '';

    return `${categoryStats.count} authors | ${categoryStats.totalSeries} total series | Avg ${categoryStats.averageSeriesLength} books/series | ${categoryStats.description} | Examples: ${exampleAuthors}${moreAuthors}`;
  }

  private lastCalculatedStats: AuthorSeriesStats[] = [];

  private getLastCalculatedStats(): AuthorSeriesStats[] {
    return this.lastCalculatedStats;
  }
}
