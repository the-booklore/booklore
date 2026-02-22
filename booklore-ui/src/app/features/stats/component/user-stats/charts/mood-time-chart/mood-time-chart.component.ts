import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {EMPTY, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {UserStatsService, MoodTimeResponse} from '../../../../../settings/user-management/user-stats.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface BubblePoint {
  x: number;
  y: number;
  r: number;
}

@Component({
  selector: 'app-mood-time-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './mood-time-chart.component.html',
  styleUrls: ['./mood-time-chart.component.scss']
})
export class MoodTimeChartComponent implements OnInit, OnDestroy {
  @Input() initialYear = new Date().getFullYear();

  private readonly userStatsService = inject(UserStatsService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'bubble' as const;
  public currentYear: number = new Date().getFullYear();
  public hasData = false;
  public moodCount = 0;
  public topMood = '';
  public peakHour = '';

  private moods: string[] = [];

  private readonly MOOD_COLORS = [
    '#e91e63', '#2196f3', '#4caf50', '#ff9800', '#9c27b0',
    '#00bcd4', '#ff5722', '#3f51b5', '#8bc34a', '#ffc107',
    '#795548', '#607d8b'
  ];

  public chartData: ChartData<'bubble', BubblePoint[], string> = {labels: [], datasets: []};

  public readonly chartOptions: ChartConfiguration<'bubble'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {duration: 400},
    layout: {padding: {top: 10, right: 20}},
    plugins: {
      legend: {
        display: true, position: 'bottom',
        labels: {color: 'rgba(255, 255, 255, 0.8)', font: {family: "'Inter', sans-serif", size: 11}, boxWidth: 10, padding: 10}
      },
      tooltip: {
        enabled: true, backgroundColor: 'rgba(0, 0, 0, 0.9)',
        titleColor: '#ffffff', bodyColor: '#ffffff',
        cornerRadius: 6, padding: 10,
        callbacks: {
          label: (ctx) => {
            const ds = ctx.dataset;
            const point = ctx.raw as BubblePoint;
            const hour = point.x;
            const h = Math.floor(hour);
            const label = h === 0 ? '12am' : h === 12 ? '12pm' : h < 12 ? `${h}am` : `${h - 12}pm`;
            return `${ds.label}: ${label}`;
          }
        }
      },
      datalabels: {display: false}
    },
    scales: {
      x: {
        min: 0, max: 23,
        grid: {color: 'rgba(255, 255, 255, 0.08)'},
        ticks: {
          color: 'rgba(255, 255, 255, 0.6)', font: {size: 10}, stepSize: 3,
          callback: (val) => {
            const h = Number(val);
            if (h === 0) return '12am';
            if (h === 12) return '12pm';
            return h < 12 ? `${h}am` : `${h - 12}pm`;
          }
        },
        title: {display: true, text: 'Hour of Day', color: 'rgba(255, 255, 255, 0.5)', font: {size: 11}}
      },
      y: {
        grid: {color: 'rgba(255, 255, 255, 0.08)'},
        ticks: {
          color: 'rgba(255, 255, 255, 0.6)', font: {size: 10},
          callback: (val) => {
            const idx = Number(val);
            return this.moods[idx] || '';
          }
        },
        title: {display: true, text: 'Mood', color: 'rgba(255, 255, 255, 0.5)', font: {size: 11}}
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
    this.userStatsService.getMoodTime(year)
      .pipe(takeUntil(this.destroy$), catchError(() => EMPTY))
      .subscribe(data => this.processData(data));
  }

  private processData(data: MoodTimeResponse[]): void {
    if (!data || data.length === 0) {
      this.hasData = false;
      this.chartData = {labels: [], datasets: []};
      return;
    }

    this.moods = [...new Set(data.map(d => d.mood))];
    this.moodCount = this.moods.length;

    const moodTotals = new Map<string, number>();
    const hourTotals = new Map<number, number>();
    for (const d of data) {
      moodTotals.set(d.mood, (moodTotals.get(d.mood) || 0) + d.totalDurationSeconds);
      hourTotals.set(d.hourOfDay, (hourTotals.get(d.hourOfDay) || 0) + d.totalDurationSeconds);
    }

    const topMoodEntry = [...moodTotals.entries()].sort((a, b) => b[1] - a[1])[0];
    this.topMood = topMoodEntry ? topMoodEntry[0] : '';

    const peakEntry = [...hourTotals.entries()].sort((a, b) => b[1] - a[1])[0];
    if (peakEntry) {
      const h = peakEntry[0];
      this.peakHour = h === 0 ? '12am' : h === 12 ? '12pm' : h < 12 ? `${h}am` : `${h - 12}pm`;
    }

    const maxDuration = Math.max(...data.map(d => d.totalDurationSeconds), 1);
    const moodIndexMap = new Map(this.moods.map((m, i) => [m, i]));

    const datasets = this.moods.map((mood, i) => {
      const moodData = data.filter(d => d.mood === mood);
      return {
        label: mood,
        data: moodData.map(d => ({
          x: d.hourOfDay,
          y: moodIndexMap.get(d.mood) || 0,
          r: Math.max(3, Math.min(18, (d.totalDurationSeconds / maxDuration) * 18))
        })),
        backgroundColor: this.MOOD_COLORS[i % this.MOOD_COLORS.length] + '99',
        borderColor: this.MOOD_COLORS[i % this.MOOD_COLORS.length],
        borderWidth: 1
      };
    });

    this.chartData = {datasets};
    this.hasData = true;
  }
}
