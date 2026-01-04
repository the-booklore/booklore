import {Component, inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../book/service/book.service';
import {Book} from '../../../book/model/book.model';
import {BookState} from '../../../book/model/state/book-state.model';

function hasClass(cls: string): boolean {
  return document.documentElement.classList.contains(cls);
}

interface MatrixDataPoint {
  x: number; // month (0-11)
  y: number; // year index
  v: number; // book count
}

interface YearMonthData {
  year: number;
  month: number;
  count: number;
}

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

type HeatmapChartData = ChartData<'matrix', MatrixDataPoint[], string>;

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
    modeBorderColor: mode === 'dark' ? '#ef476f' : '#eb184a',
    modeGridX: mode === 'dark' ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)',
    modeGridY: mode === 'dark' ? 'rgba(255, 255, 255, 0.01)' : 'rgba(0, 0, 0, 0.01)',
    modeTicks: mode === 'dark' ? 'rgba(255, 255, 255, 0.7)' : 'rgba(0, 0, 0, 0.7)',
    modeGrid: mode === 'dark' ? 'rgba(255, 255, 255, 0.15)' : 'rgba(0, 0, 0, 0.15)',
    modeAngleLines: mode === 'dark' ? 'rgba(255, 255, 255, 0.25)' : 'rgba(0, 0, 0, 0.25)',
    modePoint: mode === 'dark' ? 'rgba(255, 255, 255, 0.8)' : 'rgba(0, 0, 0, 0.8)',
  };
}

@Component({
  selector: 'app-reading-heatmap-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './reading-heatmap-chart.component.html',
  styleUrls: ['./reading-heatmap-chart.component.scss']
})
export class ReadingHeatmapChartComponent implements OnInit, OnDestroy {
  @ViewChild(BaseChartDirective) private chart?: BaseChartDirective;
  private readonly bookService = inject(BookService);
  private readonly destroy$ = new Subject<void>();
  private themeObserver: MutationObserver | null = null;

  public readonly chartType = 'matrix' as const;

  private yearLabels: string[] = [];
  private maxBookCount = 1;

