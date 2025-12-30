import {Component, inject, Input, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {GenreStatsResponse, UserStatsService} from '../../../settings/user-management/user-stats.service';

function hasClass(cls: string): boolean {
  return document.documentElement.classList.contains(cls);
}

type GenreChartData = ChartData<'bar', number[], string>;

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
  selector: 'app-genre-stats-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './genre-stats-chart.component.html',
  styleUrls: ['./genre-stats-chart.component.scss']
})
export class GenreStatsChartComponent implements OnInit, OnDestroy {
  @ViewChild(BaseChartDirective) private chart?: BaseChartDirective;
  @Input() maxGenres: number = 35;

  public readonly chartType = 'bar' as const;
  public readonly chartData$: Observable<GenreChartData>;
  public readonly chartOptions: ChartConfiguration['options'];
  private themeObserver: MutationObserver | null = null;

  private readonly userStatsService = inject(UserStatsService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<GenreChartData>;

  constructor() {
    this.chartDataSubject = new BehaviorSubject<GenreChartData>({
      labels: [],
      datasets: []
    });
    this.chartData$ = this.chartDataSubject.asObservable();

    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      layout: {
        padding: {top: 10}
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
          displayColors: true,
          padding: 12,
          titleFont: {size: 14, weight: 'bold'},
          bodyFont: {size: 13},
          callbacks: {
            label: (context) => {
              const dataIndex = context.dataIndex;
              const dataset = context.dataset;
              const label = context.chart.data.labels?.[dataIndex] as string;
              const minutes = Math.floor((dataset.data[dataIndex] as number) / 60);
              const hours = Math.floor(minutes / 60);
              const mins = minutes % 60;

              const timeStr = hours > 0
                ? `${hours}h ${mins}m`
                : `${mins}m`;

              return `${label}: ${timeStr}`;
            }
          }
        },
        datalabels: {display: false}
      },
      scales: {
        x: {
          title: {
            display: true,
            text: 'Genres',
            color: themeTokens().modeColor,
            font: {
              family: "'Inter', sans-serif",
              size: 12
            }
          },
          ticks: {
            color: themeTokens().modeColor,
            font: {family: "'Inter', sans-serif", size: 11},
            maxRotation: 90,
            minRotation: 90,
            callback: (value, index) => {
              const label = this.chartDataSubject.value.labels?.[index] as string;
              const maxLength = 12;
              if (label && label.length > maxLength) {
                return label.substring(0, maxLength) + '...';
              }
              return label;
            }
          },
          grid: {display: false},
          border: {display: false}
        },
        y: {
          title: {
            display: true,
            text: 'Time Read',
            color: themeTokens().modeColor,
            font: {
              family: "'Inter', sans-serif",
              size: 12
            }
          },
          beginAtZero: true,
          ticks: {
            color: themeTokens().modeColor,
            font: {family: "'Inter', sans-serif", size: 11},
            callback: (value) => {
              const seconds = value as number;
              const minutes = Math.floor(seconds / 60);
              const hours = Math.floor(minutes / 60);
              const days = Math.floor(hours / 24);

              if (days > 0) {
                const remainingHours = hours % 24;
                if (remainingHours > 0) {
                  return `${days} ${days === 1 ? 'day' : 'days'} ${remainingHours} ${remainingHours === 1 ? 'hr' : 'hrs'}`;
                }
                return `${days} ${days === 1 ? 'day' : 'days'}`;
              } else if (hours > 0) {
                const remainingMinutes = minutes % 60;
                if (remainingMinutes > 0) {
                  return `${hours} ${hours === 1 ? 'hr' : 'hrs'} ${remainingMinutes} min`;
                }
                return `${hours} ${hours === 1 ? 'hr' : 'hrs'}`;
              } else if (minutes > 0) {
                return `${minutes} min`;
              }
              return `${seconds} sec`;
            },
            stepSize: undefined,
            maxTicksLimit: 8
          },
          grid: {
            color: themeTokens().modeGridY,
          },
          border: {display: false}
        }
      }
    };
  }

  ngOnInit(): void {
  	this.initThemeObserver();
    this.loadGenreStats();
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
          if (xScale.title) xScale.title.color = tokens.modeColor;
        }
        if (yScale) {
          if (yScale.ticks) yScale.ticks.color = tokens.modeColor;
          if (yScale.title) yScale.title.color = tokens.modeColor;
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

  private loadGenreStats(): void {
    this.userStatsService.getGenreStats()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading genre stats:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.updateChartData(data);
      });
  }

  private updateChartData(genreStats: GenreStatsResponse[]): void {
    const sortedStats = [...genreStats]
      .sort((a, b) => b.totalDurationSeconds - a.totalDurationSeconds)
      .slice(0, this.maxGenres);

    const labels = sortedStats.map(stat => stat.genre);
    const durations = sortedStats.map(stat => stat.totalDurationSeconds);

    this.chartDataSubject.next({
      labels,
      datasets: [
        {
          label: 'Reading Time',
          data: durations,
          backgroundColor: 'rgba(34, 197, 94, 0.8)',
          borderColor: 'rgba(34, 197, 94, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6
        }
      ]
    });
  }
}
