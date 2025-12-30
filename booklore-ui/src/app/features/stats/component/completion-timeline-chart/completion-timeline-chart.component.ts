import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {CompletionTimelineResponse, UserStatsService} from '../../../settings/user-management/user-stats.service';

function hasClass(cls: string): boolean {
  return document.documentElement.classList.contains(cls);
}

type CompletionChartData = ChartData<'bar', number[], string>;

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
  selector: 'app-completion-timeline-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './completion-timeline-chart.component.html',
  styleUrls: ['./completion-timeline-chart.component.scss']
})
export class CompletionTimelineChartComponent implements OnInit, OnDestroy {
  @Input() initialYear: number = new Date().getFullYear();

  public currentYear: number = new Date().getFullYear();
  public readonly chartType = 'bar' as const;
  public readonly chartData$: Observable<CompletionChartData>;
  public readonly chartOptions: ChartConfiguration['options'];
  private themeObserver: MutationObserver | null = null;

  private readonly userStatsService = inject(UserStatsService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<CompletionChartData>;

  private readonly monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  constructor() {
    this.chartDataSubject = new BehaviorSubject<CompletionChartData>({
      labels: [],
      datasets: []
    });
    this.chartData$ = this.chartDataSubject.asObservable();

    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      layout: {
        padding: {top: 10, bottom: 10, left: 10, right: 10}
      },
      plugins: {
        legend: {
          display: true,
          position: 'top',
          labels: {
            color: themeTokens().modeColor,
            font: {family: "'Inter', sans-serif", size: 11},
            boxWidth: 12,
            padding: 10
          }
        },
        tooltip: {
          enabled: true,
          backgroundColor: themeTokens().modeColorBG,
          titleColor: themeTokens().modeColor,
          bodyColor: themeTokens().modeColor,
          borderColor: themeTokens().modeBorderColor,
          borderWidth: 1,
          cornerRadius: 6,
          displayColors: true,
          padding: 12,
          titleFont: {size: 14, weight: 'bold'},
          bodyFont: {size: 13},
          callbacks: {
            label: (context) => {
              const label = context.dataset.label || '';
              const value = context.parsed.y;
              return `${label}: ${value} book${value !== 1 ? 's' : ''}`;
            }
          }
        },
        datalabels: {display: false}
      },
      scales: {
        x: {
          title: {
            display: true,
            text: 'Month',
            color: themeTokens().modeGridX,
            font: {
              family: "'Inter', sans-serif",
              size: 13,
              weight: 'bold'
            }
          },
          ticks: {
            color: themeTokens().modeColor,
            font: {family: "'Inter', sans-serif", size: 11}
          },
          grid: {display: false},
          border: {display: false}
        },
        y: {
          title: {
            display: true,
            text: 'Number of Books',
            color: themeTokens().modeColor,
            font: {
              family: "'Inter', sans-serif",
              size: 13,
              weight: 'bold'
            }
          },
          beginAtZero: true,
          ticks: {
            color: themeTokens().modeColor,
            font: {family: "'Inter', sans-serif", size: 11},
            stepSize: 1
          },
          grid: {
            color: themeTokens().modeGridY
          },
          border: {display: false}
        }
      }
    };
  }

  ngOnInit(): void {
  	this.initThemeObserver();
    this.currentYear = this.initialYear;
    this.loadCompletionTimeline(this.currentYear);
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

      if (options.scales && options.scales['r']) {
        const rScale = options.scales['r'];
        if (rScale.ticks) {
          rScale.ticks.color = tokens.modeTicks;
        }
        if (rScale.grid) {
          rScale.grid.color = tokens.modeGrid;
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
    }
  }

  public changeYear(delta: number): void {
    this.currentYear += delta;
    this.loadCompletionTimeline(this.currentYear);
  }

  private loadCompletionTimeline(year: number): void {
    this.userStatsService.getCompletionTimelineForYear(year)
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading completion timeline:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.updateChartData(data);
      });
  }

  private updateChartData(timeline: CompletionTimelineResponse[]): void {
    const monthlyData = new Map<number, CompletionTimelineResponse>();
    timeline.forEach(item => {
      monthlyData.set(item.month, item);
    });

    const labels = this.monthNames;

    const completedBooks = this.monthNames.map((_, index) => {
      const monthData = monthlyData.get(index + 1);
      if (!monthData) return 0;
      const read = monthData.statusBreakdown['READ'] || 0;
      const partiallyRead = monthData.statusBreakdown['PARTIALLY_READ'] || 0;
      return read + partiallyRead;
    });

    const activeReading = this.monthNames.map((_, index) => {
      const monthData = monthlyData.get(index + 1);
      if (!monthData) return 0;
      const reading = monthData.statusBreakdown['READING'] || 0;
      const reReading = monthData.statusBreakdown['RE_READING'] || 0;
      return reading + reReading;
    });

    const pausedBooks = this.monthNames.map((_, index) => {
      const monthData = monthlyData.get(index + 1);
      return monthData?.statusBreakdown['PAUSED'] || 0;
    });

    const discontinuedBooks = this.monthNames.map((_, index) => {
      const monthData = monthlyData.get(index + 1);
      if (!monthData) return 0;
      const abandoned = monthData.statusBreakdown['ABANDONED'] || 0;
      const wontRead = monthData.statusBreakdown['WONT_READ'] || 0;
      return abandoned + wontRead;
    });

    this.chartDataSubject.next({
      labels,
      datasets: [
        {
          label: 'Completed',
          data: completedBooks,
          backgroundColor: 'rgba(106, 176, 76, 0.8)',
          borderColor: 'rgba(106, 176, 76, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6
        },
        {
          label: 'Active Reading',
          data: activeReading,
          backgroundColor: 'rgba(59, 130, 246, 0.8)',
          borderColor: 'rgba(59, 130, 246, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6
        },
        {
          label: 'Paused',
          data: pausedBooks,
          backgroundColor: 'rgba(255, 193, 7, 0.8)',
          borderColor: 'rgba(255, 193, 7, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6
        },
        {
          label: 'Discontinued',
          data: discontinuedBooks,
          backgroundColor: 'rgba(239, 68, 68, 0.8)',
          borderColor: 'rgba(239, 68, 68, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6
        }
      ]
    });
  }
}
