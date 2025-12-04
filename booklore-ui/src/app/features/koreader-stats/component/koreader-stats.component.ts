import {Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {PageTitleService} from '../../../shared/service/page-title.service';
import {DailyReadingStats, KoreaderStatsService} from '../service/koreader-stats.service';
import {catchError, forkJoin, of} from 'rxjs';
import {Card} from 'primeng/card';
import {ReadingCalendarComponent} from './reading-calendar/reading-calendar.component';
import {DayOfWeekChartComponent} from './day-of-week-chart/day-of-week-chart.component';
import {CdkDragDrop, DragDropModule} from '@angular/cdk/drag-drop';
import {KoreaderChartConfig, KoreaderChartConfigService} from '../service/koreader-chart-config.service';

export interface ReadingStatsSummary {
  totalReadingTimeSeconds: number;
  totalPagesRead: number;
  longestDaySeconds: number;
  longestDayDate: string;
  mostPagesInDay: number;
  mostPagesDate: string;
}

interface MonthData {
  year: number;
  month: number;
  label: string;
  weeks: WeekData[];
  totalMinutes: number;
  totalPages: number;
}

interface WeekData {
  days: DayData[];
}

interface DayData {
  date: Date | null;
  durationSeconds: number;
  pagesRead: number;
  intensity: number;
}

@Component({
  selector: 'app-koreader-stats',
  standalone: true,
  imports: [
    CommonModule,
    Card,
    ReadingCalendarComponent,
    DayOfWeekChartComponent,
    DragDropModule
  ],
  templateUrl: './koreader-stats.component.html',
  styleUrls: ['./koreader-stats.component.scss']
})
export class KoreaderStatsComponent implements OnInit {
  private readonly pageTitle = inject(PageTitleService);
  private readonly koreaderStatsService = inject(KoreaderStatsService);
  private readonly chartConfigService = inject(KoreaderChartConfigService);

  public isLoading = true;
  public hasError = false;
  public stats: ReadingStatsSummary | null = null;
  public monthsData: MonthData[] = [];
  public selectedDay: DayData | null = null;
  public maxDailyMinutes = 0;
  public showConfigPanel = false;

  private readonly WEEKDAY_LABELS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

  ngOnInit(): void {
    this.pageTitle.setPageTitle('KOReader Reading Stats');
    this.loadData();
  }

  private loadData(): void {
    forkJoin({
      summary: this.koreaderStatsService.getReadingStatsSummary().pipe(
        catchError(error => {
          console.error('Error loading KOReader summary stats:', error);
          return of(null);
        })
      ),
      daily: this.koreaderStatsService.getDailyReadingStats().pipe(
        catchError(error => {
          console.error('Error loading KOReader daily stats:', error);
          return of([]);
        })
      )
    }).subscribe(({summary, daily}) => {
      if (!summary) {
        this.hasError = true;
      } else {
        this.stats = summary;
        this.processHeatmapData(daily);
      }
      this.isLoading = false;
    });
  }

  private processHeatmapData(dailyStats: DailyReadingStats[]): void {
    if (!dailyStats || dailyStats.length === 0) {
      this.monthsData = [];
      return;
    }

    // Create a map of date -> stats for quick lookup
    const statsMap = new Map<string, DailyReadingStats>();
    dailyStats.forEach(stat => {
      statsMap.set(stat.date, stat);
    });

    // Find max daily minutes for intensity calculation
    this.maxDailyMinutes = Math.max(...dailyStats.map(s => s.durationSeconds / 60));

    // Get all unique months that have data
    const monthsWithData = new Set<string>();
    dailyStats.forEach(stat => {
      const date = new Date(stat.date);
      monthsWithData.add(`${date.getFullYear()}-${date.getMonth()}`);
    });

    // Sort months chronologically
    const sortedMonths = Array.from(monthsWithData)
      .map(key => {
        const [year, month] = key.split('-').map(Number);
        return {year, month};
      })
      .sort((a, b) => a.year - b.year || a.month - b.month);

    // Build month data for each month with activity
    this.monthsData = sortedMonths.map(({year, month}) => {
      return this.buildMonthData(year, month, statsMap);
    });
  }

  private buildMonthData(year: number, month: number, statsMap: Map<string, DailyReadingStats>): MonthData {
    const monthNames = ['January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'];

    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const daysInMonth = lastDay.getDate();
    const startDayOfWeek = firstDay.getDay();

    const weeks: WeekData[] = [];
    let currentWeek: DayData[] = [];
    let totalMinutes = 0;
    let totalPages = 0;

    // Add empty cells for days before the first of the month
    for (let i = 0; i < startDayOfWeek; i++) {
      currentWeek.push({date: null, durationSeconds: 0, pagesRead: 0, intensity: 0});
    }

    // Add each day of the month
    for (let day = 1; day <= daysInMonth; day++) {
      const date = new Date(year, month, day);
      const dateStr = date.toISOString().split('T')[0];
      const stat = statsMap.get(dateStr);

      const durationSeconds = stat?.durationSeconds || 0;
      const pagesRead = stat?.pagesRead || 0;
      const minutes = durationSeconds / 60;
      const intensity = this.maxDailyMinutes > 0 ? Math.min(1, minutes / this.maxDailyMinutes) : 0;

      totalMinutes += minutes;
      totalPages += pagesRead;

      currentWeek.push({date, durationSeconds, pagesRead, intensity});

      if (currentWeek.length === 7) {
        weeks.push({days: currentWeek});
        currentWeek = [];
      }
    }

    // Add remaining days to last week
    if (currentWeek.length > 0) {
      while (currentWeek.length < 7) {
        currentWeek.push({date: null, durationSeconds: 0, pagesRead: 0, intensity: 0});
      }
      weeks.push({days: currentWeek});
    }

    return {
      year,
      month,
      label: `${monthNames[month]} ${year}`,
      weeks,
      totalMinutes,
      totalPages
    };
  }

  getIntensityClass(intensity: number): string {
    if (intensity === 0) return 'intensity-0';
    if (intensity < 0.25) return 'intensity-1';
    if (intensity < 0.5) return 'intensity-2';
    if (intensity < 0.75) return 'intensity-3';
    return 'intensity-4';
  }

  formatDuration(seconds: number): string {
    if (!seconds) return '0h 0m';

    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);

    if (hours === 0) return `${minutes}m`;
    return `${hours}h ${minutes}m`;
  }

  formatDate(dateString: string | null): string {
    if (!dateString) return 'N/A';

    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
  }

  formatDayDate(date: Date | null): string {
    if (!date) return '';
    return date.toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
  }

  get weekdayLabels(): string[] {
    return this.WEEKDAY_LABELS;
  }

  // Chart configuration methods
  getEnabledChartsSorted(): KoreaderChartConfig[] {
    return this.chartConfigService.getEnabledChartsSorted();
  }

  isChartEnabled(chartId: string): boolean {
    return this.chartConfigService.isChartEnabled(chartId);
  }

  onChartReorder(event: CdkDragDrop<KoreaderChartConfig[]>): void {
    if (event.previousIndex !== event.currentIndex) {
      this.chartConfigService.reorderCharts(event.previousIndex, event.currentIndex);
    }
  }

  toggleConfigPanel(): void {
    this.showConfigPanel = !this.showConfigPanel;
  }

  closeConfigPanel(): void {
    this.showConfigPanel = false;
  }

  toggleChart(chartId: string): void {
    this.chartConfigService.toggleChart(chartId);
  }

  enableAllCharts(): void {
    this.chartConfigService.enableAllCharts();
  }

  disableAllCharts(): void {
    this.chartConfigService.disableAllCharts();
  }

  resetChartPositions(): void {
    this.chartConfigService.resetPositions();
  }

  get chartsConfig$() {
    return this.chartConfigService.chartsConfig$;
  }
}
