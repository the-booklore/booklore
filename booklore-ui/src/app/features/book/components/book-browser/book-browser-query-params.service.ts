import {Injectable, inject} from '@angular/core';
import {ActivatedRoute, ParamMap, Router} from '@angular/router';
import {SortDirection, SortOption} from '../../model/sort.model';
import {BookFilterMode, EntityViewPreferences} from '../../../settings/user-management/user.service';
import {EntityType} from './book-browser.component';

export const QUERY_PARAMS = {
  VIEW: 'view',
  SORT: 'sort',
  DIRECTION: 'direction',
  FILTER: 'filter',
  FMODE: 'fmode',
  SIDEBAR: 'sidebar',
  FROM: 'from',
} as const;

export const VIEW_MODES = {
  GRID: 'grid',
  TABLE: 'table',
} as const;

export const SORT_DIRECTION = {
  ASCENDING: 'asc',
  DESCENDING: 'desc',
} as const;

export interface BookBrowserQueryState {
  viewMode: 'grid' | 'table';
  sortField: string;
  sortDirection: SortDirection;
  filters: Record<string, string[]>;
  filterMode: BookFilterMode;
}

export interface QueryParseResult {
  viewMode: string;
  sortOption: SortOption;
  filters: Record<string, string[]>;
  filterMode: BookFilterMode;
  viewModeFromToggle: boolean;
}

@Injectable({providedIn: 'root'})
export class BookBrowserQueryParamsService {
  private router = inject(Router);
  private activatedRoute = inject(ActivatedRoute);

  parseQueryParams(
    queryParamMap: ParamMap,
    userPrefs: EntityViewPreferences | undefined,
    entityType: EntityType | undefined,
    entityId: number | undefined,
    sortOptions: SortOption[],
    defaultFilterMode: BookFilterMode
  ): QueryParseResult {
    const viewParam = queryParamMap.get(QUERY_PARAMS.VIEW);
    const sortParam = queryParamMap.get(QUERY_PARAMS.SORT);
    const directionParam = queryParamMap.get(QUERY_PARAMS.DIRECTION);
    const filterParams = queryParamMap.get(QUERY_PARAMS.FILTER);
    const filterModeParam = queryParamMap.get(QUERY_PARAMS.FMODE);
    const fromParam = queryParamMap.get(QUERY_PARAMS.FROM);

    const filterMode = (filterModeParam || defaultFilterMode) as BookFilterMode;

    // Parse filters
    const filters = this.deserializeFilters(filterParams);

    // Determine effective preferences
    const globalPrefs = userPrefs?.global;
    const currentEntityTypeStr = entityType?.toString().toUpperCase().replaceAll(' ', '_');
    const override = userPrefs?.overrides?.find(o =>
      o.entityType?.toUpperCase() === currentEntityTypeStr &&
      o.entityId === entityId
    );

    const effectivePrefs = override?.preferences ?? globalPrefs ?? {
      sortKey: 'addedOn',
      sortDir: 'ASC',
      view: 'GRID'
    };

    const userSortKey = effectivePrefs.sortKey;
    const userSortDir = effectivePrefs.sortDir?.toUpperCase() === 'DESC'
      ? SortDirection.DESCENDING
      : SortDirection.ASCENDING;

    const effectiveSortKey = sortParam || userSortKey;
    const effectiveSortDir = directionParam
      ? (directionParam.toLowerCase() === SORT_DIRECTION.DESCENDING ? SortDirection.DESCENDING : SortDirection.ASCENDING)
      : userSortDir;

    const matchedSort = sortOptions.find(opt => opt.field === effectiveSortKey);
    const sortOption: SortOption = matchedSort ? {
      label: matchedSort.label,
      field: matchedSort.field,
      direction: effectiveSortDir
    } : {
      label: 'Added On',
      field: 'addedOn',
      direction: SortDirection.DESCENDING
    };

    // Determine view mode
    const viewModeFromToggle = fromParam === 'toggle';
    const viewMode = viewModeFromToggle
      ? (viewParam === VIEW_MODES.TABLE || viewParam === VIEW_MODES.GRID
        ? viewParam
        : VIEW_MODES.GRID)
      : (effectivePrefs.view?.toLowerCase() ?? VIEW_MODES.GRID);

    return {
      viewMode,
      sortOption,
      filters,
      filterMode,
      viewModeFromToggle
    };
  }

