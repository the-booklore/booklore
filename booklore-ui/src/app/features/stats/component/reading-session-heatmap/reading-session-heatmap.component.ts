import {Component, inject, Input, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartConfiguration, ChartData, registerables} from 'chart.js';
import {MatrixController, MatrixElement} from 'chartjs-chart-matrix';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {ReadingSessionHeatmapResponse, UserStatsService} from '../../../settings/user-management/user-stats.service';

const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

interface MatrixDataPoint {
  x: number;
  y: number;
  v: number;
  date: string;
}

type SessionHeatmapChartData = ChartData<'matrix', MatrixDataPoint[], string>;

type ThemeMode = 'dark' | 'light';

function hasClass(cls: string): boolean {
  return document.documentElement.classList.contains(cls);
}

function themeMode(): ThemeMode {
  return hasClass('p-dark') ? 'dark' : 'light';
}

function themeTokens() {
  const mode = themeMode();
  return {
    mode,
    modeColor: mode === 'dark' ? '#ffffff' : '#000000',
    modeColorBG: mode === 'dark' ? 'rgba(0, 0, 0, 0.9)' : 'rgba(255, 255, 255, 0.9)',
    modeBorderColor: mode === 'dark' ? '#666666' : '#444444',
    modeGridX: mode === 'dark' ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)',
    modeGridY: mode === 'dark' ? 'rgba(255, 255, 255, 0.01)' : 'rgba(0, 0, 0, 0.01)',
    modeTicks: mode === 'dark' ? 'rgba(255, 255, 255, 0.7)' : 'rgba(0, 0, 0, 0.7)',
    modeGrid: mode === 'dark' ? 'rgba(255, 255, 255, 0.15)' : 'rgba(0, 0, 0, 0.15)',
    modeAngleLines: mode === 'dark' ? 'rgba(255, 255, 255, 0.25)' : 'rgba(0, 0, 0, 0.25)',
    modePoint: mode === 'dark' ? 'rgba(255, 255, 255, 0.8)' : 'rgba(0, 0, 0, 0.8)',
  };
}

@Component({
  selector: 'app-reading-session-heatmap',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './reading-session-heatmap.component.html',
  styleUrls: ['./reading-session-heatmap.component.scss']
})
export class ReadingSessionHeatmapComponent implements OnInit, OnDestroy {
  @ViewChild(BaseChartDirective) private chart?: BaseChartDirective;
  @Input() initialYear: number = new Date().getFullYear();

  public currentYear: number = new Date().getFullYear();
  public readonly chartType = 'matrix' as const;
  public readonly chartData$: Observable<SessionHeatmapChartData>;
  public readonly chartOptions: ChartConfiguration['options'];
  private themeObserver: MutationObserver | null = null;

  private readonly userStatsService = inject(UserStatsService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<SessionHeatmapChartData>;
  private maxSessionCount = 1;

  constructor() {
    this.chartDataSubject = new BehaviorSubject<SessionHeatmapChartData>({
      labels: [],
      datasets: [{label: 'Reading Sessions', data: []}]
    });
    this.chartData$ = this.chartDataSubject.asObservable();

    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      layout: {
        padding: {top: 20, bottom: 20, left: 10, right: 10}
      },
      plugins: {
        legend: {display: false},
        tooltip: {
          enabled: true,
          backgroundColor: themeTokens().modeColorBG,
          titleColor: themeTokens().modeColor,
          bodyColor: themeTokens().modeColor,
          borderColor: themeTokens().modeBorderColor,
          borderWidth: 1,
          cornerRadius: 6,
          displayColors: false,
          padding: 12,
          titleFont: {size: 14, weight: 'bold'},
          bodyFont: {size: 13},
          callbacks: {
            title: (context) => {
              const point = context[0].raw as MatrixDataPoint;
              const date = new Date(point.date);
              return date.toLocaleDateString('en-US', {
                weekday: 'short',
                year: 'numeric',
                month: 'short',
                day: 'numeric'
              });
            },
            label: (context) => {
              const point = context.raw as MatrixDataPoint;
              return `${point.v} reading session${point.v === 1 ? '' : 's'}`;
            }
          }
        },
        datalabels: {display: false}
      },
      scales: {
        x: {
          type: 'linear',
          position: 'top',
          min: 0,
          max: 52,
          ticks: {
            stepSize: 4,
            callback: (value) => {
              const weekNum = value as number;
              if (weekNum % 4 === 0) {
                const date = this.getDateFromWeek(this.currentYear, weekNum);
                return MONTH_NAMES[date.getMonth()];
              }
              return '';
            },
            color: themeTokens().modeColor,
            font: {family: "'Inter', sans-serif", size: 11}
          },
          grid: {display: false},
          border: {display: false}
        },
        y: {
          type: 'linear',
          min: 0,
          max: 6,
          ticks: {
            stepSize: 1,
            callback: (value) => {
              const dayIndex = value as number;
              return dayIndex >= 0 && dayIndex <= 6 ? DAY_NAMES[dayIndex] : '';
            },
            color: themeTokens().modeColor,
            font: {family: "'Inter', sans-serif", size: 11}
          },
          border: {display: false}
        }
      }
    };
  }

