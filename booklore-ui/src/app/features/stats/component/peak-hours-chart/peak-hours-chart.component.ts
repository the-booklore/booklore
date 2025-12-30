import {Component, inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {PeakHoursResponse, UserStatsService} from '../../../settings/user-management/user-stats.service';

function hasClass(cls: string): boolean {
  return document.documentElement.classList.contains(cls);
}
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';

type PeakHoursChartData = ChartData<'line', number[], string>;

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
  selector: 'app-peak-hours-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Select, FormsModule],
  templateUrl: './peak-hours-chart.component.html',
  styleUrls: ['./peak-hours-chart.component.scss']
})
export class PeakHoursChartComponent implements OnInit, OnDestroy {
  @ViewChild(BaseChartDirective) private chart?: BaseChartDirective;
  public readonly chartType = 'line' as const;
  public readonly chartData$: Observable<PeakHoursChartData>;
  public readonly chartOptions: ChartConfiguration['options'];
  private themeObserver: MutationObserver | null = null;

  private readonly userStatsService = inject(UserStatsService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<PeakHoursChartData>;

  public selectedYear: number | null = null;
  public selectedMonth: number | null = null;
  public yearOptions: { label: string; value: number | null }[] = [];
  public monthOptions: { label: string; value: number | null }[] = [
    { label: 'All Months', value: null },
    { label: 'January', value: 1 },
    { label: 'February', value: 2 },
    { label: 'March', value: 3 },
    { label: 'April', value: 4 },
    { label: 'May', value: 5 },
    { label: 'June', value: 6 },
    { label: 'July', value: 7 },
    { label: 'August', value: 8 },
    { label: 'September', value: 9 },
    { label: 'October', value: 10 },
    { label: 'November', value: 11 },
    { label: 'December', value: 12 }
  ];

  constructor() {
    this.chartDataSubject = new BehaviorSubject<PeakHoursChartData>({
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
              if (label === 'Sessions') {
                return `${label}: ${value} session${value !== 1 ? 's' : ''}`;
              } else {
                const hours = Math.floor(value / 3600);
                const minutes = Math.floor((value % 3600) / 60);
                return `${label}: ${hours}h ${minutes}m`;
              }
            }
          }
        },
        datalabels: {display: false}
      },
      scales: {
        x: {
          title: {
            display: true,
            text: 'Hour of Day',
            color: themeTokens().modeColor,
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
          grid: {
            color: themeTokens().modeGridX,
          },
          border: {display: false}
        },
        y: {
          type: 'linear',
          display: true,
          position: 'left',
          title: {
            display: true,
            text: 'Number of Sessions',
            color: 'rgba(34, 197, 94, 0.9)',
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
            color: themeTokens().modeGridY,
          },
          border: {display: false}
        },
        y1: {
          type: 'linear',
          display: true,
          position: 'right',
          title: {
            display: true,
            text: 'Duration (minutes)',
            color: 'rgba(251, 191, 36, 0.9)',
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
            callback: function (value) {
              return (typeof value === 'number' ? Math.round(value) : '0') + 'm';
            }
          },
          grid: {
            drawOnChartArea: false
          },
          border: {display: false}
        }
      }
    };
    this.initializeYearOptions();
  }

  ngOnInit(): void {
  	this.initThemeObserver();
    this.loadPeakHours();
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
        if (options.plugins.legend?.labels) {
          options.plugins.legend.labels.color = tokens.modeColor;
        }
      }

      if (options.scales) {
        const xScale = options.scales['x'] as any;
        const yScale = options.scales['y'] as any;
        const y1Scale = options.scales['y1'] as any;
        if (xScale) {
          if (xScale.ticks) xScale.ticks.color = tokens.modeColor;
          if (xScale.title) xScale.title.color = tokens.modeColor;
          if (xScale.grid) xScale.grid.color = tokens.modeGridX;
        }
        if (yScale) {
          if (yScale.ticks) yScale.ticks.color = tokens.modeColor;
          if (yScale.title) yScale.title.color = tokens.modeColor;
          if (yScale.grid) yScale.grid.color = tokens.modeGridY;
        }
        if (y1Scale) {
          if (y1Scale.ticks) y1Scale.ticks.color = tokens.modeColor;
          if (y1Scale.title) y1Scale.title.color = tokens.modeColor;
          if (y1Scale.grid) y1Scale.grid.color = tokens.modeGridY;
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

  private initializeYearOptions(): void {
    const currentYear = new Date().getFullYear();
    this.yearOptions = [{ label: 'All Years', value: null }];
    for (let year = currentYear; year >= currentYear - 10; year--) {
      this.yearOptions.push({ label: year.toString(), value: year });
    }
  }

  public onFilterChange(): void {
    this.loadPeakHours();
  }

  private loadPeakHours(): void {
    const year = this.selectedYear ?? undefined;
    const month = this.selectedMonth ?? undefined;

    this.userStatsService.getPeakHours(year, month)
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading peak hours:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.updateChartData(data);
      });
  }

  private updateChartData(peakHours: PeakHoursResponse[]): void {
    const hourMap = new Map<number, PeakHoursResponse>();
    peakHours.forEach(item => {
      hourMap.set(item.hourOfDay, item);
    });

    const allHours = Array.from({length: 24}, (_, i) => i);
    const labels = allHours.map(h => this.formatHour(h));

    const sessionCounts = allHours.map(hour => {
      const hourData = hourMap.get(hour);
      return hourData?.sessionCount || 0;
    });

    const durations = allHours.map(hour => {
      const hourData = hourMap.get(hour);
      return hourData ? hourData.totalDurationSeconds / 60 : 0; // Convert to minutes
    });

    this.chartDataSubject.next({
      labels,
      datasets: [
        {
          label: 'Sessions',
          data: sessionCounts,
          borderColor: 'rgba(34, 197, 94, 0.9)',
          backgroundColor: 'rgba(34, 197, 94, 0.1)',
          borderWidth: 2,
          tension: 0.4,
          fill: true,
          pointRadius: 4,
          pointHoverRadius: 6,
          pointBackgroundColor: 'rgba(34, 197, 94, 0.9)',
          pointBorderColor: themeTokens().modeColor,
          pointBorderWidth: 2,
          yAxisID: 'y'
        },
        {
          label: 'Duration (minutes)',
          data: durations,
          borderColor: 'rgba(251, 191, 36, 0.9)',
          backgroundColor: 'rgba(251, 191, 36, 0.1)',
          borderWidth: 2,
          tension: 0.4,
          fill: true,
          pointRadius: 4,
          pointHoverRadius: 6,
          pointBackgroundColor: 'rgba(251, 191, 36, 0.9)',
          pointBorderColor: themeTokens().modeColor,
          pointBorderWidth: 2,
          yAxisID: 'y1'
        }
      ]
    });
  }

  private formatHour(hour: number): string {
    if (hour === 0) return '12 AM';
    if (hour === 12) return '12 PM';
    if (hour < 12) return `${hour} AM`;
    return `${hour - 12} PM`;
  }
}
