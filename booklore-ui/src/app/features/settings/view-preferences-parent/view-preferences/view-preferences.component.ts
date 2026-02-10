import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Button} from 'primeng/button';

import {MessageService} from 'primeng/api';
import {Select} from 'primeng/select';
import {TableModule} from 'primeng/table';
import {SortCriterion, User, UserService} from '../../user-management/user.service';
import {LibraryService} from '../../../book/service/library.service';
import {ShelfService} from '../../../book/service/shelf.service';
import {MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {combineLatest, Subject} from 'rxjs';
import {FormsModule} from '@angular/forms';
import {ToastModule} from 'primeng/toast';
import {Tooltip} from 'primeng/tooltip';
import {filter, take, takeUntil} from 'rxjs/operators';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray} from '@angular/cdk/drag-drop';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-view-preferences',
  standalone: true,
  imports: [
    Select,
    FormsModule,
    Button,
    TableModule,
    ToastModule,
    Tooltip,
    ToggleSwitch,
    CdkDropList,
    CdkDrag,
    CdkDragHandle,
    TranslocoDirective
  ],
  templateUrl: './view-preferences.component.html',
  styleUrl: './view-preferences.component.scss'
})
export class ViewPreferencesComponent implements OnInit, OnDestroy {
  private t = inject(TranslocoService);

  sortOptions: {label: string; field: string; translationKey: string}[] = [
    {label: 'Title', field: 'title', translationKey: 'sortTitle'},
    {label: 'Title + Series', field: 'titleSeries', translationKey: 'sortTitleSeries'},
    {label: 'File Name', field: 'fileName', translationKey: 'sortFileName'},
    {label: 'File Path', field: 'filePath', translationKey: 'sortFilePath'},
    {label: 'Author', field: 'author', translationKey: 'sortAuthor'},
    {label: 'Author (Surname)', field: 'authorSurnameVorname', translationKey: 'sortAuthorSurname'},
    {label: 'Author + Series', field: 'authorSeries', translationKey: 'sortAuthorSeries'},
    {label: 'Last Read', field: 'lastReadTime', translationKey: 'sortLastRead'},
    {label: 'Personal Rating', field: 'personalRating', translationKey: 'sortPersonalRating'},
    {label: 'Added On', field: 'addedOn', translationKey: 'sortAddedOn'},
    {label: 'File Size', field: 'fileSizeKb', translationKey: 'sortFileSize'},
    {label: 'Locked', field: 'locked', translationKey: 'sortLocked'},
    {label: 'Publisher', field: 'publisher', translationKey: 'sortPublisher'},
    {label: 'Published Date', field: 'publishedDate', translationKey: 'sortPublishedDate'},
    {label: 'Amazon Rating', field: 'amazonRating', translationKey: 'sortAmazonRating'},
    {label: 'Amazon #', field: 'amazonReviewCount', translationKey: 'sortAmazonCount'},
    {label: 'Goodreads Rating', field: 'goodreadsRating', translationKey: 'sortGoodreadsRating'},
    {label: 'Goodreads #', field: 'goodreadsReviewCount', translationKey: 'sortGoodreadsCount'},
    {label: 'Hardcover Rating', field: 'hardcoverRating', translationKey: 'sortHardcoverRating'},
    {label: 'Hardcover #', field: 'hardcoverReviewCount', translationKey: 'sortHardcoverCount'},
    {label: 'Ranobedb Rating', field: 'ranobedbRating', translationKey: 'sortRanobedbRating'},
    {label: 'Pages', field: 'pageCount', translationKey: 'sortPages'},
    {label: 'Random', field: 'random', translationKey: 'sortRandom'},
  ];

  entityTypeOptions: {label: string; value: string; translationKey: string}[] = [
    {label: 'Library', value: 'LIBRARY', translationKey: 'entityLibrary'},
    {label: 'Shelf', value: 'SHELF', translationKey: 'entityShelf'},
    {label: 'Magic Shelf', value: 'MAGIC_SHELF', translationKey: 'entityMagicShelf'}
  ];

