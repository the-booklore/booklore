import {Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';

export interface KoreaderChartConfig {
  id: string;
  name: string;
  enabled: boolean;
  order: number;
}

@Injectable({
  providedIn: 'root'
})
export class KoreaderChartConfigService {
  private readonly STORAGE_KEY = 'booklore-koreader-chart-config';

  private readonly defaultCharts: KoreaderChartConfig[] = [
    {id: 'summaryCards', name: 'Summary Cards', enabled: true, order: 0},
    {id: 'readingCalendar', name: 'Reading Calendar', enabled: true, order: 1},
    {id: 'dayOfWeek', name: 'Reading by Day of Week', enabled: true, order: 2},
    {id: 'readingActivity', name: 'Reading Activity Heatmap', enabled: true, order: 3}
  ];

  private chartsConfigSubject = new BehaviorSubject<KoreaderChartConfig[]>(this.loadConfig());
  public chartsConfig$ = this.chartsConfigSubject.asObservable();

  constructor() {
    this.initializeConfig();
  }

  private initializeConfig(): void {
    const savedConfig = this.loadConfig();
    this.chartsConfigSubject.next(savedConfig);
  }

  private loadConfig(): KoreaderChartConfig[] {
    try {
      const saved = localStorage.getItem(this.STORAGE_KEY);
      if (saved) {
        const savedConfig = JSON.parse(saved) as KoreaderChartConfig[];
        return this.mergeWithDefaults(savedConfig);
      }
    } catch (error) {
      console.error('Error loading KOReader chart config from localStorage:', error);
    }
    return [...this.defaultCharts];
  }

  private mergeWithDefaults(savedConfig: KoreaderChartConfig[]): KoreaderChartConfig[] {
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

  private saveConfig(config: KoreaderChartConfig[]): void {
    try {
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(config));
    } catch (error) {
      console.error('Error saving KOReader chart config to localStorage:', error);
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

  public getEnabledChartsSorted(): KoreaderChartConfig[] {
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

    const [movedChart] = enabledCharts.splice(fromIndex, 1);
    enabledCharts.splice(toIndex, 0, movedChart);

    enabledCharts.forEach((chart, index) => {
      const configIndex = currentConfig.findIndex(c => c.id === chart.id);
      if (configIndex !== -1) {
        currentConfig[configIndex] = {...currentConfig[configIndex], order: index};
      }
    });

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
