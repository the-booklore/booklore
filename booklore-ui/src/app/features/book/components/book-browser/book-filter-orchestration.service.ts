import {Injectable, inject} from '@angular/core';
import {Observable, BehaviorSubject} from 'rxjs';
import {map, switchMap} from 'rxjs/operators';
import {BookState} from '../../model/state/book-state.model';
import {SortOption} from '../../model/sort.model';
import {SortService} from '../../service/sort.service';
import {HeaderFilter} from './filters/HeaderFilter';
import {SideBarFilter} from './filters/SidebarFilter';
import {SeriesCollapseFilter} from './filters/SeriesCollapseFilter';
import {BookFilterMode} from '../../../settings/user-management/user.service';
import {ParamMap} from '@angular/router';
import {QUERY_PARAMS} from './book-browser-query-params.service';

@Injectable({providedIn: 'root'})
export class BookFilterOrchestrationService {
  private sortService = inject(SortService);

  applyFilters(
    bookState: BookState,
    headerFilter: HeaderFilter,
    sideBarFilter: SideBarFilter,
    seriesCollapseFilter: SeriesCollapseFilter,
    forceExpandSeries: boolean,
    sortOption: SortOption
  ): Observable<BookState> {
    return headerFilter.filter(bookState).pipe(
      switchMap(filtered => sideBarFilter.filter(filtered)),
      switchMap(filtered => seriesCollapseFilter.filter(filtered, forceExpandSeries)),
      map(filtered =>
        (filtered.loaded && !filtered.error)
          ? ({
            ...filtered,
            books: this.sortService.applySort(filtered.books || [], sortOption)
          })
          : filtered
      )
    );
  }

  shouldForceExpandSeries(queryParamMap: ParamMap): boolean {
    const filterParam = queryParamMap.get(QUERY_PARAMS.FILTER);
    return (
      !!filterParam &&
      typeof filterParam === 'string' &&
      filterParam.split(',').some(pair => pair.trim().startsWith('series:'))
    );
  }

  createHeaderFilter(searchTerm$: BehaviorSubject<string>): HeaderFilter {
    return new HeaderFilter(searchTerm$);
  }

  createSideBarFilter(
    selectedFilter$: BehaviorSubject<Record<string, string[]> | null>,
    selectedFilterMode$: BehaviorSubject<BookFilterMode>
  ): SideBarFilter {
    return new SideBarFilter(selectedFilter$, selectedFilterMode$);
  }
}
