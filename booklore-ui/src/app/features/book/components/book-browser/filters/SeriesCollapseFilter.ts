import {BookFilter} from './BookFilter';
import {BehaviorSubject, Observable, Subject} from 'rxjs';
import {BookState} from '../../../model/state/book-state.model';
import {debounceTime, filter, map, take, takeUntil} from 'rxjs/operators';
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

  constructor() {
    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        take(1),
        takeUntil(this.destroy$)
      )
      .subscribe(user => {
        const prefs = user.user?.userSettings?.entityViewPreferences;
        const initialCollapsed = prefs?.global?.seriesCollapsed ?? false;
        this.seriesCollapseSubject.next(initialCollapsed);
      });

    this.seriesCollapse$
      .pipe(debounceTime(500))
      .subscribe(isCollapsed => {
        if (this.hasUserToggled) {
          this.persistCollapsePreference(isCollapsed);
        }
      });
  }

  get isSeriesCollapsed(): boolean {
    return this.seriesCollapseSubject.value;
  }

  setCollapsed(value: boolean): void {
    this.hasUserToggled = true;
    this.seriesCollapseSubject.next(value);
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
            seriesCount: group.length
          });
        }

        return {...bookState, books: collapsedBooks};
      })
    );
  }

  private persistCollapsePreference(isCollapsed: boolean): void {
    const user = this.userService.getCurrentUser();
    const prefs = user?.userSettings?.entityViewPreferences;
    if (!user || !prefs) return;

    prefs.global = prefs.global ?? {};
    prefs.global.seriesCollapsed = isCollapsed;

    this.userService.updateUserSetting(user.id, 'entityViewPreferences', prefs);

    this.messageService.add({
      severity: 'success',
      summary: 'Preference Saved',
      detail: `Series collapse set to ${isCollapsed ? 'enabled' : 'disabled'}.`,
      life: 1500
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
