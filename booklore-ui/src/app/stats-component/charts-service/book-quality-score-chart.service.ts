import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

interface QualityScoreStats {
  category: string;
  count: number;
  averageScore: number;
  scoreRange: string;
}

const CHART_COLORS = [
  '#e74c3c', '#e67e22', '#f39c12', '#f1c40f', '#2ecc71',
  '#27ae60', '#3498db', '#9b59b6', '#8e44ad', '#34495e'
] as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 2,
  hoverBorderWidth: 3,
  hoverBorderColor: '#ffffff'
} as const;

const QUALITY_COLORS = {
  'Excellent (9+)': '#2ecc71',     // Green
  'Very Good (8-9)': '#27ae60',    // Dark Green
  'Good (6-8)': '#3498db',         // Blue
  'Average (4-6)': '#f39c12',      // Orange
  'Poor (2-4)': '#e67e22',         // Dark Orange
  'Very Poor (0-2)': '#e74c3c'     // Red
} as const;

type QualityChartData = ChartData<'doughnut', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class BookQualityScoreChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly qualityChartType = 'doughnut' as const;

  public readonly qualityChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          padding: 12,
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
    },
    cutout: '45%'
  };

  private readonly qualityChartDataSubject = new BehaviorSubject<QualityChartData>({
    labels: [],
    datasets: [{
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly qualityChartData$: Observable<QualityChartData> = this.qualityChartDataSubject.asObservable();

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
          console.error('Error processing quality score stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateQualityScoreStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: QualityScoreStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      const labels = stats.map(s => s.category);
      const dataValues = stats.map(s => s.count);
      const colors = this.getColorsForQualityData(stats);

      this.qualityChartDataSubject.next({
        labels,
        datasets: [{
          data: dataValues,
          backgroundColor: colors,
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating quality chart data:', error);
    }
  }

  private getColorsForQualityData(stats: QualityScoreStats[]): string[] {
    return stats.map(stat => QUALITY_COLORS[stat.category as keyof typeof QUALITY_COLORS] || '#34495e');
  }

  private calculateQualityScoreStats(): QualityScoreStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, String(selectedLibraryId));
    return this.processQualityScoreStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private processQualityScoreStats(books: Book[]): QualityScoreStats[] {
    if (books.length === 0) {
      return [];
    }

    const qualityCategories = this.categorizeByQualityScore(books);
    return this.convertToQualityStats(qualityCategories);
  }

  private categorizeByQualityScore(books: Book[]): Map<string, { books: Book[], scores: number[] }> {
    const categories = new Map<string, { books: Book[], scores: number[] }>();

    // Initialize 6 categories
    categories.set('Excellent (9+)', {books: [], scores: []});
    categories.set('Very Good (8-9)', {books: [], scores: []});
    categories.set('Good (6-8)', {books: [], scores: []});
    categories.set('Average (4-6)', {books: [], scores: []});
    categories.set('Poor (2-4)', {books: [], scores: []});
    categories.set('Very Poor (0-2)', {books: [], scores: []});

    for (const book of books) {
      const qualityScore = this.calculateQualityScore(book);
      const category = this.getQualityCategory(qualityScore);

      categories.get(category)!.books.push(book);
      categories.get(category)!.scores.push(qualityScore);
    }

    return categories;
  }

  private calculateQualityScore(book: Book): number {
    // Use metadataMatchScore directly, scale from 0-100 to 0-10
    if (book.metadataMatchScore !== null && book.metadataMatchScore !== undefined) {
      return Math.min(10, Math.max(0, book.metadataMatchScore / 10));
    }
    return 0;
  }

  private getQualityCategory(score: number): string {
    if (score >= 9) return 'Excellent (9+)';
    if (score >= 8) return 'Very Good (8-9)';
    if (score >= 6) return 'Good (6-8)';
    if (score >= 4) return 'Average (4-6)';
    if (score >= 2) return 'Poor (2-4)';
    return 'Very Poor (0-2)';
  }

  private convertToQualityStats(categoriesMap: Map<string, { books: Book[], scores: number[] }>): QualityScoreStats[] {
    const scoreRanges: Record<string, string> = {
      'Excellent (9+)': '90-100%',
      'Very Good (8-9)': '80-89%',
      'Good (6-8)': '60-79%',
      'Average (4-6)': '40-59%',
      'Poor (2-4)': '20-39%',
      'Very Poor (0-2)': '0-19%'
    };

    return Array.from(categoriesMap.entries())
      .filter(([_, data]) => data.books.length > 0)
      .map(([category, data]) => {
        const averageScore = data.scores.reduce((sum, score) => sum + score, 0) / data.scores.length;

        return {
          category,
          count: data.books.length,
          averageScore: Number(averageScore.toFixed(1)),
          scoreRange: scoreRanges[category] || 'Unknown'
        };
      })
      .sort((a, b) => b.averageScore - a.averageScore);
  }

  private generateLegendLabels(chart: any) {
    const data = chart.data;
    if (!data.labels?.length || !data.datasets?.[0]?.data?.length) {
      return [];
    }

    const dataset = data.datasets[0];

    return data.labels.map((label: string, index: number) => {
      const isVisible = typeof chart.getDataVisibility === 'function'
        ? chart.getDataVisibility(index)
        : !((chart.getDatasetMeta && chart.getDatasetMeta(0)?.data?.[index]?.hidden) || false);

      return {
        text: label,
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
    const qualityStats = this.getLastCalculatedStats();

    if (!qualityStats || dataIndex >= qualityStats.length) {
      return `${context.parsed} books`;
    }

    const stats = qualityStats[dataIndex];
    const total = context.chart.data.datasets[0].data.reduce((a: number, b: number) => a + b, 0);
    const percentage = ((stats.count / total) * 100).toFixed(1);

    return `${stats.count} books (${percentage}%) | Average Score: ${stats.averageScore}/10 (${stats.scoreRange})`;
  }

  private lastCalculatedStats: QualityScoreStats[] = [];

  private getLastCalculatedStats(): QualityScoreStats[] {
    return this.lastCalculatedStats;
  }
}
