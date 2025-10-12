import {Component, inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, registerables, Tooltip} from 'chart.js';
import 'chartjs-chart-matrix';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {catchError, map, of, startWith, Subject, takeUntil} from 'rxjs';
import {CdkDragDrop, DragDropModule} from '@angular/cdk/drag-drop';
import {Select} from 'primeng/select';
import ChartDataLabels from 'chartjs-plugin-datalabels';
import {Button} from 'primeng/button';
import {MatrixController, MatrixElement} from 'chartjs-chart-matrix';
import {LibraryFilterService, LibraryOption} from '../service/library-filter.service';
import {LibrariesSummaryService} from '../service/libraries-summary.service';
import {ReadStatusChartService} from '../service/read-status-chart.service';
import {BookTypeChartService} from '../service/book-type-chart.service';
import {ReadingProgressChartService} from '../service/reading-progress-chart-service';
import {PageCountChartService} from '../service/page-count-chart.service';
import {BookRatingChartService} from '../service/book-rating-chart.service';
import {PersonalRatingChartService} from '../service/personal-rating-chart.service';
import {PublicationYearChartService} from '../service/publication-year-chart-service';
import {AuthorPopularityChartService} from '../service/author-popularity-chart.service';
import {ReadingCompletionChartService} from '../service/reading-completion-chart.service';
import {LanguageDistributionChartService} from '../service/language-distribution-chart.service';
import {BookQualityScoreChartService} from '../service/book-quality-score-chart.service';
import {BookSizeChartService} from '../service/book-size-chart.service';
import {ReadingVelocityTimelineChartService} from '../service/reading-velocity-timeline-chart.service';
import {MonthlyReadingPatternsChartService} from '../service/monthly-reading-patterns-chart.service';
import {TopSeriesChartService} from '../service/top-series-chart.service';
import {ReadingDNAChartService} from '../service/reading-dna-chart.service';
import {ReadingHabitsChartService} from '../service/reading-habits-chart.service';
import {ChartConfig, ChartConfigService} from '../service/chart-config.service';
import {ReadingHeatmapChartService} from '../service/reading-heatmap-chart.service';

@Component({
  selector: 'app-stats-component',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    BaseChartDirective,
    Select,
    DragDropModule,
    Button
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
  protected readonly readingDNAChartService = inject(ReadingDNAChartService);
  protected readonly readingHabitsChartService = inject(ReadingHabitsChartService);
  protected readonly chartConfigService = inject(ChartConfigService);
  protected readonly readingHeatmapChartService = inject(ReadingHeatmapChartService);
  private readonly destroy$ = new Subject<void>();

  public isLoading = true;
  public hasData = false;
  public hasError = false;
  public libraryOptions: LibraryOption[] = [];
  public selectedLibrary: LibraryOption | null = null;
  public showConfigPanel = false;
  public chartsConfig: ChartConfig[] = [];

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
    Chart.register(...registerables, Tooltip, ChartDataLabels, MatrixController, MatrixElement);
    // Register matrix chart - it's automatically registered when imported
    Chart.defaults.plugins.legend.labels.font = {
      family: "'Inter', sans-serif",
      size: 11.5,
    };
    this.loadLibraryOptions();
    this.loadChartConfig();
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

  private loadChartConfig(): void {
    this.chartConfigService.chartsConfig$
      .pipe(takeUntil(this.destroy$))
      .subscribe(config => {
        this.chartsConfig = config;
      });
  }

  public toggleConfigPanel(): void {
    this.showConfigPanel = !this.showConfigPanel;
  }

  public closeConfigPanel(): void {
    this.showConfigPanel = false;
  }

  public toggleChart(chartId: string): void {
    this.chartConfigService.toggleChart(chartId);
  }

  public isChartEnabled(chartId: string): boolean {
    return this.chartConfigService.isChartEnabled(chartId);
  }

  public enableAllCharts(): void {
    this.chartConfigService.enableAllCharts();
  }

  public disableAllCharts(): void {
    this.chartConfigService.disableAllCharts();
  }

  public getChartsByCategory(category: string): ChartConfig[] {
    return this.chartsConfig.filter(chart => chart.category === category);
  }

  public getEnabledChartsSorted(): ChartConfig[] {
    return this.chartConfigService.getEnabledChartsSorted();
  }

  public onChartReorder(event: CdkDragDrop<ChartConfig[]>): void {
    if (event.previousIndex !== event.currentIndex) {
      this.chartConfigService.reorderCharts(event.previousIndex, event.currentIndex);
    }
  }

  public resetChartOrder(): void {
    this.chartConfigService.resetOrder();
  }

  public resetChartPositions(): void {
    this.chartConfigService.resetPositions();
  }

  trackByTrait(index: number, insight: any): string {
    return insight.trait;
  }

  trackByHabit(index: number, habit: any): string {
    return habit.habit;
  }

  protected readonly ChartDataLabels = ChartDataLabels;
}