  sortDirectionOptions: {label: string; value: string; translationKey: string}[] = [
    {label: 'Ascending', value: 'ASC', translationKey: 'ascending'},
    {label: 'Descending', value: 'DESC', translationKey: 'descending'}
  ];

  viewModeOptions: {label: string; value: string; translationKey: string}[] = [
    {label: 'Grid', value: 'GRID', translationKey: 'viewGrid'},
    {label: 'Table', value: 'TABLE', translationKey: 'viewTable'}
  ];

  libraryOptions: { label: string; value: number }[] = [];
  shelfOptions: { label: string; value: number }[] = [];
  magicShelfOptions: { label: string; value: number }[] = [];

  selectedSort: string = 'title';
  selectedSortDir: 'ASC' | 'DESC' = 'ASC';
  selectedView: 'GRID' | 'TABLE' = 'GRID';
  autoSaveMetadata: boolean = false;
  sortCriteria: SortCriterion[] = [];
  selectedAddField: string | null = null;

  overrides: {
    entityType: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF';
    library: number;
    sort: string;
    sortDir: 'ASC' | 'DESC';
    view: 'GRID' | 'TABLE';
  }[] = [];

  private user: User | null = null;
  private readonly destroy$ = new Subject<void>();

  private libraryService = inject(LibraryService);
  private shelfService = inject(ShelfService);
  private magicShelfService = inject(MagicShelfService);
  private userService = inject(UserService);
  private messageService = inject(MessageService);

