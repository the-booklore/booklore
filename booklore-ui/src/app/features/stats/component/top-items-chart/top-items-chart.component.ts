import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData, TooltipItem} from 'chart.js';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, switchMap, takeUntil} from 'rxjs/operators';
import {Select} from 'primeng/select';
import {LibraryFilterService} from '../../service/library-filter.service';
import {BookService} from '../../../book/service/book.service';
import {Book} from '../../../book/model/book.model';
import {BookState} from '../../../book/model/state/book-state.model';

interface ItemStats {
  name: string;
  count: number;
}

interface DataTypeOption {
  label: string;
  value: DataType;
  icon: string;
  color: string;
}

type DataType = 'authors' | 'categories' | 'publishers' | 'tags' | 'moods' | 'series';
type ItemChartData = ChartData<'bar', number[], string>;

const DATA_TYPE_OPTIONS: DataTypeOption[] = [
  {label: 'Series', value: 'series', icon: 'pi-bookmark', color: '#059669'},
  {label: 'Authors', value: 'authors', icon: 'pi-user', color: '#0D9488'},
  {label: 'Categories', value: 'categories', icon: 'pi-th-large', color: '#2563EB'},
  {label: 'Publishers', value: 'publishers', icon: 'pi-building', color: '#7C3AED'},
  {label: 'Tags', value: 'tags', icon: 'pi-tag', color: '#DB2777'},
  {label: 'Moods', value: 'moods', icon: 'pi-heart', color: '#EA580C'}
];

const COLOR_PALETTES: Record<DataType, string[]> = {
  authors: [
    '#0D9488', '#0891B2', '#0284C7', '#2563EB', '#4F46E5',
    '#14B8A6', '#06B6D4', '#0EA5E9', '#3B82F6', '#6366F1',
    '#5EEAD4', '#67E8F9', '#7DD3FC', '#93C5FD', '#A5B4FC',
    '#99F6E4', '#A5F3FC', '#BAE6FD', '#BFDBFE', '#C7D2FE',
    '#2DD4BF', '#22D3EE', '#38BDF8', '#60A5FA', '#818CF8'
  ],
  categories: [
    '#2563EB', '#4F46E5', '#7C3AED', '#9333EA', '#C026D3',
    '#3B82F6', '#6366F1', '#8B5CF6', '#A855F7', '#D946EF',
    '#60A5FA', '#818CF8', '#A78BFA', '#C084FC', '#E879F9',
    '#93C5FD', '#A5B4FC', '#C4B5FD', '#D8B4FE', '#F0ABFC',
    '#BFDBFE', '#C7D2FE', '#DDD6FE', '#E9D5FF', '#F5D0FE'
  ],
  publishers: [
    '#7C3AED', '#9333EA', '#C026D3', '#DB2777', '#E11D48',
    '#8B5CF6', '#A855F7', '#D946EF', '#EC4899', '#F43F5E',
    '#A78BFA', '#C084FC', '#E879F9', '#F472B6', '#FB7185',
    '#C4B5FD', '#D8B4FE', '#F0ABFC', '#F9A8D4', '#FDA4AF',
    '#DDD6FE', '#E9D5FF', '#F5D0FE', '#FBCFE8', '#FECDD3'
  ],
  tags: [
    '#DB2777', '#E11D48', '#DC2626', '#EA580C', '#D97706',
    '#EC4899', '#F43F5E', '#EF4444', '#F97316', '#F59E0B',
    '#F472B6', '#FB7185', '#F87171', '#FB923C', '#FBBF24',
    '#F9A8D4', '#FDA4AF', '#FCA5A5', '#FDBA74', '#FCD34D',
    '#FBCFE8', '#FECDD3', '#FECACA', '#FED7AA', '#FDE68A'
  ],
  moods: [
    '#EA580C', '#D97706', '#CA8A04', '#65A30D', '#16A34A',
    '#F97316', '#F59E0B', '#EAB308', '#84CC16', '#22C55E',
    '#FB923C', '#FBBF24', '#FACC15', '#A3E635', '#4ADE80',
    '#FDBA74', '#FCD34D', '#FDE047', '#BEF264', '#86EFAC',
    '#FED7AA', '#FDE68A', '#FEF08A', '#D9F99D', '#BBF7D0'
  ],
  series: [
    '#059669', '#0D9488', '#0891B2', '#0284C7', '#2563EB',
    '#10B981', '#14B8A6', '#06B6D4', '#0EA5E9', '#3B82F6',
    '#34D399', '#2DD4BF', '#22D3EE', '#38BDF8', '#60A5FA',
    '#6EE7B7', '#5EEAD4', '#67E8F9', '#7DD3FC', '#93C5FD',
    '#A7F3D0', '#99F6E4', '#A5F3FC', '#BAE6FD', '#BFDBFE'
  ]
};

@Component({
  selector: 'app-top-items-chart',
  standalone: true,
  imports: [CommonModule, FormsModule, BaseChartDirective, Select],
  templateUrl: './top-items-chart.component.html',
  styleUrls: ['./top-items-chart.component.scss']
})
export class TopItemsChartComponent implements OnInit, OnDestroy {
  @Input() initialDataType: DataType | null = null;

  public readonly chartType = 'bar' as const;
  public readonly chartData$: Observable<ItemChartData>;
  public chartOptions: ChartConfiguration<'bar'>['options'];
  public dataTypeOptions = DATA_TYPE_OPTIONS;
  public selectedDataType: DataTypeOption = DATA_TYPE_OPTIONS[0];

  public totalItems = 0;
  public totalBooks = 0;

  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<ItemChartData>;
  private lastCalculatedStats: ItemStats[] = [];
  private allBooks: Book[] = [];