  ngOnInit(): void {
  	this.initThemeObserver();
    Chart.register(...registerables, MatrixController, MatrixElement);
    this.currentYear = this.initialYear;
    this.loadYearData(this.currentYear);
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

  public changeYear(delta: number): void {
    this.currentYear += delta;
    this.loadYearData(this.currentYear);
  }

  private loadYearData(year: number): void {
    this.userStatsService.getHeatmapForYear(year)
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading reading session heatmap:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.updateChartData(data);
      });
  }

  private updateChartData(sessionData: ReadingSessionHeatmapResponse[]): void {
    const sessionMap = new Map<string, number>();
    sessionData.forEach(item => {
      sessionMap.set(item.date, item.count);
    });

    this.maxSessionCount = Math.max(1, ...sessionData.map(d => d.count));

    const heatmapData: MatrixDataPoint[] = [];
    const startDate = new Date(this.currentYear, 0, 1);
    const endDate = new Date(this.currentYear, 11, 31);

    const firstMonday = new Date(startDate);
    const dayOfWeek = firstMonday.getDay();
    const daysToMonday = dayOfWeek === 0 ? 6 : dayOfWeek - 1;
    firstMonday.setDate(firstMonday.getDate() - daysToMonday);

    let weekIndex = 0;
    let currentDate = new Date(firstMonday);

    while (currentDate <= endDate || weekIndex === 0) {
      for (let dayOfWeek = 0; dayOfWeek < 7; dayOfWeek++) {
        const dateStr = currentDate.toISOString().split('T')[0];

        if (currentDate >= startDate && currentDate <= endDate) {
          const count = sessionMap.get(dateStr) || 0;

          heatmapData.push({
            x: weekIndex,
            y: dayOfWeek,
            v: count,
            date: dateStr
          });
        }

        currentDate.setDate(currentDate.getDate() + 1);
      }

      weekIndex++;

      if (currentDate > endDate) {
        break;
      }
    }

    this.chartDataSubject.next({
      labels: [],
      datasets: [{
        label: 'Reading Sessions',
        data: heatmapData,
        backgroundColor: (context) => {
          const point = context.raw as MatrixDataPoint;
          if (!point?.v) return 'rgba(255, 255, 255, 0.05)';

          const intensity = point.v / this.maxSessionCount;
          const alpha = Math.max(0.3, Math.min(0.9, intensity * 0.6 + 0.3));
          return `rgba(59, 130, 246, ${alpha})`;
        },
        borderColor: themeTokens().modeColor,
        borderWidth: 1
      }]
    });
  }

  private getDateFromWeek(year: number, week: number): Date {
    const date = new Date(year, 0, 1);
    date.setDate(date.getDate() + (week * 7) - date.getDay());
    return date;
  }
}
