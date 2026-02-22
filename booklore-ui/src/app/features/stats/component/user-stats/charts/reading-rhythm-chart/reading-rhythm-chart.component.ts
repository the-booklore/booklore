import {Component, ElementRef, inject, Input, OnDestroy, OnInit, ViewChild, AfterViewInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Tooltip} from 'primeng/tooltip';
import {Subject, EMPTY} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {UserStatsService, ReadingRhythmResponse} from '../../../../../settings/user-management/user-stats.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-reading-rhythm-chart',
  standalone: true,
  imports: [CommonModule, Tooltip, TranslocoDirective],
  templateUrl: './reading-rhythm-chart.component.html',
  styleUrls: ['./reading-rhythm-chart.component.scss']
})
export class ReadingRhythmChartComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild('rhythmCanvas', {static: false}) canvasRef!: ElementRef<HTMLCanvasElement>;
  @Input() initialYear = new Date().getFullYear();

  private readonly userStatsService = inject(UserStatsService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  public currentYear: number = new Date().getFullYear();
  public hasData = false;
  public totalSessions = 0;
  public busiestMonth = '';
  public topGenre = '';

  private data: ReadingRhythmResponse[] = [];
  private canvasReady = false;
  private dataReady = false;

  private readonly MONTH_NAMES = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
  private readonly GENRE_COLORS: Record<string, string> = {};
  private readonly DEFAULT_COLORS = ['#e91e63','#2196f3','#4caf50','#ff9800','#9c27b0','#00bcd4','#ff5722','#3f51b5','#8bc34a','#ffc107','#795548','#607d8b'];

  ngOnInit(): void {
    this.currentYear = this.initialYear;
    this.loadData(this.currentYear);
  }

  ngAfterViewInit(): void {
    this.canvasReady = true;
    this.tryRender();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  changeYear(delta: number): void {
    this.currentYear += delta;
    this.loadData(this.currentYear);
  }

  private tryRender(): void {
    if (this.canvasReady && this.dataReady && this.hasData) {
      this.draw();
    }
  }

  private loadData(year: number): void {
    this.userStatsService.getReadingRhythm(year)
      .pipe(takeUntil(this.destroy$), catchError(() => EMPTY))
      .subscribe(data => {
        this.data = data;
        this.dataReady = true;
        this.processStats(data);
        this.tryRender();
      });
  }

  private processStats(data: ReadingRhythmResponse[]): void {
    if (!data || data.length === 0) {
      this.hasData = false;
      return;
    }

    this.hasData = true;
    this.totalSessions = data.reduce((sum, d) => sum + d.sessionCount, 0);

    const monthTotals = new Map<number, number>();
    const genreTotals = new Map<string, number>();

    for (const d of data) {
      monthTotals.set(d.month, (monthTotals.get(d.month) || 0) + d.sessionCount);
      if (d.topGenre) {
        genreTotals.set(d.topGenre, (genreTotals.get(d.topGenre) || 0) + d.sessionCount);
      }
    }

    const busiestEntry = [...monthTotals.entries()].sort((a, b) => b[1] - a[1])[0];
    this.busiestMonth = busiestEntry ? this.MONTH_NAMES[busiestEntry[0] - 1] : '';

    const topGenreEntry = [...genreTotals.entries()].sort((a, b) => b[1] - a[1])[0];
    this.topGenre = topGenreEntry ? topGenreEntry[0] : '';

    const genres = [...genreTotals.keys()];
    genres.forEach((g, i) => {
      if (!this.GENRE_COLORS[g]) {
        this.GENRE_COLORS[g] = this.DEFAULT_COLORS[i % this.DEFAULT_COLORS.length];
      }
    });
  }

  private draw(): void {
    const canvas = this.canvasRef.nativeElement;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const rect = canvas.parentElement!.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const width = rect.width;
    const height = 500;
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';
    ctx.scale(dpr, dpr);

    const centerX = width / 2;
    const centerY = height / 2;
    const maxRadius = Math.min(width, height) * 0.42;
    const minRadius = 30;

    const maxDuration = Math.max(...this.data.map(d => d.totalDurationSeconds), 1);

    // Draw spiral path
    ctx.beginPath();
    for (let t = 0; t <= 12; t += 0.05) {
      const angle = (t / 12) * Math.PI * 2 - Math.PI / 2;
      const r = minRadius + (t / 12) * (maxRadius - minRadius);
      const x = centerX + r * Math.cos(angle);
      const y = centerY + r * Math.sin(angle);
      if (t === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
    }
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.lineWidth = 1;
    ctx.stroke();

    // Draw month labels
    ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
    ctx.font = '10px Inter, sans-serif';
    ctx.textAlign = 'center';
    for (let m = 0; m < 12; m++) {
      const angle = (m / 12) * Math.PI * 2 - Math.PI / 2;
      const r = minRadius + ((m + 0.5) / 12) * (maxRadius - minRadius);
      const labelR = r + 20;
      const x = centerX + labelR * Math.cos(angle);
      const y = centerY + labelR * Math.sin(angle);
      ctx.fillText(this.MONTH_NAMES[m], x, y);
    }

    // Draw data points
    for (const d of this.data) {
      const monthFrac = (d.month - 1 + d.hourOfDay / 24) / 12;
      const angle = monthFrac * Math.PI * 2 - Math.PI / 2;
      const r = minRadius + monthFrac * (maxRadius - minRadius);
      const x = centerX + r * Math.cos(angle);
      const y = centerY + r * Math.sin(angle);

      const dotSize = Math.max(3, Math.min(12, (d.totalDurationSeconds / maxDuration) * 12));
      const color = d.topGenre ? (this.GENRE_COLORS[d.topGenre] || '#9e9e9e') : '#9e9e9e';

      ctx.beginPath();
      ctx.arc(x, y, dotSize, 0, Math.PI * 2);
      ctx.fillStyle = color;
      ctx.globalAlpha = 0.8;
      ctx.fill();
      ctx.globalAlpha = 1;
    }

    // Center label
    ctx.fillStyle = 'rgba(255, 255, 255, 0.3)';
    ctx.font = 'bold 14px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(String(this.currentYear), centerX, centerY);
  }
}
