import {Component, inject, Input, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {FavoriteDaysResponse, UserStatsService} from '../../../settings/user-management/user-stats.service';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';

function hasClass(cls: string): boolean {
  return document.documentElement.classList.contains(cls);
}

type FavoriteDaysChartData = ChartData<'bar', number[], string>;

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
  selector: 'app-favorite-days-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Select, FormsModule],
  templateUrl: './favorite-days-chart.component.html',
  styleUrls: ['./favorite-days-chart.component.scss']
})
export class FavoriteDaysChartComponent implements OnInit, OnDestroy {
  @ViewChild(BaseChartDirective) private chart?: BaseChartDirective;
  public readonly chartType = 'bar' as const;
  public readonly chartData$: Observable<FavoriteDaysChartData>;
  public readonly chartOptions: ChartConfiguration['options'];
  private themeObserver: MutationObserver | null = null;

  private readonly userStatsService = inject(UserStatsService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<FavoriteDaysChartData>;

  private readonly allDays = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

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
    this.chartDataSubject = new BehaviorSubject<FavoriteDaysChartData>({
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
            text: 'Day of Week',
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
          grid: {display: false},
          border: {display: false}
        },
        y: {
          type: 'linear',
          display: true,
          position: 'left',
          title: {
            display: true,
            text: 'Number of Sessions',
            color: 'rgba(139, 92, 246, 1)',
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
        },
        y1: {
          type: 'linear',
          display: true,
          position: 'right',
          title: {
            display: true,
            text: 'Duration (hours)',
            color: 'rgba(236, 72, 153, 1)',
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
            callback: function(value) {
              return (typeof value === 'number' ? value.toFixed(1) : '0.0') + 'h';
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
    this.loadFavoriteDays();
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
        }
        if (yScale) {
          if (yScale.ticks) yScale.ticks.color = tokens.modeColor;
          if (yScale.title) yScale.title.color = tokens.modeColor;
          if (yScale.grid) yScale.grid.color = tokens.modeGridY;
        }
        if (y1Scale) {
          if (y1Scale.ticks) y1Scale.ticks.color = tokens.modeColor;
          if (y1Scale.title) y1Scale.title.color = tokens.modeColor;
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
    this.loadFavoriteDays();
  }

  private loadFavoriteDays(): void {
    const year = this.selectedYear ?? undefined;
    const month = this.selectedMonth ?? undefined;

    this.userStatsService.getFavoriteDays(year, month)
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading favorite days:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.updateChartData(data);
      });
  }

  private updateChartData(favoriteDays: FavoriteDaysResponse[]): void {
    const dayMap = new Map<number, FavoriteDaysResponse>();
    favoriteDays.forEach(item => {
      dayMap.set(item.dayOfWeek, item);
    });

    const labels = this.allDays;
    const sessionCounts = this.allDays.map((_, index) => {
      const dayData = dayMap.get(index);
      return dayData?.sessionCount || 0;
    });

    const durations = this.allDays.map((_, index) => {
      const dayData = dayMap.get(index);
      return dayData ? dayData.totalDurationSeconds / 3600 : 0; // Convert to hours
    });

    this.chartDataSubject.next({
      labels,
      datasets: [
        {
          label: 'Sessions',
          data: sessionCounts,
          backgroundColor: 'rgba(139, 92, 246, 0.8)',
          borderColor: 'rgba(139, 92, 246, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6,
          yAxisID: 'y'
        },
        {
          label: 'Duration (hours)',
          data: durations,
          backgroundColor: 'rgba(236, 72, 153, 0.8)',
          borderColor: 'rgba(236, 72, 153, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6,
          yAxisID: 'y1'
        }
      ]
    });
  }
}
