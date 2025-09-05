import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData, ChartType} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

interface BookTypeStats {
  bookType: string;
  count: number;
  percentage: number;
}

const CHART_COLORS = ['#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFA726', '#AB47BC'] as const;
const CHART_DEFAULTS = {
  borderWidth: 2,
  hoverBorderWidth: 3,
  borderColor: '#ffffff',
  hoverBorderColor: '#ffffff'
} as const;

type BookTypeChartData = ChartData<'pie', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class BookTypeChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly bookTypeChartType: ChartType = 'pie';

  public readonly bookTypeChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          color: '#ffffff',
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
          label: this.formatTooltipLabel
        }
      }
    },
    interaction: {
      intersect: false,
      mode: 'point'
    }
  };

  private readonly bookTypeChartDataSubject = new BehaviorSubject<BookTypeChartData>({
    labels: [],
    datasets: [{
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly bookTypeChartData$: Observable<BookTypeChartData> =
    this.bookTypeChartDataSubject.asObservable();

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
          console.error('Error processing book type stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateBookTypeStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: BookTypeStats[]): void {
    try {
      const labels = stats.map(s => s.bookType);
      const dataValues = stats.map(s => s.count);
      const colors = this.getColorsForData(stats.length);

      this.bookTypeChartDataSubject.next({
        labels,
        datasets: [{
          data: dataValues,
          backgroundColor: colors,
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating chart data:', error);
    }
  }

  private getColorsForData(dataLength: number): string[] {
    // Repeat colors if we have more data points than colors
    const colors = [...CHART_COLORS];
    while (colors.length < dataLength) {
      colors.push(...CHART_COLORS);
    }
    return colors.slice(0, dataLength);
  }

  private calculateBookTypeStats(): BookTypeStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, String(selectedLibraryId));
    return this.processBookTypeStats(filteredBooks);
  }

  public updateFromStats(stats: BookTypeStats[]): void {
    this.updateChartData(stats);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private processBookTypeStats(books: Book[]): BookTypeStats[] {
    if (books.length === 0) {
      return [];
    }

    const typeMap = this.buildTypeMap(books);
    return this.convertMapToStats(typeMap, books.length);
  }

  private buildTypeMap(books: Book[]): Map<string, number> {
    const typeMap = new Map<string, number>();

    for (const book of books) {
      const type = book.bookType || 'Unknown';
      typeMap.set(type, (typeMap.get(type) || 0) + 1);
    }

    return typeMap;
  }

  private convertMapToStats(typeMap: Map<string, number>, totalBooks: number): BookTypeStats[] {
    return Array.from(typeMap.entries())
      .map(([bookType, count]) => ({
        bookType: this.formatBookType(bookType),
        count,
        percentage: Number(((count / totalBooks) * 100).toFixed(1))
      }))
      .sort((a, b) => b.count - a.count);
  }

  private formatBookType(type: string): string {
    const TYPE_MAPPING: Record<string, string> = {
      PDF: 'PDF',
      EPUB: 'EPUB',
      CBX: 'Comic Books',
      Unknown: 'Unknown Format'
    } as const;

    return TYPE_MAPPING[type] || type;
  }

  private generateLegendLabels(chart: any) {
    const data = chart.data;
    if (!data.labels?.length || !data.datasets?.[0]?.data?.length) {
      return [];
    }

    const dataset = data.datasets[0];
    const dataValues = dataset.data as number[];

    return data.labels.map((label: string, index: number) => ({
      text: `${label} (${dataValues[index]})`,
      fillStyle: (dataset.backgroundColor as string[])[index],
      strokeStyle: '#ffffff',
      lineWidth: 1,
      hidden: false,
      fontColor: '#ffffff',
      index
    }));
  }

  private formatTooltipLabel(context: any): string {
    const dataIndex = context.dataIndex;
    const dataset = context.dataset;
    const value = dataset.data[dataIndex] as number;
    const label = context.chart.data.labels?.[dataIndex] || 'Unknown';
    const total = (dataset.data as number[]).reduce((a: number, b: number) => a + b, 0);
    const percentage = ((value / total) * 100).toFixed(1);
    return `${label}: ${value} books (${percentage}%)`;
  }
}
