import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Select} from 'primeng/select';
import {SidebarLibrarySorting, SidebarMagicShelfSorting, SidebarShelfSorting, User, UserService, UserSettings, UserState} from '../../user-management/user.service';
import {MessageService} from 'primeng/api';
import {Observable, Subject} from 'rxjs';
import {FormsModule} from '@angular/forms';
import {filter, takeUntil} from 'rxjs/operators';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-sidebar-sorting-preferences',
  imports: [
    Select,
    FormsModule,
    TranslocoDirective
  ],
  templateUrl: './sidebar-sorting-preferences.component.html',
  styleUrl: './sidebar-sorting-preferences.component.scss'
})
export class SidebarSortingPreferencesComponent implements OnInit, OnDestroy {

  readonly sortingOptions = [
    {label: 'Name | Ascending', value: {field: 'name', order: 'asc'}, translationKey: 'nameAsc'},
    {label: 'Name | Descending', value: {field: 'name', order: 'desc'}, translationKey: 'nameDesc'},
    {label: 'Creation Date | Ascending', value: {field: 'id', order: 'asc'}, translationKey: 'creationAsc'},
    {label: 'Creation Date | Descending', value: {field: 'id', order: 'desc'}, translationKey: 'creationDesc'},
  ];

  selectedLibrarySorting: SidebarLibrarySorting = {field: 'id', order: 'asc'};
  selectedShelfSorting: SidebarShelfSorting = {field: 'id', order: 'asc'};
  selectedMagicShelfSorting: SidebarMagicShelfSorting = {field: 'id', order: 'asc'};

  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
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
    this.selectedLibrarySorting = settings.sidebarLibrarySorting;
    this.selectedShelfSorting = settings.sidebarShelfSorting;
    this.selectedMagicShelfSorting = settings.sidebarMagicShelfSorting;
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
      summary: this.t.translate('settingsView.sidebarSort.prefsUpdated'),
      detail: this.t.translate('settingsView.sidebarSort.prefsUpdatedDetail'),
      life: 1500
    });
  }

  onLibrarySortingChange() {
    this.updatePreference(['sidebarLibrarySorting'], this.selectedLibrarySorting);
  }

  onShelfSortingChange() {
    this.updatePreference(['sidebarShelfSorting'], this.selectedShelfSorting);
  }

  onMagicShelfSortingChange() {
    this.updatePreference(['sidebarMagicShelfSorting'], this.selectedMagicShelfSorting);
  }
}
