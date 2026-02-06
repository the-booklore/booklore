import {DEFAULT_MAX_ITEMS} from '../components/dashboard-settings/dashboard-settings.component';

export enum ScrollerType {
  LAST_READ = 'lastRead',
  LAST_LISTENED = 'lastListened',
  LATEST_ADDED = 'latestAdded',
  RANDOM = 'random',
  MAGIC_SHELF = 'magicShelf',
  UP_NEXT = 'upNext',
  READ_AGAIN = 'readAgain',
  RECOMMENDATIONS = 'recommendations'
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
    {id: '1', type: ScrollerType.LAST_LISTENED, title: 'Continue Listening', enabled: true, order: 1, maxItems: DEFAULT_MAX_ITEMS},
    {id: '2', type: ScrollerType.LAST_READ, title: 'Continue Reading', enabled: true, order: 2, maxItems: DEFAULT_MAX_ITEMS},
    {id: '3', type: ScrollerType.LATEST_ADDED, title: 'Recently Added', enabled: true, order: 3, maxItems: DEFAULT_MAX_ITEMS},
    {id: '4', type: ScrollerType.RANDOM, title: 'Discover Something New', enabled: true, order: 4, maxItems: DEFAULT_MAX_ITEMS},
    {id: '5', type: ScrollerType.UP_NEXT, title: 'Up Next', enabled: true, order: 5, maxItems: DEFAULT_MAX_ITEMS},
    {id: '6', type: ScrollerType.READ_AGAIN, title: 'Read Again', enabled: false, order: 6, maxItems: DEFAULT_MAX_ITEMS},
    {id: '7', type: ScrollerType.RECOMMENDATIONS, title: 'Recommendations', enabled: true, order: 7, maxItems: DEFAULT_MAX_ITEMS}
  ]
};
