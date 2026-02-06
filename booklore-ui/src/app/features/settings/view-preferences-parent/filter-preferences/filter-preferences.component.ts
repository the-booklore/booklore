import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Select} from 'primeng/select';
import {
  ALL_FILTER_OPTIONS,
  BookFilterMode,
  DEFAULT_VISIBLE_FILTERS,
  User,
  UserService,
  UserSettings,
  UserState,
  VisibleFilterType
} from '../../user-management/user.service';
import {MessageService} from 'primeng/api';
import {Observable, Subject} from 'rxjs';
import {FormsModule} from '@angular/forms';
import {filter, takeUntil} from 'rxjs/operators';
import {MultiSelect} from 'primeng/multiselect';

const MIN_VISIBLE_FILTERS = 5;
const MAX_VISIBLE_FILTERS = 15;

@Component({
  selector: 'app-filter-preferences',
  imports: [
    Select,
    FormsModule,
    MultiSelect
  ],
  templateUrl: './filter-preferences.component.html',
  styleUrl: './filter-preferences.component.scss'
})
export class FilterPreferencesComponent implements OnInit, OnDestroy {

  readonly filterModes = [
    {label: 'And', value: 'and'},
    {label: 'Or', value: 'or'},
    {label: 'Single', value: 'single'},
  ];

  readonly allFilterOptions = ALL_FILTER_OPTIONS;
  readonly minFilters = MIN_VISIBLE_FILTERS;
  readonly maxFilters = MAX_VISIBLE_FILTERS;

  selectedFilterMode: BookFilterMode = 'and';
  selectedVisibleFilters: VisibleFilterType[] = [...DEFAULT_VISIBLE_FILTERS];

  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);
  private readonly destroy$ = new Subject<void>();

  userData$: Observable<UserState> = this.userService.userState$;
  private currentUser: User | null = null;

  ngOnInit(): void {
    this.userData$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      takeUntil(this.destroy$)
    ).subscribe(userState => {
      this.currentUser = userState.user;
      this.loadPreferences(userState.user!.userSettings);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadPreferences(settings: UserSettings): void {
    this.selectedFilterMode = settings.filterMode ?? 'and';
    this.selectedVisibleFilters = settings.visibleFilters ?? [...DEFAULT_VISIBLE_FILTERS];
  }

  private updatePreference(path: string[], value: unknown): void {
    if (!this.currentUser) return;

    let target: any = this.currentUser.userSettings;
    for (let i = 0; i < path.length - 1; i++) {
      target = target[path[i]] ||= {};
    }
    target[path.at(-1)!] = value;

    const [rootKey] = path;
    const updatedValue = this.currentUser.userSettings[rootKey as keyof UserSettings];
    this.userService.updateUserSetting(this.currentUser.id, rootKey, updatedValue);
    this.messageService.add({
      severity: 'success',
      summary: 'Preferences Updated',
      detail: 'Your preferences have been saved successfully.',
      life: 1500
    });
  }

  onFilterModeChange(): void {
    this.updatePreference(['filterMode'], this.selectedFilterMode);
  }

  onVisibleFiltersChange(): void {
    if (this.selectedVisibleFilters.length < MIN_VISIBLE_FILTERS) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Minimum Filters Required',
        detail: `Please select at least ${MIN_VISIBLE_FILTERS} filters.`,
        life: 2000
      });
      return;
    }
    if (this.selectedVisibleFilters.length > MAX_VISIBLE_FILTERS) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Maximum Filters Exceeded',
        detail: `Please select at most ${MAX_VISIBLE_FILTERS} filters.`,
        life: 2000
      });
      return;
    }
    this.updatePreference(['visibleFilters'], this.selectedVisibleFilters);
  }

  get selectionCountText(): string {
    return `${this.selectedVisibleFilters.length} of ${this.allFilterOptions.length} selected`;
  }
}