  ngOnInit(): void {
    combineLatest([
      this.userService.userState$.pipe(filter(userState => !!userState?.user && userState.loaded), take(1)),
      this.libraryService.libraryState$.pipe(filter(libraryState => !!libraryState?.libraries && libraryState.loaded), take(1)),
      this.shelfService.shelfState$.pipe(filter(shelfState => !!shelfState?.shelves && shelfState.loaded), take(1)),
      this.magicShelfService.shelvesState$.pipe(filter(magicState => !!magicState?.shelves && magicState.loaded), take(1))
    ]).pipe(
      takeUntil(this.destroy$)
    ).subscribe(([userState, librariesState, shelfState, magicState]) => {

      this.user = userState.user;
      const prefs = userState.user?.userSettings?.entityViewPreferences;
      const global = prefs?.global;
      this.selectedSort = global?.sortKey ?? 'title';
      this.selectedSortDir = global?.sortDir ?? 'ASC';
      this.selectedView = global?.view ?? 'GRID';
      this.autoSaveMetadata = userState.user?.userSettings?.autoSaveMetadata ?? false;

      // Load multi-sort criteria, falling back to legacy single sort
      if (global?.sortCriteria && global.sortCriteria.length > 0) {
        this.sortCriteria = [...global.sortCriteria];
      } else {
        this.sortCriteria = [{field: this.selectedSort, direction: this.selectedSortDir}];
      }

      this.overrides = (prefs?.overrides ?? []).map(o => ({
        entityType: o.entityType,
        library: o.entityId,
        sort: o.preferences.sortKey,
        sortDir: o.preferences.sortDir ?? 'ASC',
        view: o.preferences.view ?? 'GRID'
      }));

      this.libraryOptions = (librariesState.libraries ?? []).filter(lib => lib.id !== undefined).map(lib => ({
        label: lib.name,
        value: lib.id!
      }));

      this.shelfOptions = (shelfState?.shelves ?? []).filter(s => s.id !== undefined).map(s => ({
        label: s.name,
        value: s.id!
      }));

      this.magicShelfOptions = (magicState?.shelves ?? []).filter(s => s.id !== undefined).map(s => ({
        label: s.name,
        value: s.id!
      }));
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  getAvailableEntities(index: number, type: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF') {
    const selected = this.overrides.map((o, i) => i !== index ? o.library : null);
    let source: { label: string; value: number }[];
    switch (type) {
      case 'LIBRARY':
        source = this.libraryOptions;
        break;
      case 'SHELF':
        source = this.shelfOptions;
        break;
      case 'MAGIC_SHELF':
        source = this.magicShelfOptions;
        break;
      default:
        source = [];
    }
    return source.filter(opt => !selected.includes(opt.value) || this.overrides[index]?.library === opt.value);
  }

  get availableLibraries() {
    const used = new Set(this.overrides.map(o => `${o.entityType}_${o.library}`));

    const withEntityType = (options: { label: string; value: number }[], entityType: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF') =>
      options.map(opt => ({...opt, entityType}));

    return [...withEntityType(this.libraryOptions, 'LIBRARY'),
      ...withEntityType(this.shelfOptions, 'SHELF'),
      ...withEntityType(this.magicShelfOptions, 'MAGIC_SHELF')]
      .filter(opt => !used.has(`${opt.entityType}_${opt.value}`));
  }

  addOverride(): void {
    const next = this.availableLibraries[0];
    if (next) {
      this.overrides.push({
        entityType: next.entityType,
        library: next.value,
        sort: 'title',
        sortDir: 'ASC',
        view: 'GRID'
      });
    }
  }

  removeOverride(index: number): void {
    this.overrides.splice(index, 1);
  }

  // Multi-sort criteria methods
  get availableSortFields(): {label: string; field: string; translationKey: string}[] {
    const usedFields = new Set(this.sortCriteria.map(c => c.field));
    return this.sortOptions.filter(opt => !usedFields.has(opt.field));
  }

  getSortLabel(field: string): string {
    const key = this.sortOptions.find(opt => opt.field === field)?.translationKey;
    return key ? this.t.translate('settingsView.librarySort.' + key) : field;
  }

  addSortCriterion(): void {
    if (this.selectedAddField) {
      this.sortCriteria.push({field: this.selectedAddField, direction: 'ASC'});
      this.selectedAddField = null;
      this.syncLegacySort();
    }
  }

  removeSortCriterion(index: number): void {
    if (this.sortCriteria.length > 1) {
      this.sortCriteria.splice(index, 1);
      this.syncLegacySort();
    }
  }

  toggleSortDirection(index: number): void {
    const criterion = this.sortCriteria[index];
    criterion.direction = criterion.direction === 'ASC' ? 'DESC' : 'ASC';
    this.syncLegacySort();
  }

  onSortCriteriaDrop(event: CdkDragDrop<SortCriterion[]>): void {
    moveItemInArray(this.sortCriteria, event.previousIndex, event.currentIndex);
    this.syncLegacySort();
  }

  private syncLegacySort(): void {
    // Keep legacy fields in sync with first criterion
    if (this.sortCriteria.length > 0) {
      this.selectedSort = this.sortCriteria[0].field;
      this.selectedSortDir = this.sortCriteria[0].direction;
    }
  }

  saveSettings(): void {
    if (!this.user) return;

    const prefs = structuredClone(this.user.userSettings.entityViewPreferences ?? {});

    prefs.global = {
      ...prefs.global,
      sortKey: this.selectedSort,
      sortDir: this.selectedSortDir,
      sortCriteria: [...this.sortCriteria],
      view: this.selectedView
    };

    prefs.overrides = this.overrides.map(o => {
      const existing = prefs.overrides?.find(p =>
        p.entityId === o.library && p.entityType === o.entityType
      )?.preferences;

      return {
        entityType: o.entityType,
        entityId: o.library,
        preferences: {
          sortKey: o.sort,
          sortDir: o.sortDir,
          view: o.view,
          coverSize: existing?.coverSize ?? 1.0,
          seriesCollapsed: existing?.seriesCollapsed ?? false,
          overlayBookType: existing?.overlayBookType ?? true
        }
      };
    });

    this.userService.updateUserSetting(this.user.id, 'entityViewPreferences', prefs);
    this.userService.updateUserSetting(this.user.id, 'autoSaveMetadata', this.autoSaveMetadata);

    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.librarySort.saveSuccess'),
      detail: this.t.translate('settingsView.librarySort.saveSuccessDetail')
    });
  }
}
