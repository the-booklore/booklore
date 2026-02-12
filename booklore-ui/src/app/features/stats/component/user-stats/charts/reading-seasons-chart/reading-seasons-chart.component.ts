import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {Book, ReadStatus} from '../../../../../book/model/book.model';

type SeasonsChartData = ChartData<'bar', number[], string>;

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const GENRE_COLORS = [
  'rgba(99, 102, 241, 0.85)',
  'rgba(239, 68, 68, 0.85)',
  'rgba(34, 197, 94, 0.85)',
  'rgba(251, 146, 60, 0.85)',
  'rgba(168, 85, 247, 0.85)',
  'rgba(14, 165, 233, 0.85)',
];

@Component({
  selector: 'app-reading-seasons-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './reading-seasons-chart.component.html',
  styleUrls: ['./reading-seasons-chart.component.scss']
})
export class ReadingSeasonsChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'bar' as const;
  public readonly chartData$: Observable<SeasonsChartData>;
  public readonly chartOptions: ChartConfiguration['options'];

  public currentYear: number | null = new Date().getFullYear();
  public showAllTime = false;
  public stats = {diverseMonth: '', focusedMonth: '', seasonalInsight: ''};

  private readonly chartDataSubject: BehaviorSubject<SeasonsChartData>;
  private allBooks: Book[] = [];

  constructor() {
    this.chartDataSubject = new BehaviorSubject<SeasonsChartData>({labels: [], datasets: []});
    this.chartData$ = this.chartDataSubject.asObservable();

    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      layout: {padding: {top: 10, bottom: 10, left: 10, right: 10}},
      plugins: {
        legend: {
          display: true,
          position: 'top',
          labels: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11},
            boxWidth: 12,
            padding: 10
          }
        },
        tooltip: {
          enabled: true,
          backgroundColor: 'rgba(0, 0, 0, 0.9)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: '#ffffff',
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
          stacked: true,
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11}
          },
          grid: {display: false},
          border: {display: false}
        },
        y: {
          stacked: true,
          title: {
            display: true,
            text: 'Books Read',
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 13, weight: 'bold'}
          },
          beginAtZero: true,
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11},
            stepSize: 1
          },
          grid: {color: 'rgba(255, 255, 255, 0.1)'},
          border: {display: false}
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
          console.error('Error processing reading seasons data:', error);
          return EMPTY;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        const currentState = this.bookService.getCurrentBookState();
        if (currentState?.loaded && currentState.books?.length) {
          this.allBooks = currentState.books.filter(b =>
            b.dateFinished && b.readStatus === ReadStatus.READ
          );
        }
        this.calculateAndUpdate();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public changeYear(delta: number): void {
    if (this.currentYear !== null) {
      this.currentYear += delta;
      this.showAllTime = false;
      this.calculateAndUpdate();
    }
  }

  public toggleAllTime(): void {
    this.showAllTime = !this.showAllTime;
    if (this.showAllTime) {
      this.currentYear = null;
    } else {
      this.currentYear = new Date().getFullYear();
    }
    this.calculateAndUpdate();
  }

  private calculateAndUpdate(): void {
    const filtered = this.showAllTime
      ? this.allBooks
      : this.allBooks.filter(b => {
        const year = new Date(b.dateFinished!).getFullYear();
        return year === this.currentYear;
      });

    const genreMonthMap = new Map<string, number[]>();

    filtered.forEach(book => {
      const month = new Date(book.dateFinished!).getMonth();
      const genre = book.metadata?.categories?.[0] || 'Other';

      if (!genreMonthMap.has(genre)) {
        genreMonthMap.set(genre, new Array(12).fill(0));
      }
      genreMonthMap.get(genre)![month]++;
    });

    const genreTotals = Array.from(genreMonthMap.entries())
      .map(([genre, months]) => ({genre, total: months.reduce((a, b) => a + b, 0)}))
      .sort((a, b) => b.total - a.total);

    const topGenres = genreTotals.slice(0, 5).map(g => g.genre);
    const otherGenres = genreTotals.slice(5).map(g => g.genre);

    let otherMonths: number[] | null = null;
    if (otherGenres.length > 0) {
      otherMonths = new Array(12).fill(0);
      otherGenres.forEach(genre => {
        const months = genreMonthMap.get(genre)!;
        months.forEach((count, i) => otherMonths![i] += count);
      });
    }

    this.calculateStats(genreMonthMap);

    const datasets = topGenres.map((genre, idx) => ({
      label: genre,
      data: genreMonthMap.get(genre)!,
      backgroundColor: GENRE_COLORS[idx % GENRE_COLORS.length],
      borderColor: GENRE_COLORS[idx % GENRE_COLORS.length].replace('0.85', '1'),
      borderWidth: 1,
      borderRadius: 2,
      barPercentage: 0.8,
      categoryPercentage: 0.7
    }));

    if (otherMonths && otherMonths.some(v => v > 0)) {
      datasets.push({
        label: 'Other',
        data: otherMonths,
        backgroundColor: 'rgba(148, 163, 184, 0.6)',
        borderColor: 'rgba(148, 163, 184, 1)',
        borderWidth: 1,
        borderRadius: 2,
        barPercentage: 0.8,
        categoryPercentage: 0.7
      });
    }

    this.chartDataSubject.next({labels: MONTH_NAMES, datasets});
  }

  private calculateStats(genreMonthMap: Map<string, number[]>): void {
    const monthGenreCounts = new Array(12).fill(0).map(() => new Set<string>());
    const monthTotals = new Array(12).fill(0);

    genreMonthMap.forEach((months, genre) => {
      months.forEach((count, monthIdx) => {
        if (count > 0) {
          monthGenreCounts[monthIdx].add(genre);
          monthTotals[monthIdx] += count;
        }
      });
    });

    let maxDiversity = 0;
    let diverseMonthIdx = 0;
    let minDiversity = Infinity;
    let focusedMonthIdx = 0;

    monthGenreCounts.forEach((genres, idx) => {
      if (monthTotals[idx] > 0) {
        if (genres.size > maxDiversity) {
          maxDiversity = genres.size;
          diverseMonthIdx = idx;
        }
        if (genres.size < minDiversity) {
          minDiversity = genres.size;
          focusedMonthIdx = idx;
        }
      }
    });

    this.stats.diverseMonth = maxDiversity > 0 ? `${MONTH_NAMES[diverseMonthIdx]} (${maxDiversity} genres)` : '-';
    this.stats.focusedMonth = minDiversity < Infinity ? `${MONTH_NAMES[focusedMonthIdx]} (${minDiversity} genre${minDiversity !== 1 ? 's' : ''})` : '-';

    const peakMonth = monthTotals.indexOf(Math.max(...monthTotals));
    const lowMonth = monthTotals.indexOf(Math.min(...monthTotals.filter(v => v > 0)));
    if (peakMonth >= 0 && lowMonth >= 0 && monthTotals[peakMonth] > 0) {
      this.stats.seasonalInsight = `Peak in ${MONTH_NAMES[peakMonth]}, quiet in ${MONTH_NAMES[lowMonth]}`;
    } else {
      this.stats.seasonalInsight = '-';
    }
  }
}
