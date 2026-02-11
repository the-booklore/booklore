import {BookFilter} from './BookFilter';
import {BehaviorSubject, Observable, Subject} from 'rxjs';
import {BookState} from '../../../model/state/book-state.model';
import {debounceTime, filter, map, takeUntil} from 'rxjs/operators';
import {Book} from '../../../model/book.model';
import {inject, Injectable, OnDestroy} from '@angular/core';
import {MessageService} from 'primeng/api';
import {UserService} from '../../../../settings/user-management/user.service';

@Injectable({providedIn: 'root'})
export class SeriesCollapseFilter implements BookFilter, OnDestroy {
  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);

  private readonly seriesCollapseSubject = new BehaviorSubject<boolean>(false);
  readonly seriesCollapse$ = this.seriesCollapseSubject.asObservable();
  private destroy$ = new Subject<void>();

  private hasUserToggled = false;
  private currentContext: { type: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF', id: number } | null = null;

  constructor() {
    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        this.applyPreference();
      });

    this.seriesCollapse$
      .pipe(debounceTime(500))
      .subscribe(isCollapsed => {
        if (this.hasUserToggled) {
          this.persistCollapsePreference(isCollapsed);
        }
      });
  }

  setContext(type: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF' | null, id: number | null): void {
    if (type && id) {
      this.currentContext = {type, id};
    } else {
      this.currentContext = null;
    }
    this.applyPreference();
  }

  get isSeriesCollapsed(): boolean {
    return this.seriesCollapseSubject.value;
  }

  setCollapsed(value: boolean): void {
    this.hasUserToggled = true;
    this.seriesCollapseSubject.next(value);
  }

  private applyPreference(): void {
    const user = this.userService.getCurrentUser();
    const prefs = user?.userSettings?.entityViewPreferences;

    let collapsed = false;

    if (prefs) {
      // Backward compatibility: check for old 'seriesCollapse' field
      const globalAny = prefs.global as any;
      collapsed = prefs.global?.seriesCollapsed ?? globalAny?.seriesCollapse ?? false;

      if (this.currentContext) {
        const override = prefs.overrides?.find(o =>
          o.entityType === this.currentContext?.type && o.entityId === this.currentContext?.id
        );
        if (override) {
           const prefAny = override.preferences as any;
           if (override.preferences.seriesCollapsed !== undefined) {
             collapsed = override.preferences.seriesCollapsed;
           } else if (prefAny?.seriesCollapse !== undefined) {
             collapsed = prefAny.seriesCollapse;
           }
        }
      }
    }

    this.hasUserToggled = false;
    if (this.seriesCollapseSubject.value !== collapsed) {
      this.seriesCollapseSubject.next(collapsed);
    }
  }

  filter(bookState: BookState, forceExpandSeries?: boolean): Observable<BookState> {
    return this.seriesCollapse$.pipe(
      map(isCollapsed => {
        const shouldCollapse = forceExpandSeries ? false : isCollapsed;
        if (!shouldCollapse || !bookState.books) return bookState;

        const books = [...bookState.books];

        const seriesMap = new Map<string, Book[]>();
        const collapsedBooks: Book[] = [];

        for (const book of books) {
          const seriesName = book.metadata?.seriesName?.trim();
          if (seriesName) {
            if (!seriesMap.has(seriesName)) {
              seriesMap.set(seriesName, []);
            }
            seriesMap.get(seriesName)!.push(book);
          } else {
            collapsedBooks.push(book);
          }
        }

        for (const [seriesName, group] of seriesMap.entries()) {
          const sortedGroup = group.slice().sort((a, b) => {
            const aNum = a.metadata?.seriesNumber ?? Number.MAX_VALUE;
            const bNum = b.metadata?.seriesNumber ?? Number.MAX_VALUE;
            return aNum - bNum;
          });
          const firstBook = sortedGroup[0];
          collapsedBooks.push({
            ...firstBook,
            seriesBooks: group,
            seriesCount: group.length
          });
        }

        return {...bookState, books: collapsedBooks};
      })
    );
  }

  private persistCollapsePreference(isCollapsed: boolean): void {
    const user = this.userService.getCurrentUser();
    if (!user) return;

    const prefs = structuredClone(user.userSettings.entityViewPreferences ?? {
      global: {
        sortKey: 'addedOn',
        sortDir: 'DESC',
        view: 'GRID',
        coverSize: 1.0,
        seriesCollapsed: false
      },
      overrides: []
    });

    if (!prefs.overrides) {
      prefs.overrides = [];
    }

    if (this.currentContext) {
      let override = prefs.overrides.find(o =>
        o.entityType === this.currentContext?.type && o.entityId === this.currentContext?.id
      );

      if (!override) {
        override = {
          entityType: this.currentContext.type,
          entityId: this.currentContext.id,
          preferences: {
            ...prefs.global,
            seriesCollapsed: isCollapsed
          }
        };
        prefs.overrides.push(override);
      } else {
        override.preferences.seriesCollapsed = isCollapsed;
      }
    } else {
      prefs.global.seriesCollapsed = isCollapsed;
    }

    this.userService.updateUserSetting(user.id, 'entityViewPreferences', prefs);

    this.messageService.add({
      severity: 'success',
      summary: 'Preference Saved',
      detail: `Series collapse set to ${isCollapsed ? 'enabled' : 'disabled'}${this.currentContext ? ' for this view' : ' globally'}.`,
      life: 1500
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
