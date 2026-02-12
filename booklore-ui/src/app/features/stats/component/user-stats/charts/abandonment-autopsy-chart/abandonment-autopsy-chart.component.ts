import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {Book, ReadStatus} from '../../../../../book/model/book.model';

type CasualtyChartData = ChartData<'bar', number[], string>;

interface FunnelStage {
  label: string;
  count: number;
  percentage: number;
}

@Component({
  selector: 'app-abandonment-autopsy-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './abandonment-autopsy-chart.component.html',
  styleUrls: ['./abandonment-autopsy-chart.component.scss']
})
export class AbandonmentAutopsyChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'bar' as const;
  public readonly chartData$: Observable<CasualtyChartData>;
  public readonly chartOptions: ChartConfiguration['options'];

  public funnelStages: FunnelStage[] = [];
  public stats = {totalAbandoned: 0, avgProgress: 0, mostAbandonedGenre: '', abandonmentRate: 0};
  public insights: string[] = [];

  private readonly chartDataSubject: BehaviorSubject<CasualtyChartData>;

  constructor() {
    this.chartDataSubject = new BehaviorSubject<CasualtyChartData>({labels: [], datasets: []});
    this.chartData$ = this.chartDataSubject.asObservable();

    this.chartOptions = {
      indexAxis: 'y',
      responsive: true,
      maintainAspectRatio: false,
      layout: {padding: {top: 10, bottom: 10, left: 10, right: 10}},
      plugins: {
        legend: {display: false},
        tooltip: {
          enabled: true,
          backgroundColor: 'rgba(0, 0, 0, 0.9)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: 'rgba(239, 68, 68, 0.8)',
          borderWidth: 2,
          cornerRadius: 8,
          displayColors: false,
          padding: 16,
          titleFont: {size: 14, weight: 'bold'},
          bodyFont: {size: 13},
          callbacks: {
            label: (context) => {
              return `Abandonment rate: ${context.parsed.x}%`;
            }
          }
        },
        datalabels: {display: false}
      },
      scales: {
        x: {
          title: {
            display: true,
            text: 'Abandonment Rate (%)',
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 13, weight: 'bold'}
          },
          min: 0,
          max: 100,
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11},
            callback: (value) => `${value}%`
          },
          grid: {color: 'rgba(255, 255, 255, 0.1)'},
          border: {display: false}
        },
        y: {
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11}
          },
          grid: {display: false},
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
          console.error('Error processing abandonment data:', error);
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

    const abandonedStatuses = [ReadStatus.ABANDONED, ReadStatus.WONT_READ, ReadStatus.PARTIALLY_READ];

    const abandonedBooks = currentState.books.filter(b =>
      b.readStatus && abandonedStatuses.includes(b.readStatus as ReadStatus)
    );

    const allTrackedBooks = currentState.books.filter(b =>
      b.readStatus && b.readStatus !== ReadStatus.UNREAD && b.readStatus !== ReadStatus.UNSET
    );

    this.buildFunnel(abandonedBooks);
    this.buildGenreCasualty(abandonedBooks, currentState.books);
    this.buildStats(abandonedBooks, allTrackedBooks);
    this.buildInsights(abandonedBooks);
  }

  private getMaxProgress(book: Book): number {
    const progresses: number[] = [];
    if (book.epubProgress?.percentage) progresses.push(book.epubProgress.percentage);
    if (book.pdfProgress?.percentage) progresses.push(book.pdfProgress.percentage);
    if (book.cbxProgress?.percentage) progresses.push(book.cbxProgress.percentage);
    if (book.koreaderProgress?.percentage) progresses.push(book.koreaderProgress.percentage);
    if (book.koboProgress?.percentage) progresses.push(book.koboProgress.percentage);
    return progresses.length > 0 ? Math.max(...progresses) : 0;
  }

  private buildFunnel(abandonedBooks: Book[]): void {
    const ranges = [
      {label: '0-10%', min: 0, max: 10},
      {label: '10-25%', min: 10, max: 25},
      {label: '25-50%', min: 25, max: 50},
      {label: '50-75%', min: 50, max: 75},
      {label: '75-100%', min: 75, max: 100},
    ];

    const total = abandonedBooks.length || 1;
    this.funnelStages = ranges.map(range => {
      const count = abandonedBooks.filter(b => {
        const progress = this.getMaxProgress(b) * 100;
        return progress >= range.min && progress < (range.max === 100 ? 101 : range.max);
      }).length;
      return {
        label: range.label,
        count,
        percentage: Math.round((count / total) * 100)
      };
    });
  }

  private buildGenreCasualty(abandonedBooks: Book[], allBooks: Book[]): void {
    const genreAbandoned = new Map<string, number>();
    const genreTotal = new Map<string, number>();

    allBooks.forEach(book => {
      const genre = book.metadata?.categories?.[0];
      if (genre) {
        genreTotal.set(genre, (genreTotal.get(genre) || 0) + 1);
      }
    });

    abandonedBooks.forEach(book => {
      const genre = book.metadata?.categories?.[0];
      if (genre) {
        genreAbandoned.set(genre, (genreAbandoned.get(genre) || 0) + 1);
      }
    });

    const genreRates = Array.from(genreTotal.entries())
      .filter(([genre]) => genreAbandoned.has(genre))
      .map(([genre, total]) => ({
        genre,
        rate: Math.round(((genreAbandoned.get(genre) || 0) / total) * 100)
      }))
      .sort((a, b) => b.rate - a.rate)
      .slice(0, 10);

    const labels = genreRates.map(g => g.genre.length > 20 ? g.genre.substring(0, 20) + '...' : g.genre);
    const values = genreRates.map(g => g.rate);
    const bgColors = genreRates.map(g => {
      const t = g.rate / 100;
      return `rgba(239, 68, 68, ${Math.max(0.3, t)})`;
    });

    this.chartDataSubject.next({
      labels,
      datasets: [{
        label: 'Abandonment Rate',
        data: values,
        backgroundColor: bgColors,
        borderColor: bgColors.map(c => c.replace(/[\d.]+\)$/, '1)')),
        borderWidth: 1,
        borderRadius: 4,
        barPercentage: 0.8,
        categoryPercentage: 0.7
      }]
    });
  }

  private buildStats(abandonedBooks: Book[], allTrackedBooks: Book[]): void {
    this.stats.totalAbandoned = abandonedBooks.length;

    const progresses = abandonedBooks.map(b => this.getMaxProgress(b) * 100);
    this.stats.avgProgress = progresses.length > 0
      ? Math.round(progresses.reduce((a, b) => a + b, 0) / progresses.length)
      : 0;

    const genreCounts = new Map<string, number>();
    abandonedBooks.forEach(b => {
      const genre = b.metadata?.categories?.[0];
      if (genre) genreCounts.set(genre, (genreCounts.get(genre) || 0) + 1);
    });
    let maxGenre = '-';
    let maxCount = 0;
    genreCounts.forEach((count, genre) => {
      if (count > maxCount) {
        maxCount = count;
        maxGenre = genre;
      }
    });
    this.stats.mostAbandonedGenre = maxGenre;

    this.stats.abandonmentRate = allTrackedBooks.length > 0
      ? Math.round((abandonedBooks.length / allTrackedBooks.length) * 100)
      : 0;
  }

  private buildInsights(abandonedBooks: Book[]): void {
    this.insights = [];

    if (abandonedBooks.length === 0) {
      this.insights.push('No abandoned books found - great finishing streak!');
      return;
    }

    const progresses = abandonedBooks.map(b => this.getMaxProgress(b) * 100);
    const avgProgress = progresses.reduce((a, b) => a + b, 0) / progresses.length;

    if (avgProgress < 25) {
      this.insights.push(`You tend to abandon books early (avg ${Math.round(avgProgress)}% in)`);
    } else if (avgProgress > 50) {
      this.insights.push(`You give books a fair shot before abandoning (avg ${Math.round(avgProgress)}% in)`);
    }

    if (this.stats.mostAbandonedGenre !== '-') {
      const genreCount = abandonedBooks.filter(b => b.metadata?.categories?.[0] === this.stats.mostAbandonedGenre).length;
      const rate = Math.round((genreCount / abandonedBooks.length) * 100);
      this.insights.push(`${this.stats.mostAbandonedGenre} has a ${rate}% share of your abandonments`);
    }

    if (this.stats.abandonmentRate > 30) {
      this.insights.push(`Your ${this.stats.abandonmentRate}% abandonment rate suggests being more selective might help`);
    } else if (this.stats.abandonmentRate < 10) {
      this.insights.push(`Low ${this.stats.abandonmentRate}% abandonment rate - you rarely give up on a book`);
    }
  }
}
