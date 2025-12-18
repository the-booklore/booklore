import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData, ChartType} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book, ReadStatus} from '../../book/model/book.model';

function hasClass(cls: string): boolean {
  return document.documentElement.classList.contains(cls);
}

type ThemeMode = 'dark' | 'light';

function themeMode(): ThemeMode {
  return hasClass('p-dark') ? 'dark' : 'light';
}

function themeTokens() {
  const mode = themeMode();
  return {
    mode,
    modeColor: mode === 'dark' ? '#ffffff' : '#000000',
    modeColorBG: mode === 'dark' ? 'rgba(0, 0, 0, 0.9)' : 'rgba(255, 255, 255, 0.9)',
  };
}

interface ReadingStatusStats {
  status: string;
  count: number;
  percentage: number;
}

const CHART_COLORS = [
  '#28a745', '#17a2b8', '#ffc107', '#6f42c1',
  '#fd7e14', '#6c757d', '#dc3545', '#343a40', '#e9ecef'
] as const;

const CHART_DEFAULTS = () => ({
  borderColor: themeTokens().modeColor,
  borderWidth: 2,
  hoverBorderWidth: 3,
  hoverBorderColor: themeTokens().modeColor,
});

type StatusChartData = ChartData<'doughnut', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class ReadStatusChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();
  private themeObserver: MutationObserver | null = null;

  public readonly statusChartType: ChartType = 'doughnut';

  public readonly statusChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          padding: 15,
          usePointStyle: true,
          generateLabels: this.generateLegendLabels.bind(this)
        }
      },
      tooltip: {
        enabled: true,
        backgroundColor: themeTokens().modeColorBG,
        titleColor: themeTokens().modeColor,
        bodyColor: themeTokens().modeColor,
        borderColor: themeTokens().modeColor,
        borderWidth: 1,
        cornerRadius: 6,
        displayColors: true,
        padding: 12,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 13},
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

  private readonly statusChartDataSubject = new BehaviorSubject<StatusChartData>({
    labels: [],
    datasets: [{
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS()
    }]
  });

  public readonly statusChartData$: Observable<StatusChartData> = this.statusChartDataSubject.asObservable();

  constructor() {
    this.initThemeObserver();
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
          console.error('Error processing reading status stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateReadingStatusStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.themeObserver) {
      this.themeObserver.disconnect();
    }
  }

  private initThemeObserver(): void {
    this.themeObserver = new MutationObserver((mutations) => {
      let shouldUpdate = false;
      for (const mutation of mutations) {
        if (mutation.type === 'attributes' && mutation.attributeName === 'class') {
          shouldUpdate = true;
          break;
        }
      }
      if (shouldUpdate) {
        this.updateChartTheme();
      }
    });

    this.themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class']
    });
  }

  private updateChartTheme(): void {
    const tokens = themeTokens();
    if (this.statusChartOptions?.plugins?.tooltip) {
      const tooltip = this.statusChartOptions.plugins.tooltip;
      tooltip.backgroundColor = tokens.modeColorBG;
      tooltip.titleColor = tokens.modeColor;
      tooltip.bodyColor = tokens.modeColor;
      tooltip.borderColor = tokens.modeColor;
    }
    const currentData = this.statusChartDataSubject.getValue();
    if (currentData.datasets && currentData.datasets.length > 0) {
      const dataset = currentData.datasets[0];
      dataset.borderColor = tokens.modeColor;
      dataset.hoverBorderColor = tokens.modeColor;
      this.statusChartDataSubject.next({
        ...currentData,
        datasets: [{ ...dataset }]
      });
    }
  }

  private updateChartData(stats: ReadingStatusStats[]): void {
    try {
      const labels = stats.map(s => s.status);
      const dataValues = stats.map(s => s.count);
      const colors = this.getColorsForData(stats.length);

      this.statusChartDataSubject.next({
        labels,
        datasets: [{
          data: dataValues,
          backgroundColor: colors,
          ...CHART_DEFAULTS()
        }]
      });
    } catch (error) {
      console.error('Error updating chart data:', error);
    }
  }

  private getColorsForData(dataLength: number): string[] {
    const colors = [...CHART_COLORS];
    while (colors.length < dataLength) {
      colors.push(...CHART_COLORS);
    }
    return colors.slice(0, dataLength);
  }

  private calculateReadingStatusStats(): ReadingStatusStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, String(selectedLibraryId));
    return this.processReadingStatusStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private processReadingStatusStats(books: Book[]): ReadingStatusStats[] {
    if (books.length === 0) {
      return [];
    }

    const statusMap = this.buildStatusMap(books);
    return this.convertMapToStats(statusMap, books.length);
  }

  private buildStatusMap(books: Book[]): Map<ReadStatus, number> {
    const statusMap = new Map<ReadStatus, number>();

    for (const book of books) {
      const rawStatus = book.readStatus;
      const status: ReadStatus = Object.values(ReadStatus).includes(rawStatus as ReadStatus)
        ? (rawStatus as ReadStatus)
        : ReadStatus.UNSET;

      statusMap.set(status, (statusMap.get(status) || 0) + 1);
    }

    return statusMap;
  }

  private convertMapToStats(statusMap: Map<ReadStatus, number>, totalBooks: number): ReadingStatusStats[] {
    return Array.from(statusMap.entries())
      .map(([status, count]) => ({
        status: this.formatReadStatus(status),
        count,
        percentage: Number(((count / totalBooks) * 100).toFixed(1))
      }))
      .sort((a, b) => b.count - a.count);
  }

  private formatReadStatus(status: ReadStatus | null | undefined): string {
    const STATUS_MAPPING: Record<string, string> = {
      [ReadStatus.UNREAD]: 'Unread',
      [ReadStatus.READING]: 'Currently Reading',
      [ReadStatus.RE_READING]: 'Re-reading',
      [ReadStatus.READ]: 'Read',
      [ReadStatus.PARTIALLY_READ]: 'Partially Read',
      [ReadStatus.PAUSED]: 'Paused',
      [ReadStatus.WONT_READ]: "Won't Read",
      [ReadStatus.ABANDONED]: 'Abandoned',
      [ReadStatus.UNSET]: 'No Status'
    };

    if (!status) return 'No Status';
    return STATUS_MAPPING[status] ?? 'No Status';
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
        strokeStyle: themeTokens().modeColor,
        lineWidth: 1,
        hidden: !isVisible,
        index,
        fontColor: themeTokens().modeColor
      };
    });
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