  updateViewMode(mode: 'grid' | 'table'): void {
    this.router.navigate([], {
      queryParams: {
        [QUERY_PARAMS.VIEW]: mode,
        [QUERY_PARAMS.FROM]: 'toggle'
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  updateSort(sortOption: SortOption): void {
    const currentParams = this.activatedRoute.snapshot.queryParams;
    const newParams = {
      ...currentParams,
      [QUERY_PARAMS.SORT]: sortOption.field,
      [QUERY_PARAMS.DIRECTION]: sortOption.direction === SortDirection.ASCENDING
        ? SORT_DIRECTION.ASCENDING
        : SORT_DIRECTION.DESCENDING
    };

    this.router.navigate([], {
      queryParams: newParams,
      replaceUrl: true
    });
  }

  updateFilters(filters: Record<string, string[]> | null): void {
    const queryParam = filters && Object.keys(filters).length > 0
      ? this.serializeFilters(filters)
      : null;

    if (queryParam !== this.activatedRoute.snapshot.queryParamMap.get(QUERY_PARAMS.FILTER)) {
      this.router.navigate([], {
        relativeTo: this.activatedRoute,
        queryParams: {[QUERY_PARAMS.FILTER]: queryParam},
        queryParamsHandling: 'merge',
        replaceUrl: false
      });
    }
  }

  updateFilterMode(mode: BookFilterMode, currentFilters: Record<string, string[]>): void {
    const params: Record<string, string | null> = {[QUERY_PARAMS.FMODE]: mode};

    // Clear filters if switching from multiple selected to single mode
    if (mode === 'single') {
      const categories = Object.keys(currentFilters);
      if (categories.length > 1 || (categories.length === 1 && currentFilters[categories[0]].length > 1)) {
        params[QUERY_PARAMS.FILTER] = null;
      }
    }

    this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: params,
      queryParamsHandling: 'merge',
      replaceUrl: false
    });
  }

  serializeFilters(filters: Record<string, string[]>): string {
    return Object.entries(filters)
      .map(([k, v]) => `${k}:${v.join('|')}`)
      .join(',');
  }

  deserializeFilters(filterParam: string | null): Record<string, string[]> {
    const parsedFilters: Record<string, string[]> = {};

    if (!filterParam) return parsedFilters;

    filterParam.split(',').forEach(pair => {
      const [key, ...valueParts] = pair.split(':');
      const value = valueParts.join(':');
      if (key && value) {
        parsedFilters[key] = value.split('|').map(v => v.trim()).filter(Boolean);
      }
    });

    return parsedFilters;
  }

  syncQueryParams(
    viewMode: string,
    sortOption: SortOption,
    filterMode: BookFilterMode,
    filters: Record<string, string[]>
  ): void {
    const queryParams: Record<string, string | number | null | undefined> = {
      [QUERY_PARAMS.VIEW]: viewMode,
      [QUERY_PARAMS.SORT]: sortOption.field,
      [QUERY_PARAMS.DIRECTION]: sortOption.direction === SortDirection.ASCENDING
        ? SORT_DIRECTION.ASCENDING
        : SORT_DIRECTION.DESCENDING,
      [QUERY_PARAMS.FMODE]: filterMode,
    };

    if (Object.keys(filters).length > 0) {
      queryParams[QUERY_PARAMS.FILTER] = this.serializeFilters(filters);
    }

    const currentParams = this.activatedRoute.snapshot.queryParams;
    const changed = Object.keys(queryParams).some(k => currentParams[k] !== queryParams[k]);

    if (changed) {
      const mergedParams = {...currentParams, ...queryParams};
      this.router.navigate([], {
        queryParams: mergedParams,
        replaceUrl: true
      });
    }
  }

  shouldForceExpandSeries(queryParamMap: ParamMap): boolean {
    const filterParam = queryParamMap.get(QUERY_PARAMS.FILTER);
    return (
      !!filterParam &&
      typeof filterParam === 'string' &&
      filterParam.split(',').some(pair => pair.trim().startsWith('series:'))
    );
  }
}
