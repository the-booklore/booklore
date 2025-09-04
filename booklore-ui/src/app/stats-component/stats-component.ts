import {Component, inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, registerables, Tooltip} from 'chart.js';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {catchError, map, of, startWith, Subject, takeUntil} from 'rxjs';
import {LibraryFilterService, LibraryOption} from './charts-service/library-filter.service';
import {LibrariesSummaryService} from './charts-service/libraries-summary.service';
import {Select} from 'primeng/select';
import {ReadStatusChartService} from './charts-service/read-status-chart.service';
import {BookTypeChartService} from './charts-service/book-type-chart.service';
import {ReadingProgressChartService} from './charts-service/reading-progress-chart-service';
import {PageCountChartService} from './charts-service/page-count-chart.service';
import {BookRatingChartService} from './charts-service/book-rating-chart.service';
import {PersonalRatingChartService} from './charts-service/personal-rating-chart.service';
import {PublicationYearChartService} from './charts-service/publication-year-chart-service';
import {AuthorPopularityChartService} from './charts-service/author-popularity-chart.service';
import {ReadingCompletionChartService} from './charts-service/reading-completion-chart.service';
import {LanguageDistributionChartService} from './charts-service/language-distribution-chart.service';
import {BookQualityScoreChartService} from './charts-service/book-quality-score-chart.service';
import {BookSizeChartService} from './charts-service/book-size-chart.service';
import {ReadingVelocityTimelineChartService} from './charts-service/reading-velocity-timeline-chart.service';
import {MonthlyReadingPatternsChartService} from './charts-service/monthly-reading-patterns-chart.service';
import {TopSeriesChartService} from './charts-service/top-series-chart.service';
import {FinishedBooksTimelineChartService} from './charts-service/finished-books-timeline-chart.service';
import ChartDataLabels from 'chartjs-plugin-datalabels';

@Component({
  selector: 'app-stats-component',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    BaseChartDirective,
    Select
  ],
  templateUrl: './stats-component.html',
  styleUrls: ['./stats-component.scss']
})
export class StatsComponent implements OnInit, OnDestroy {

  @ViewChild(BaseChartDirective) chart: BaseChartDirective | undefined;

  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly librariesSummaryService = inject(LibrariesSummaryService);
  protected readonly readStatusChartService = inject(ReadStatusChartService);
  protected readonly bookTypeChartService = inject(BookTypeChartService);
  protected readonly readingProgressChartService = inject(ReadingProgressChartService);
  protected readonly pageCountChartService = inject(PageCountChartService);
  protected readonly bookRatingChartService = inject(BookRatingChartService);
  protected readonly personalRatingChartService = inject(PersonalRatingChartService);
  protected readonly publicationYearChartService = inject(PublicationYearChartService);
  protected readonly authorPopularityChartService = inject(AuthorPopularityChartService);
  protected readonly readingCompletionChartService = inject(ReadingCompletionChartService);
  protected readonly languageDistributionChartService = inject(LanguageDistributionChartService);
  protected readonly bookQualityScoreChartService = inject(BookQualityScoreChartService);
  protected readonly bookSizeChartService = inject(BookSizeChartService);
  protected readonly readingVelocityTimelineChartService = inject(ReadingVelocityTimelineChartService);
  protected readonly monthlyReadingPatternsChartService = inject(MonthlyReadingPatternsChartService);
  protected readonly topSeriesChartService = inject(TopSeriesChartService);
  protected readonly finishedBooksTimelineChartService = inject(FinishedBooksTimelineChartService);
  private readonly destroy$ = new Subject<void>();

  public isLoading = true;
  public hasData = false;
  public hasError = false;
  public libraryOptions: LibraryOption[] = [];
  public selectedLibrary: LibraryOption | null = null;

  booksSummary$ = this.librariesSummaryService.getBooksSummary().pipe(
    catchError(error => {
      console.error('Error loading books summary:', error);
      this.hasError = true;
      return of({totalBooks: 0, totalSizeKb: 0, totalAuthors: 0, totalSeries: 0, totalPublishers: 0});
    })
  );

  public readonly totalBooks$ = this.booksSummary$.pipe(map(summary => summary.totalBooks));
  public readonly totalAuthors$ = this.booksSummary$.pipe(map(summary => summary.totalAuthors));
  public readonly totalSeries$ = this.booksSummary$.pipe(map(summary => summary.totalSeries));
  public readonly totalPublishers$ = this.booksSummary$.pipe(map(summary => summary.totalPublishers));
  public readonly totalSize$ = this.librariesSummaryService.getFormattedSize().pipe(catchError(() => of('0 KB')));

  ngOnInit(): void {
    Chart.register(...registerables, Tooltip, ChartDataLabels);
    Chart.defaults.plugins.legend.labels.font = {
      family: "'Inter', sans-serif",
      size: 11.5,
    };
    this.loadLibraryOptions();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onLibraryChange(): void {
    if (!this.selectedLibrary) {
      return;
    }
    const libraryId = this.selectedLibrary.id;
    this.libraryFilterService.setSelectedLibrary(libraryId);
  }

  private loadLibraryOptions(): void {
    this.libraryFilterService.getLibraryOptions()
      .pipe(
        takeUntil(this.destroy$),
        startWith([]),
        catchError(error => {
          console.error('Error loading library options:', error);
          this.hasError = true;
          this.isLoading = false;
          return of([]);
        })
      )
      .subscribe({
        next: (options) => {
          this.libraryOptions = options;
          this.initializeSelectedLibrary(options);
        },
        error: (error) => {
          console.error('Subscription error:', error);
          this.hasError = true;
          this.isLoading = false;
        }
      });
  }

  private initializeSelectedLibrary(options: LibraryOption[]): void {
    if (options.length === 0) {
      this.hasData = false;
      this.isLoading = false;
      return;
    }

    if (!this.selectedLibrary) {
      this.hasData = true;
      this.isLoading = false;
      this.selectedLibrary = options[0];
      this.libraryFilterService.setSelectedLibrary(this.selectedLibrary.id);
    }
  }

  protected readonly ChartDataLabels = ChartDataLabels;
}
