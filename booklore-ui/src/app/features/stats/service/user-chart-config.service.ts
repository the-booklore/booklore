import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable} from 'rxjs';
import {ReadingSessionHeatmapComponent} from '../component/reading-session-heatmap/reading-session-heatmap.component';
import {ReadingSessionTimelineComponent} from '../component/reading-session-timeline/reading-session-timeline.component';
import {GenreStatsChartComponent} from '../component/genre-stats-chart/genre-stats-chart.component';
import {CompletionTimelineChartComponent} from '../component/completion-timeline-chart/completion-timeline-chart.component';
import {FavoriteDaysChartComponent} from '../component/favorite-days-chart/favorite-days-chart.component';
import {PeakHoursChartComponent} from '../component/peak-hours-chart/peak-hours-chart.component';
import {ReadingDNAChartComponent} from '../component/reading-dna-chart/reading-dna-chart.component';
import {ReadingHabitsChartComponent} from '../component/reading-habits-chart/reading-habits-chart.component';

export interface UserChartConfig {
  id: string;
  title: string;
  component: any;
  enabled: boolean;
  sizeClass: string;
  order: number;
}

@Injectable({
  providedIn: 'root'
})
export class UserChartConfigService {
  private readonly STORAGE_KEY = 'userStatsChartConfig';
  private readonly defaultCharts: UserChartConfig[] = [
    {
      id: 'heatmap',
      title: 'Reading Session Heatmap',
      component: ReadingSessionHeatmapComponent,
      enabled: true,
      sizeClass: 'chart-full',
      order: 0
    },
    {
      id: 'favorite-days',
      title: 'Favorite Reading Days',
      component: FavoriteDaysChartComponent,
      enabled: true,
      sizeClass: 'chart-medium',
      order: 1
    },
    {
      id: 'peak-hours',
      title: 'Peak Reading Hours',
      component: PeakHoursChartComponent,
      enabled: true,
      sizeClass: 'chart-medium',
      order: 2
    },
    {
      id: 'genre-stats',
      title: 'Genre Statistics',
      component: GenreStatsChartComponent,
      enabled: true,
      sizeClass: 'chart-medium',
      order: 3
    },
    {
      id: 'completion-timeline',
      title: 'Completion Timeline',
      component: CompletionTimelineChartComponent,
      enabled: true,
      sizeClass: 'chart-medium',
      order: 4
    },
    {
      id: 'reading-dna',
      title: 'Reading DNA Profile',
      component: ReadingDNAChartComponent,
      enabled: true,
      sizeClass: 'chart-medium',
      order: 5
    },
    {
      id: 'reading-habits',
      title: 'Reading Habits Analysis',
      component: ReadingHabitsChartComponent,
      enabled: true,
      sizeClass: 'chart-medium',
      order: 6
    },
    {
      id: 'timeline',
      title: 'Reading Session Timeline',
      component: ReadingSessionTimelineComponent,
      enabled: true,
      sizeClass: 'chart-full',
      order: 7
    },
  ];

  private chartsSubject = new BehaviorSubject<UserChartConfig[]>(this.loadChartConfig());
  public charts$: Observable<UserChartConfig[]> = this.chartsSubject.asObservable();

  getVisibleCharts(): UserChartConfig[] {
    return this.chartsSubject.value.filter(chart => chart.enabled);
  }

  toggleChart(chartId: string): void {
    const charts = this.chartsSubject.value;
    const chart = charts.find(c => c.id === chartId);
    if (chart) {
      chart.enabled = !chart.enabled;
      this.saveChartConfig(charts);
      this.chartsSubject.next([...charts]);
    }
  }

  reorderCharts(previousIndex: number, currentIndex: number): void {
    const charts = [...this.chartsSubject.value];
    const [movedChart] = charts.splice(previousIndex, 1);
    charts.splice(currentIndex, 0, movedChart);

    charts.forEach((chart, index) => {
      chart.order = index;
    });

    this.saveChartConfig(charts);
    this.chartsSubject.next(charts);
  }

  resetLayout(): void {
    const resetCharts = JSON.parse(JSON.stringify(this.defaultCharts));
    this.saveChartConfig(resetCharts);
    this.chartsSubject.next(resetCharts);
  }

  private saveChartConfig(charts: UserChartConfig[]): void {
    const config = charts.map(chart => ({
      id: chart.id,
      enabled: chart.enabled,
      sizeClass: chart.sizeClass,
      order: chart.order
    }));
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(config));
  }

  private loadChartConfig(): UserChartConfig[] {
    const savedConfig = localStorage.getItem(this.STORAGE_KEY);
    if (!savedConfig) {
      return JSON.parse(JSON.stringify(this.defaultCharts));
    }

    try {
      const config = JSON.parse(savedConfig);
      const charts = JSON.parse(JSON.stringify(this.defaultCharts));

      config.forEach((saved: any) => {
        const chart = charts.find((c: UserChartConfig) => c.id === saved.id);
        if (chart) {
          chart.enabled = saved.enabled;
          chart.sizeClass = saved.sizeClass;
          chart.order = saved.order ?? chart.order;
        }
      });

      charts.sort((a: UserChartConfig, b: UserChartConfig) => a.order - b.order);

      return charts;
    } catch (e) {
      console.error('Failed to load chart config', e);
      return JSON.parse(JSON.stringify(this.defaultCharts));
    }
  }
}
