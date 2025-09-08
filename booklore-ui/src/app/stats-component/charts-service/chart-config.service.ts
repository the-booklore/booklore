import {Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';

export interface ChartConfig {
  id: string;
  name: string;
  enabled: boolean;
  category: 'small' | 'medium' | 'large' | 'full-width';
  order: number;
}

@Injectable({
  providedIn: 'root'
})
export class ChartConfigService {
  private readonly STORAGE_KEY = 'booklore-chart-config';

  private readonly defaultCharts: ChartConfig[] = [
    {id: 'readingStatus', name: 'Reading Status', enabled: true, category: 'small', order: 0},
    {id: 'bookFormats', name: 'Book Formats', enabled: true, category: 'small', order: 1},
    {id: 'bookMetadataScore', name: 'Book Metadata Score', enabled: true, category: 'small', order: 2},
    {id: 'readingHeatmap', name: 'Books Finished per Month', category: 'small', enabled: true, order: 3},
    {id: 'topAuthors', name: 'Top 25 Authors', enabled: true, category: 'large', order: 4},
    {id: 'topCategories', name: 'Top 25 Categories', enabled: true, category: 'large', order: 5},
    {id: 'monthlyReadingPatterns', name: 'Monthly Reading Patterns', enabled: true, category: 'large', order: 6},
    {id: 'readingVelocityTimeline', name: 'Reading Velocity Timeline', enabled: true, category: 'large', order: 7},
    {id: 'readingProgress', name: 'Reading Progress', enabled: true, category: 'medium', order: 8},
    {id: 'externalRating', name: 'External Rating Distribution', enabled: true, category: 'medium', order: 9},
    {id: 'personalRating', name: 'Personal Rating Distribution', enabled: true, category: 'medium', order: 10},
    {id: 'pageCount', name: 'Page Count Distribution', enabled: true, category: 'medium', order: 11},
    {id: 'topBooksBySize', name: 'Top 20 Largest Books', enabled: true, category: 'large', order: 12},
    {id: 'topSeries', name: 'Top 20 Series', enabled: true, category: 'large', order: 13},
    {id: 'readingDNA', name: 'Reading DNA Profile', enabled: true, category: 'large', order: 14},
    {id: 'readingHabits', name: 'Reading Habits Analysis', enabled: true, category: 'large', order: 15},
    {id: 'publicationYear', name: 'Publication Year Timeline', enabled: true, category: 'full-width', order: 16},
    {id: 'languageDistribution', name: 'Language Distribution', enabled: true, category: 'small', order: 17}
  ];

  private chartsConfigSubject = new BehaviorSubject<ChartConfig[]>(this.loadConfig());
  public chartsConfig$ = this.chartsConfigSubject.asObservable();

  constructor() {
    this.initializeConfig();
  }

  private initializeConfig(): void {
    const savedConfig = this.loadConfig();
    this.chartsConfigSubject.next(savedConfig);
  }

  private loadConfig(): ChartConfig[] {
    try {
      const saved = localStorage.getItem(this.STORAGE_KEY);
      if (saved) {
        const savedConfig = JSON.parse(saved) as ChartConfig[];
        return this.mergeWithDefaults(savedConfig);
      }
    } catch (error) {
      console.error('Error loading chart config from localStorage:', error);
    }
    return [...this.defaultCharts];
  }

  private mergeWithDefaults(savedConfig: ChartConfig[]): ChartConfig[] {
    const merged = [...this.defaultCharts];

    savedConfig.forEach(saved => {
      const index = merged.findIndex(chart => chart.id === saved.id);
      if (index !== -1) {
        merged[index] = {
          ...merged[index],
          enabled: saved.enabled,
          order: saved.order !== undefined ? saved.order : merged[index].order
        };
      }
    });

    return merged.sort((a, b) => a.order - b.order);
  }

  private saveConfig(config: ChartConfig[]): void {
    try {
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(config));
    } catch (error) {
      console.error('Error saving chart config to localStorage:', error);
    }
  }

  public toggleChart(chartId: string): void {
    const currentConfig = this.chartsConfigSubject.value;
    const updatedConfig = currentConfig.map(chart =>
      chart.id === chartId ? {...chart, enabled: !chart.enabled} : chart
    );

    this.chartsConfigSubject.next(updatedConfig);
    this.saveConfig(updatedConfig);
  }

  public isChartEnabled(chartId: string): boolean {
    const config = this.chartsConfigSubject.value;
    const chart = config.find(c => c.id === chartId);
    return chart?.enabled ?? false;
  }

  public enableAllCharts(): void {
    const updatedConfig = this.chartsConfigSubject.value.map(chart => ({...chart, enabled: true}));
    this.chartsConfigSubject.next(updatedConfig);
    this.saveConfig(updatedConfig);
  }

  public disableAllCharts(): void {
    const updatedConfig = this.chartsConfigSubject.value.map(chart => ({...chart, enabled: false}));
    this.chartsConfigSubject.next(updatedConfig);
    this.saveConfig(updatedConfig);
  }

  public getChartsByCategory(category: string): ChartConfig[] {
    return this.chartsConfigSubject.value.filter(chart => chart.category === category);
  }

  public getEnabledChartsSorted(): ChartConfig[] {
    return this.chartsConfigSubject.value
      .filter(chart => chart.enabled)
      .sort((a, b) => a.order - b.order);
  }

  public reorderCharts(fromIndex: number, toIndex: number): void {
    const currentConfig = [...this.chartsConfigSubject.value];
    const enabledCharts = currentConfig.filter(chart => chart.enabled).sort((a, b) => a.order - b.order);

    if (fromIndex >= enabledCharts.length || toIndex >= enabledCharts.length) {
      return;
    }

    // Move the chart from fromIndex to toIndex
    const [movedChart] = enabledCharts.splice(fromIndex, 1);
    enabledCharts.splice(toIndex, 0, movedChart);

    // Update order values for all enabled charts
    enabledCharts.forEach((chart, index) => {
      const configIndex = currentConfig.findIndex(c => c.id === chart.id);
      if (configIndex !== -1) {
        currentConfig[configIndex] = {...currentConfig[configIndex], order: index};
      }
    });

    this.chartsConfigSubject.next(currentConfig);
    this.saveConfig(currentConfig);
  }

  public resetOrder(): void {
    const currentConfig = this.chartsConfigSubject.value.map((chart, index) => ({
      ...chart,
      order: this.defaultCharts.find(d => d.id === chart.id)?.order ?? index
    }));

    this.chartsConfigSubject.next(currentConfig);
    this.saveConfig(currentConfig);
  }

  public resetPositions(): void {
    const resetConfig = this.defaultCharts.map(defaultChart => {
      const currentChart = this.chartsConfigSubject.value.find(c => c.id === defaultChart.id);
      return {
        ...defaultChart,
        enabled: currentChart?.enabled ?? defaultChart.enabled
      };
    });

    this.chartsConfigSubject.next(resetConfig);
    this.saveConfig(resetConfig);
  }
}