  public readonly chartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {
      padding: {
        top: 20
      }
    },
    plugins: {
      legend: {display: false},
      tooltip: {
        enabled: true,
        backgroundColor: themeTokens().modeColorBG,
        titleColor: themeTokens().modeColor,
        bodyColor: themeTokens().modeColor,
        borderColor: themeTokens().modeBorderColor,
        borderWidth: 2,
        cornerRadius: 8,
        displayColors: false,
        padding: 16,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 13},
        callbacks: {
          title: (context) => {
            const point = context[0].raw as MatrixDataPoint;
            const year = this.yearLabels[point.y];
            const month = MONTH_NAMES[point.x];
            return `${month} ${year}`;
          },
          label: (context) => {
            const point = context.raw as MatrixDataPoint;
            return `${point.v} book${point.v === 1 ? '' : 's'} read`;
          }
        }
      },
      datalabels: {
        display: true,
        color: themeTokens().modeColor,
        font: {
          family: "'Inter', sans-serif",
          size: 10,
          weight: 'bold'
        },
        formatter: (value: MatrixDataPoint) => value.v > 0 ? value.v.toString() : ''
      }
    },
    scales: {
      x: {
        type: 'linear',
        position: 'bottom',
        ticks: {
          stepSize: 1,
          callback: (value) => MONTH_NAMES[value as number] || '',
          color: themeTokens().modeColor,
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {display: false},
      },
      y: {
        type: 'linear',
        offset: true,
        ticks: {
          stepSize: 1,
          callback: (value) => this.yearLabels[value as number] || '',
          color: themeTokens().modeColor,
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {display: false},
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<HeatmapChartData>({
    labels: [],
    datasets: [{
      label: 'Books Read',
      data: []
    }]
  });

  public readonly chartData$: Observable<HeatmapChartData> = this.chartDataSubject.asObservable();

  ngOnInit(): void {
  	this.initThemeObserver();
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded),
        first(),
        catchError((error) => {
          console.error('Error processing reading heatmap data:', error);
          return EMPTY;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        const stats = this.calculateHeatmapData();
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
    const options = this.chartOptions;
    
    if (options) {
      if (options.plugins) {
        if (options.plugins.tooltip) {
          options.plugins.tooltip.backgroundColor = tokens.modeColorBG;
          options.plugins.tooltip.titleColor = tokens.modeColor;
          options.plugins.tooltip.bodyColor = tokens.modeColor;
        }
        if (options.plugins.datalabels) {
          options.plugins.datalabels.color = tokens.modeColor;
        }
      }

      if (options.scales) {
        const xScale = options.scales['x'] as any;
        const yScale = options.scales['y'] as any;
        if (xScale) {
          if (xScale.ticks) xScale.ticks.color = tokens.modeColor;
          if (xScale.grid) xScale.grid.color = tokens.modeGridX;
        }
        if (yScale) {
          if (yScale.ticks) yScale.ticks.color = tokens.modeColor;
          if (yScale.grid) yScale.grid.color = tokens.modeGridY;
        }
      }

      if (options.elements && options.elements.point) {
        options.elements.point.backgroundColor = tokens.modePoint;
      }
    }

    const currentData = this.chartDataSubject.getValue();
    if (currentData.datasets && currentData.datasets.length > 0) {
      const updatedDatasets = currentData.datasets.map((dataset: any) => ({
        ...dataset,
        pointBorderColor: tokens.modeColor
      }));

      this.chartDataSubject.next({
        ...currentData,
        datasets: updatedDatasets
      });
      this.chart?.update();
    }
  }

  private updateChartData(yearMonthData: YearMonthData[]): void {
    const currentYear = new Date().getFullYear();
    const years = Array.from({length: 10}, (_, i) => currentYear - 9 + i);

    this.yearLabels = years.map(String);
    this.maxBookCount = Math.max(1, ...yearMonthData.map(d => d.count));

    const heatmapData: MatrixDataPoint[] = [];

    years.forEach((year, yearIndex) => {
      for (let month = 0; month <= 11; month++) {
        const dataPoint = yearMonthData.find(d => d.year === year && d.month === month + 1);
        heatmapData.push({
          x: month,
          y: yearIndex,
          v: dataPoint?.count || 0
        });
      }
    });

    if (this.chartOptions?.scales?.['y']) {
      (this.chartOptions.scales['y'] as any).max = years.length - 1;
    }

    this.chartDataSubject.next({
      labels: [],
      datasets: [{
        label: 'Books Read',
        data: heatmapData,
        backgroundColor: (context) => {
          const point = context.raw as MatrixDataPoint;
          if (!point?.v) return 'rgba(255, 255, 255, 0.05)';

          const intensity = point.v / this.maxBookCount;
          const alpha = Math.max(0.2, Math.min(1.0, intensity * 0.8 + 0.2));
          return `rgba(239, 71, 111, ${alpha})`;
        },
        borderColor: themeTokens().modeColor,
        borderWidth: 1,
        width: ({chart}) => (chart.chartArea?.width || 0) / 12 - 1,
        height: ({chart}) => (chart.chartArea?.height || 0) / years.length - 1
      }]
    });
  }

  private calculateHeatmapData(): YearMonthData[] {
    const currentState = this.bookService.getCurrentBookState();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    return this.processHeatmapData(currentState.books!);
  }

  private isValidBookState(state: unknown): state is BookState {
    return (
      typeof state === 'object' &&
      state !== null &&
      'loaded' in state &&
      typeof (state as {loaded: boolean}).loaded === 'boolean' &&
      'books' in state &&
      Array.isArray((state as {books: unknown}).books) &&
      (state as {books: Book[]}).books.length > 0
    );
  }

  private processHeatmapData(books: Book[]): YearMonthData[] {
    const yearMonthMap = new Map<string, number>();
    const currentYear = new Date().getFullYear();
    const startYear = currentYear - 9;

    books
      .filter(book => book.dateFinished)
      .forEach(book => {
        const finishedDate = new Date(book.dateFinished!);
        const year = finishedDate.getFullYear();

        if (year >= startYear && year <= currentYear) {
          const month = finishedDate.getMonth() + 1;
          const key = `${year}-${month}`;
          yearMonthMap.set(key, (yearMonthMap.get(key) || 0) + 1);
        }
      });

    return Array.from(yearMonthMap.entries())
      .map(([key, count]) => {
        const [year, month] = key.split('-').map(Number);
        return {year, month, count};
      })
      .sort((a, b) => a.year - b.year || a.month - b.month);
  }
}