  constructor() {
    this.chartDataSubject = new BehaviorSubject<ItemChartData>({
      labels: [],
      datasets: []
    });
    this.chartData$ = this.chartDataSubject.asObservable();
    this.initChartOptions();
  }

  ngOnInit(): void {
    if (this.initialDataType) {
      const initialOption = DATA_TYPE_OPTIONS.find(opt => opt.value === this.initialDataType);
      if (initialOption) {
        this.selectedDataType = initialOption;
        this.initChartOptions();
      }
    }

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
          console.error('Error processing top items stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        this.loadAndProcessData();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onDataTypeChange(): void {
    this.initChartOptions();
    this.processData();
  }

  private initChartOptions(): void {
    const currentColor = this.selectedDataType.color;
    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y',
      layout: {
        padding: {top: 10, right: 20, bottom: 10, left: 10}
      },
      scales: {
        x: {
          beginAtZero: true,
          ticks: {
            color: 'rgba(255, 255, 255, 0.8)',
            font: {
              family: "'Inter', sans-serif",
              size: 11
            },
            precision: 0,
            stepSize: 1
          },
          grid: {
            color: 'rgba(255, 255, 255, 0.08)'
          },
          border: {display: false},
          title: {
            display: true,
            text: 'Number of Books',
            color: '#ffffff',
            font: {
              family: "'Inter', sans-serif",
              size: 12,
              weight: 500
            }
          }
        },
        y: {
          ticks: {
            color: 'rgba(255, 255, 255, 0.9)',
            font: {
              family: "'Inter', sans-serif",
              size: 11
            },
            maxTicksLimit: 25
          },
          grid: {
            display: false
          },
          border: {display: false}
        }
      },
      plugins: {
        legend: {
          display: false
        },
        tooltip: {
          enabled: true,
          backgroundColor: 'rgba(0, 0, 0, 0.95)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: currentColor,
          borderWidth: 2,
          cornerRadius: 8,
          displayColors: true,
          padding: 12,
          titleFont: {size: 14, weight: 'bold'},
          bodyFont: {size: 12},
          callbacks: {
            title: (context) => {
              const dataIndex = context[0].dataIndex;
              return this.lastCalculatedStats[dataIndex]?.name || 'Unknown';
            },
            label: this.formatTooltipLabel.bind(this)
          }
        },
        datalabels: {
          display: false
        }
      },
      interaction: {
        intersect: true,
        mode: 'nearest',
        axis: 'y'
      }
    };
  }

  private loadAndProcessData(): void {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      this.allBooks = [];
      this.updateChartData([]);
      return;
    }

    this.allBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    this.processData();
  }

  private processData(): void {
    const stats = this.calculateStats(this.allBooks);
    this.updateChartData(stats);
  }

  private updateChartData(stats: ItemStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      this.totalItems = stats.length;
      this.totalBooks = stats.reduce((sum, s) => sum + s.count, 0);

      const labels = stats.map(s => this.truncateTitle(s.name, 30));
      const dataValues = stats.map(s => s.count);
      const colors = this.getColorsForData(stats.length);

      this.chartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books',
          data: dataValues,
          backgroundColor: colors,
          borderColor: colors.map(c => c),
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.85,
          categoryPercentage: 0.8,
          hoverBorderWidth: 2,
          hoverBorderColor: '#ffffff'
        }]
      });
    } catch (error) {
      console.error('Error updating items chart data:', error);
    }
  }

  private calculateStats(books: Book[]): ItemStats[] {
    if (books.length === 0) {
      return [];
    }

    const itemMap = new Map<string, number>();
    const dataType = this.selectedDataType.value;

    for (const book of books) {
      const items = this.getItemsFromBook(book, dataType);
      for (const item of items) {
        if (item && item.trim()) {
          const normalizedName = item.trim();
          itemMap.set(normalizedName, (itemMap.get(normalizedName) || 0) + 1);
        }
      }
    }

    return Array.from(itemMap.entries())
      .map(([name, count]) => ({name, count}))
      .sort((a, b) => b.count - a.count)
      .slice(0, 25);
  }

  private getItemsFromBook(book: Book, dataType: DataType): string[] {
    const metadata = book.metadata;
    if (!metadata) return [];

    switch (dataType) {
      case 'authors':
        return metadata.authors || [];
      case 'categories':
        return metadata.categories || [];
      case 'publishers':
        return metadata.publisher ? [metadata.publisher] : [];
      case 'tags':
        return metadata.tags || [];
      case 'moods':
        return metadata.moods || [];
      case 'series':
        return metadata.seriesName ? [metadata.seriesName] : [];
      default:
        return [];
    }
  }

  private isValidBookState(state: unknown): state is BookState {
    return (
      typeof state === 'object' &&
      state !== null &&
      'loaded' in state &&
      typeof (state as { loaded: boolean }).loaded === 'boolean' &&
      'books' in state &&
      Array.isArray((state as { books: unknown }).books) &&
      (state as { books: Book[] }).books.length > 0
    );
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private getColorsForData(dataLength: number): string[] {
    const palette = COLOR_PALETTES[this.selectedDataType.value];
    const colors = [...palette];
    while (colors.length < dataLength) {
      colors.push(...palette);
    }
    return colors.slice(0, dataLength);
  }

  private formatTooltipLabel(context: TooltipItem<'bar'>): string {
    const dataIndex = context.dataIndex;

    if (!this.lastCalculatedStats || dataIndex >= this.lastCalculatedStats.length) {
      return `${context.parsed.x} books`;
    }

    const item = this.lastCalculatedStats[dataIndex];
    const count = item.count;
    const bookText = count === 1 ? 'book' : 'books';

    return `${count} ${bookText}`;
  }

  private truncateTitle(title: string, maxLength: number): string {
    return title.length > maxLength ? title.substring(0, maxLength) + '...' : title;
  }
}
