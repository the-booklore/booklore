import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {Book} from '../../../../../book/model/book.model';

interface MatrixDataPoint {
  x: number;
  y: number;
  v: number;
}

type MatrixChartData = ChartData<'matrix', MatrixDataPoint[], string>;

@Component({
  selector: 'app-genre-flow-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './genre-flow-chart.component.html',
  styleUrls: ['./genre-flow-chart.component.scss']
})
export class GenreFlowChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'matrix' as const;
  public readonly chartData$: Observable<MatrixChartData>;
  public readonly chartOptions: ChartConfiguration['options'];

  public stats = {commonTransition: '', longestStreak: '', genreLoyalty: 0};
  private genreLabels: string[] = [];
  private maxCount = 1;

  private readonly chartDataSubject: BehaviorSubject<MatrixChartData>;

  constructor() {
    this.chartDataSubject = new BehaviorSubject<MatrixChartData>({labels: [], datasets: [{label: 'Transitions', data: []}]});
    this.chartData$ = this.chartDataSubject.asObservable();

    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      layout: {padding: {top: 20}},
      plugins: {
        legend: {display: false},
        tooltip: {
          enabled: true,
          backgroundColor: 'rgba(0, 0, 0, 0.95)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: 'rgba(168, 85, 247, 0.8)',
          borderWidth: 2,
          cornerRadius: 8,
          displayColors: false,
          padding: 16,
          titleFont: {size: 14, weight: 'bold'},
          bodyFont: {size: 13},
          callbacks: {
            title: (context) => {
              const point = context[0].raw as MatrixDataPoint;
              const from = this.genreLabels[point.y] || '';
              const to = this.genreLabels[point.x] || '';
              return `${from} → ${to}`;
            },
            label: (context) => {
              const point = context.raw as MatrixDataPoint;
              return `${point.v} transition${point.v === 1 ? '' : 's'}`;
            }
          }
        },
        datalabels: {
          display: true,
          color: '#ffffff',
          font: {family: "'Inter', sans-serif", size: 10, weight: 'bold'},
          formatter: (value: MatrixDataPoint) => value.v > 0 ? value.v.toString() : ''
        }
      },
      scales: {
        x: {
          type: 'linear',
          position: 'bottom',
          title: {display: true, text: 'To Genre', color: '#ffffff', font: {family: "'Inter', sans-serif", size: 12, weight: 'bold'}},
          ticks: {
            stepSize: 1,
            callback: (value) => this.genreLabels[value as number] || '',
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 10},
            maxRotation: 45,
            minRotation: 45
          },
          grid: {display: false}
        },
        y: {
          type: 'linear',
          offset: true,
          title: {display: true, text: 'From Genre', color: '#ffffff', font: {family: "'Inter', sans-serif", size: 12, weight: 'bold'}},
          ticks: {
            stepSize: 1,
            callback: (value) => this.genreLabels[value as number] || '',
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 10}
          },
          grid: {display: false}
        }
      }
    };
  }

  ngOnInit(): void {
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded),
        first(),
        catchError((error) => {
          console.error('Error processing genre flow data:', error);
          return EMPTY;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        this.calculateAndUpdate();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private calculateAndUpdate(): void {
    const currentState = this.bookService.getCurrentBookState();
    if (!currentState?.loaded || !currentState.books?.length) return;

    const readBooks = currentState.books
      .filter(b => b.dateFinished && b.metadata?.categories?.length)
      .sort((a, b) => new Date(a.dateFinished!).getTime() - new Date(b.dateFinished!).getTime());

    if (readBooks.length < 2) return;

    const transitionMap = new Map<string, number>();
    const genreSet = new Set<string>();
    let sameGenreCount = 0;

    for (let i = 1; i < readBooks.length; i++) {
      const fromGenre = this.getPrimaryGenre(readBooks[i - 1]);
      const toGenre = this.getPrimaryGenre(readBooks[i]);
      if (!fromGenre || !toGenre) continue;

      genreSet.add(fromGenre);
      genreSet.add(toGenre);

      const key = `${fromGenre}|||${toGenre}`;
      transitionMap.set(key, (transitionMap.get(key) || 0) + 1);

      if (fromGenre === toGenre) sameGenreCount++;
    }

    const allGenres = Array.from(genreSet);
    const topGenres = this.getTopGenres(allGenres, transitionMap, 8);
    this.genreLabels = topGenres;

    this.calculateStats(readBooks, transitionMap, sameGenreCount, readBooks.length - 1);
    this.buildChartData(topGenres, transitionMap);
  }

  private getPrimaryGenre(book: Book): string | null {
    return book.metadata?.categories?.[0] || null;
  }

  private getTopGenres(genres: string[], transitionMap: Map<string, number>, limit: number): string[] {
    const genreCount = new Map<string, number>();
    transitionMap.forEach((count, key) => {
      const [from, to] = key.split('|||');
      genreCount.set(from, (genreCount.get(from) || 0) + count);
      genreCount.set(to, (genreCount.get(to) || 0) + count);
    });

    return genres
      .sort((a, b) => (genreCount.get(b) || 0) - (genreCount.get(a) || 0))
      .slice(0, limit);
  }

  private calculateStats(readBooks: Book[], transitionMap: Map<string, number>, sameGenreCount: number, totalTransitions: number): void {
    let maxTransition = '';
    let maxCount = 0;
    transitionMap.forEach((count, key) => {
      if (count > maxCount) {
        maxCount = count;
        const [from, to] = key.split('|||');
        maxTransition = `${from} → ${to}`;
      }
    });
    this.stats.commonTransition = maxTransition || '-';

    let longestStreak = 1;
    let currentStreak = 1;
    let streakGenre = '';
    let longestStreakGenre = '';
    for (let i = 1; i < readBooks.length; i++) {
      const prev = this.getPrimaryGenre(readBooks[i - 1]);
      const curr = this.getPrimaryGenre(readBooks[i]);
      if (prev && curr && prev === curr) {
        currentStreak++;
        if (currentStreak > longestStreak) {
          longestStreak = currentStreak;
          longestStreakGenre = curr;
        }
      } else {
        currentStreak = 1;
      }
    }
    this.stats.longestStreak = longestStreak > 1 ? `${longestStreakGenre} (${longestStreak})` : '-';
    this.stats.genreLoyalty = totalTransitions > 0
      ? Math.round((sameGenreCount / totalTransitions) * 100)
      : 0;
  }

  private buildChartData(genres: string[], transitionMap: Map<string, number>): void {
    const matrixData: MatrixDataPoint[] = [];

    genres.forEach((fromGenre, fromIdx) => {
      genres.forEach((toGenre, toIdx) => {
        const key = `${fromGenre}|||${toGenre}`;
        const count = transitionMap.get(key) || 0;
        matrixData.push({x: toIdx, y: fromIdx, v: count});
      });
    });

    this.maxCount = Math.max(1, ...matrixData.map(d => d.v));

    if (this.chartOptions?.scales?.['x']) {
      (this.chartOptions.scales['x'] as any).max = genres.length - 1;
    }
    if (this.chartOptions?.scales?.['y']) {
      (this.chartOptions.scales['y'] as any).max = genres.length - 1;
    }

    this.chartDataSubject.next({
      labels: [],
      datasets: [{
        label: 'Transitions',
        data: matrixData,
        backgroundColor: (context) => {
          const point = context.raw as MatrixDataPoint;
          if (!point?.v) return 'rgba(255, 255, 255, 0.05)';
          const intensity = point.v / this.maxCount;
          const alpha = Math.max(0.2, Math.min(1.0, intensity * 0.8 + 0.2));
          return `rgba(168, 85, 247, ${alpha})`;
        },
        borderColor: 'rgba(255, 255, 255, 0.2)',
        borderWidth: 1,
        width: ({chart}) => (chart.chartArea?.width || 0) / genres.length - 1,
        height: ({chart}) => (chart.chartArea?.height || 0) / genres.length - 1
      }]
    });
  }
}
