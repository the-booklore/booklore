import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {ReadingHeartbeatResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';

type HeartbeatChartData = ChartData<'bar', number[], string>;

const GENRE_COLORS: string[] = [
  'rgba(99, 102, 241, 0.85)',
  'rgba(239, 68, 68, 0.85)',
  'rgba(34, 197, 94, 0.85)',
  'rgba(251, 146, 60, 0.85)',
  'rgba(168, 85, 247, 0.85)',
  'rgba(14, 165, 233, 0.85)',
  'rgba(236, 72, 153, 0.85)',
  'rgba(234, 179, 8, 0.85)',
  'rgba(20, 184, 166, 0.85)',
  'rgba(244, 63, 94, 0.85)',
];

@Component({
  selector: 'app-reading-heartbeat-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './reading-heartbeat-chart.component.html',
  styleUrls: ['./reading-heartbeat-chart.component.scss']
})
export class ReadingHeartbeatChartComponent implements OnInit, OnDestroy {
  @Input() initialYear: number = new Date().getFullYear();

  public currentYear: number = new Date().getFullYear();
  public readonly chartType = 'bar' as const;
  public readonly chartData$: Observable<HeartbeatChartData>;
  public readonly chartOptions: ChartConfiguration['options'];

  public stats = {totalBooks: 0, avgPagesPerDay: 0, fastestRead: '', slowestRead: ''};
  public genreLegend: {genre: string; color: string}[] = [];

  private readonly userStatsService = inject(UserStatsService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<HeartbeatChartData>;
  private genreColorMap = new Map<string, string>();

  constructor() {
    this.chartDataSubject = new BehaviorSubject<HeartbeatChartData>({labels: [], datasets: []});
    this.chartData$ = this.chartDataSubject.asObservable();

    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      layout: {padding: {top: 10, bottom: 10, left: 10, right: 10}},
      plugins: {
        legend: {display: false},
        tooltip: {
          enabled: true,
          backgroundColor: 'rgba(0, 0, 0, 0.95)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: 'rgba(99, 102, 241, 0.8)',
          borderWidth: 2,
          cornerRadius: 8,
          displayColors: false,
          padding: 16,
          titleFont: {size: 14, weight: 'bold'},
          bodyFont: {size: 13},
          callbacks: {
            title: (context) => {
              return context[0]?.label || '';
            },
            label: () => ''
          }
        },
        datalabels: {display: false}
      },
      scales: {
        x: {
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 10},
            maxRotation: 45,
            minRotation: 45
          },
          grid: {display: false},
          border: {display: false}
        },
        y: {
          title: {
            display: true,
            text: 'Pages / Day',
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 13, weight: 'bold'}
          },
          beginAtZero: true,
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11}
          },
          grid: {color: 'rgba(255, 255, 255, 0.1)'},
          border: {display: false}
        }
      }
    };
  }

  ngOnInit(): void {
    this.currentYear = this.initialYear;
    this.loadData(this.currentYear);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public changeYear(delta: number): void {
    this.currentYear += delta;
    this.loadData(this.currentYear);
  }

  private loadData(year: number): void {
    this.userStatsService.getReadingHeartbeat(year)
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading reading heartbeat:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.updateStats(data);
        this.updateChartData(data);
      });
  }

  private updateStats(data: ReadingHeartbeatResponse[]): void {
    this.stats.totalBooks = data.length;
    this.stats.avgPagesPerDay = data.length > 0
      ? Math.round(data.reduce((sum, d) => sum + d.pagesPerDay, 0) / data.length * 10) / 10
      : 0;

    if (data.length > 0) {
      const sorted = [...data].sort((a, b) => a.daysToRead - b.daysToRead);
      this.stats.fastestRead = sorted[0].bookTitle;
      this.stats.slowestRead = sorted[sorted.length - 1].bookTitle;
    } else {
      this.stats.fastestRead = '-';
      this.stats.slowestRead = '-';
    }
  }

  private updateChartData(data: ReadingHeartbeatResponse[]): void {
    const allGenres = new Set<string>();
    data.forEach(d => (d.categories || []).forEach(c => allGenres.add(c)));

    this.genreColorMap.clear();
    let colorIdx = 0;
    allGenres.forEach(genre => {
      this.genreColorMap.set(genre, GENRE_COLORS[colorIdx % GENRE_COLORS.length]);
      colorIdx++;
    });

    this.genreLegend = Array.from(this.genreColorMap.entries()).map(([genre, color]) => ({genre, color}));

    const labels = data.map(d => d.bookTitle.length > 20 ? d.bookTitle.substring(0, 20) + '...' : d.bookTitle);
    const values = data.map(d => d.pagesPerDay);
    const bgColors = data.map(d => {
      const primaryGenre = d.categories?.[0];
      return primaryGenre ? (this.genreColorMap.get(primaryGenre) || 'rgba(148, 163, 184, 0.85)') : 'rgba(148, 163, 184, 0.85)';
    });

    if (this.chartOptions?.plugins?.tooltip?.callbacks) {
      this.chartOptions.plugins.tooltip.callbacks.label = (context) => {
        const idx = context.dataIndex;
        const item = data[idx];
        if (!item) return '';
        const lines = [
          `Pages/day: ${item.pagesPerDay}`,
          `Days to read: ${item.daysToRead}`,
          `Sessions: ${item.totalSessions}`,
        ];
        if (item.categories?.length) {
          lines.push(`Genre: ${item.categories[0]}`);
        }
        return lines;
      };
    }

    this.chartDataSubject.next({
      labels,
      datasets: [{
        label: 'Pages per Day',
        data: values,
        backgroundColor: bgColors,
        borderColor: bgColors.map(c => c.replace('0.85', '1')),
        borderWidth: 1,
        borderRadius: 4,
        barPercentage: 0.8,
        categoryPercentage: 0.7
      }]
    });
  }
}
