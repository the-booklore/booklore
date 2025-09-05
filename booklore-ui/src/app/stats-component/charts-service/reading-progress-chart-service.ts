import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData, ChartType} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

interface ReadingProgressStats {
  progressRange: string;
  count: number;
  description: string;
}

const CHART_COLORS = [
  '#6c757d', '#ffc107', '#fd7e14', '#17a2b8', '#6f42c1', '#28a745'
] as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: '#ffffff'
} as const;

const PROGRESS_RANGES = [
  {range: '0%', min: 0, max: 0, desc: 'Not Started'},
  {range: '1-25%', min: 0.1, max: 25, desc: 'Just Started'},
  {range: '26-50%', min: 26, max: 50, desc: 'Getting Into It'},
  {range: '51-75%', min: 51, max: 75, desc: 'Halfway Through'},
  {range: '76-99%', min: 76, max: 99, desc: 'Almost Finished'},
  {range: '100%', min: 100, max: 100, desc: 'Completed'}
] as const;

type ProgressChartData = ChartData<'bar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class ReadingProgressChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly progressChartType: ChartType = 'bar';

  public readonly progressChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {display: false},
      tooltip: {
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#666666',
        borderWidth: 1,
        callbacks: {
          title: (context) => context[0].label,
          label: this.formatTooltipLabel
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
          }
        },
        grid: {color: 'rgba(255, 255, 255, 0.1)'},
        title: {
          display: true,
          text: 'Progress Range',
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
          color: '#ffffff', font: {
            family: "'Inter', sans-serif",
            size: 11
          }, stepSize: 1
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

  private readonly progressChartDataSubject = new BehaviorSubject<ProgressChartData>({
    labels: [],
    datasets: [{
      label: 'Books by Progress',
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly progressChartData$: Observable<ProgressChartData> =
    this.progressChartDataSubject.asObservable();

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
          console.error('Error processing reading progress stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateReadingProgressStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: ReadingProgressStats[]): void {
    try {
      const labels = stats.map(s => s.progressRange);
      const dataValues = stats.map(s => s.count);

      this.progressChartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books by Progress',
          data: dataValues,
          backgroundColor: [...CHART_COLORS],
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating chart data:', error);
    }
  }

  private calculateReadingProgressStats(): ReadingProgressStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, String(selectedLibraryId));
    return this.processReadingProgressStats(filteredBooks);
  }

  public updateFromStats(stats: ReadingProgressStats[]): void {
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

  private processReadingProgressStats(books: Book[]): ReadingProgressStats[] {
    if (books.length === 0) {
      return [];
    }

    const rangeCounts = this.buildProgressMap(books);
    return this.convertMapToStats(rangeCounts);
  }

  private buildProgressMap(books: Book[]): Map<string, number> {
    const rangeCounts = new Map<string, number>();
    PROGRESS_RANGES.forEach(range => rangeCounts.set(range.range, 0));

    for (const book of books) {
      const progress = this.getBookProgress(book);

      for (const range of PROGRESS_RANGES) {
        if (progress >= range.min && progress <= range.max) {
          rangeCounts.set(range.range, (rangeCounts.get(range.range) || 0) + 1);
          break;
        }
      }
    }

    return rangeCounts;
  }

  private convertMapToStats(rangeCounts: Map<string, number>): ReadingProgressStats[] {
    return PROGRESS_RANGES.map(range => ({
      progressRange: range.range,
      count: rangeCounts.get(range.range) || 0,
      description: range.desc
    }));
  }

  private getBookProgress(book: Book): number {
    if (book.pdfProgress?.percentage) return book.pdfProgress.percentage;
    if (book.epubProgress?.percentage) return book.epubProgress.percentage;
    if (book.cbxProgress?.percentage) return book.cbxProgress.percentage;
    if (book.koreaderProgress?.percentage) return book.koreaderProgress.percentage;
    return 0;
  }

  private formatTooltipLabel(context: any): string {
    const value = context.parsed.y;
    const label = context.label;
    const rangeInfo = PROGRESS_RANGES.find(r => r.range === label);
    const description = rangeInfo ? ` (${rangeInfo.desc})` : '';
    return `${value} book${value === 1 ? '' : 's'}${description}`;
  }
}
