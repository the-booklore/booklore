import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {EMPTY, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-genre-evolution-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './genre-evolution-chart.component.html',
  styleUrls: ['./genre-evolution-chart.component.scss']
})
export class GenreEvolutionChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'line' as const;
  public hasData = false;
  public genreCount = 0;
  public monthCount = 0;

  private readonly GENRE_COLORS = [
    '#e91e63', '#2196f3', '#4caf50', '#ff9800', '#9c27b0',
    '#00bcd4', '#ff5722', '#3f51b5', '#8bc34a'
  ];

  public chartData: ChartData<'line', number[], string> = {labels: [], datasets: []};

  public readonly chartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {duration: 400},
    layout: {padding: {top: 10}},
    plugins: {
      legend: {
        display: true, position: 'bottom',
        labels: {color: 'rgba(255, 255, 255, 0.8)', font: {family: "'Inter', sans-serif", size: 11}, boxWidth: 12, padding: 15}
      },
      tooltip: {
        enabled: true, mode: 'index', intersect: false,
        backgroundColor: 'rgba(0, 0, 0, 0.9)', titleColor: '#ffffff', bodyColor: '#ffffff',
        borderColor: '#ffffff', borderWidth: 1, cornerRadius: 6, padding: 10
      },
      datalabels: {display: false}
    },
    scales: {
      x: {
        grid: {color: 'rgba(255, 255, 255, 0.08)'},
        ticks: {color: 'rgba(255, 255, 255, 0.6)', font: {size: 10}, maxRotation: 45}
      },
      y: {
        stacked: true, beginAtZero: true,
        grid: {color: 'rgba(255, 255, 255, 0.08)'},
        ticks: {color: 'rgba(255, 255, 255, 0.6)', font: {size: 11}, stepSize: 1}
      }
    },
    interaction: {mode: 'index', intersect: false}
  };

  ngOnInit(): void {
    this.bookService.bookState$
      .pipe(filter(state => state.loaded), first(), catchError(() => EMPTY), takeUntil(this.destroy$))
      .subscribe(() => this.processData());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private processData(): void {
    const state = this.bookService.getCurrentBookState();
    const books = state.books;
    if (!books || books.length === 0) return;

    const finishedBooks = books.filter(b => b.dateFinished);
    if (finishedBooks.length === 0) return;

    const monthGenreCounts = new Map<string, Map<string, number>>();
    const genreTotals = new Map<string, number>();

    for (const book of finishedBooks) {
      const date = new Date(book.dateFinished!);
      const monthKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
      const genres = book.metadata?.categories || [];
      if (genres.length === 0) continue;

      if (!monthGenreCounts.has(monthKey)) monthGenreCounts.set(monthKey, new Map());
      const mMap = monthGenreCounts.get(monthKey)!;

      for (const genre of genres) {
        mMap.set(genre, (mMap.get(genre) || 0) + 1);
        genreTotals.set(genre, (genreTotals.get(genre) || 0) + 1);
      }
    }

    if (monthGenreCounts.size === 0) return;

    const topGenres = [...genreTotals.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 8)
      .map(e => e[0]);
    const topGenreSet = new Set(topGenres);

    const months = [...monthGenreCounts.keys()].sort();
    const displayMonths = months.slice(-36);
    this.monthCount = displayMonths.length;

    const allGenres = [...topGenres, 'Other'];
    this.genreCount = allGenres.length;

    const monthLabels = displayMonths.map(m => {
      const [y, mo] = m.split('-');
      return `${['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][parseInt(mo) - 1]} ${y.slice(2)}`;
    });

    const datasets = allGenres.map((genre, i) => ({
      label: genre,
      data: displayMonths.map(month => {
        const mMap = monthGenreCounts.get(month);
        if (!mMap) return 0;
        if (genre === 'Other') {
          let otherTotal = 0;
          for (const [g, count] of mMap) {
            if (!topGenreSet.has(g)) otherTotal += count;
          }
          return otherTotal;
        }
        return mMap.get(genre) || 0;
      }),
      fill: true,
      backgroundColor: this.hexToRgba(this.GENRE_COLORS[i] || '#9e9e9e', 0.4),
      borderColor: this.GENRE_COLORS[i] || '#9e9e9e',
      borderWidth: 1.5,
      pointRadius: 0,
      pointHoverRadius: 4,
      tension: 0.4
    }));

    this.chartData = {labels: monthLabels, datasets};
    this.hasData = true;
  }

  private hexToRgba(hex: string, alpha: number): string {
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  }
}
