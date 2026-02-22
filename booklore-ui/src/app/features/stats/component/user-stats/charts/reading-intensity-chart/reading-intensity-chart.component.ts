import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {EMPTY, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {Chart, ChartConfiguration, ChartData, registerables} from 'chart.js';
import {MatrixController, MatrixElement} from 'chartjs-chart-matrix';
import {UserStatsService, ReadingIntensityResponse} from '../../../../../settings/user-management/user-stats.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

Chart.register(...registerables, MatrixController, MatrixElement);

interface MatrixDataPoint {
  x: number;
  y: number;
  v: number;
  bookTitle: string;
}

@Component({
  selector: 'app-reading-intensity-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './reading-intensity-chart.component.html',
  styleUrls: ['./reading-intensity-chart.component.scss']
})
export class ReadingIntensityChartComponent implements OnInit, OnDestroy {
  @Input() initialYear = new Date().getFullYear();

  private readonly userStatsService = inject(UserStatsService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'matrix' as const;
  public currentYear: number = new Date().getFullYear();
  public hasData = false;
  public bookCount = 0;
  public longestStreak = '';

  private bookTitles: string[] = [];
  private maxDay = 1;
  private bookTotal = 1;

  public chartData: ChartData<'matrix', MatrixDataPoint[], string> = {labels: [], datasets: []};

  public chartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {duration: 400},
    layout: {padding: {top: 10, left: 10, right: 20, bottom: 10}},
    plugins: {
      legend: {display: false},
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.9)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        cornerRadius: 6,
        padding: 10,
        callbacks: {
          title: (ctx: any) => {
            const raw = ctx[0]?.raw as MatrixDataPoint;
            return raw?.bookTitle || '';
          },
          label: (ctx: any) => {
            const raw = ctx.raw as MatrixDataPoint;
            const minutes = Math.round(raw.v / 60);
            return `Day ${raw.x}: ${minutes} min`;
          }
        }
      },
      datalabels: {display: false}
    },
    scales: {
      x: {
        type: 'linear',
        offset: true,
        grid: {display: false},
        ticks: {color: 'rgba(255, 255, 255, 0.6)', font: {size: 10}, stepSize: 5},
        title: {display: true, text: 'Day since start', color: 'rgba(255, 255, 255, 0.5)', font: {size: 11}},
        min: 0
      },
      y: {
        type: 'linear',
        offset: true,
        reverse: true,
        grid: {display: false},
        ticks: {
          color: 'rgba(255, 255, 255, 0.6)',
          font: {size: 9},
          stepSize: 1,
          callback: (val: any) => {
            const idx = Number(val);
            const title = this.bookTitles[idx];
            return title ? (title.length > 25 ? title.substring(0, 23) + '..' : title) : '';
          }
        },
        min: -0.5
      }
    }
  };

  ngOnInit(): void {
    this.currentYear = this.initialYear;
    this.loadData(this.currentYear);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  changeYear(delta: number): void {
    this.currentYear += delta;
    this.loadData(this.currentYear);
  }

  private loadData(year: number): void {
    this.userStatsService.getReadingIntensity(year)
      .pipe(takeUntil(this.destroy$), catchError(() => EMPTY))
      .subscribe(data => this.processData(data));
  }

  private processData(data: ReadingIntensityResponse[]): void {
    if (!data || data.length === 0) {
      this.hasData = false;
      this.chartData = {labels: [], datasets: []};
      return;
    }

    const bookIds = [...new Set(data.map(d => d.bookId))];
    this.bookCount = bookIds.length;
    this.bookTotal = bookIds.length;

    const bookIdToIndex = new Map<number, number>();
    this.bookTitles = [];
    bookIds.forEach((id, i) => {
      bookIdToIndex.set(id, i);
      const entry = data.find(d => d.bookId === id);
      this.bookTitles.push(entry?.bookTitle || `Book ${id}`);
    });

    let maxStreak = 0;
    for (const bookId of bookIds) {
      const days = data.filter(d => d.bookId === bookId).map(d => d.dayOffset).sort((a, b) => a - b);
      let streak = 1;
      let best = 1;
      for (let i = 1; i < days.length; i++) {
        if (days[i] === days[i - 1] + 1) { streak++; best = Math.max(best, streak); }
        else streak = 1;
      }
      if (best > maxStreak) maxStreak = best;
    }
    this.longestStreak = maxStreak > 0 ? `${maxStreak}d` : '';

    this.maxDay = Math.max(...data.map(d => d.dayOffset), 1);
    const maxDuration = Math.max(...data.map(d => d.totalDurationSeconds), 1);

    const points: MatrixDataPoint[] = data.map(d => ({
      x: d.dayOffset,
      y: bookIdToIndex.get(d.bookId) ?? 0,
      v: d.totalDurationSeconds,
      bookTitle: this.bookTitles[bookIdToIndex.get(d.bookId) ?? 0]
    }));

    const cellW = Math.max(6, Math.min(16, 800 / (this.maxDay + 1)));
    const cellH = Math.max(10, Math.min(28, 400 / bookIds.length));

    this.chartData = {
      datasets: [{
        label: 'Reading Intensity',
        data: points,
        backgroundColor: (ctx: any) => {
          const raw = ctx.raw as MatrixDataPoint;
          if (!raw) return 'rgba(66, 165, 245, 0.1)';
          const ratio = raw.v / maxDuration;
          if (ratio > 0.7) return 'rgba(255, 152, 0, 0.9)';
          if (ratio > 0.4) return 'rgba(255, 193, 7, 0.75)';
          if (ratio > 0.15) return 'rgba(100, 181, 246, 0.65)';
          return 'rgba(66, 133, 244, 0.4)';
        },
        width: () => cellW,
        height: () => cellH,
        borderWidth: 1,
        borderColor: 'rgba(0, 0, 0, 0.3)',
        borderRadius: 2
      } as any]
    };
    this.hasData = true;
  }
}
