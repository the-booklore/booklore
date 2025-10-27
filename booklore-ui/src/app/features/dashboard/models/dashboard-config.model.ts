import {DEFAULT_MAX_ITEMS} from '../components/dashboard-settings/dashboard-settings.component';

export enum ScrollerType {
  LAST_READ = 'lastRead',
  LATEST_ADDED = 'latestAdded',
  RANDOM = 'random',
  MAGIC_SHELF = 'magicShelf'
}

export interface ScrollerConfig {
  id: string;
  type: ScrollerType;
  title: string;
  enabled: boolean;
  order: number;
  maxItems: number;
  magicShelfId?: number;
  sortField?: string;
  sortDirection?: string;
}

export interface DashboardConfig {
  scrollers: ScrollerConfig[];
}

export const DEFAULT_DASHBOARD_CONFIG: DashboardConfig = {
  scrollers: [
    {id: '1', type: ScrollerType.LAST_READ, title: 'Continue Reading', enabled: true, order: 1, maxItems: DEFAULT_MAX_ITEMS},
    {id: '2', type: ScrollerType.LATEST_ADDED, title: 'Recently Added', enabled: true, order: 2, maxItems: DEFAULT_MAX_ITEMS},
    {id: '3', type: ScrollerType.RANDOM, title: 'Discover Something New', enabled: true, order: 3, maxItems: DEFAULT_MAX_ITEMS}
  ]
};
