import {Component, ElementRef, inject, OnDestroy, OnInit, viewChild} from '@angular/core';
import {Button} from 'primeng/button';

import {MessageService} from 'primeng/api';
import {Select} from 'primeng/select';
import {TableModule} from 'primeng/table';
import {DEFAULT_VISIBLE_SORT_FIELDS, SortCriterion, User, UserService} from '../../user-management/user.service';
import {LibraryService} from '../../../book/service/library.service';
import {ShelfService} from '../../../book/service/shelf.service';
import {MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {combineLatest, Subject} from 'rxjs';
import {FormsModule} from '@angular/forms';
import {ToastModule} from 'primeng/toast';
import {Tooltip} from 'primeng/tooltip';
import {filter, take, takeUntil} from 'rxjs/operators';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {SortDirection, SortOption} from '../../../book/model/sort.model';
import {MultiSortPopoverComponent} from '../../../book/components/book-browser/sorting/multi-sort-popover/multi-sort-popover.component';
import {Popover} from 'primeng/popover';
import {CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray} from '@angular/cdk/drag-drop';

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
    TranslocoDirective,
    MultiSortPopoverComponent,
    Popover,
    CdkDropList,
    CdkDrag,
    CdkDragHandle
  ],
  templateUrl: './view-preferences.component.html',
  styleUrl: './view-preferences.component.scss'
})
export class ViewPreferencesComponent implements OnInit, OnDestroy {
  private t = inject(TranslocoService);

  sortOptions: {label: string; field: string; translationKey: string}[] = [
    {label: 'Title', field: 'title', translationKey: 'sortTitle'},
    {label: 'File Name', field: 'fileName', translationKey: 'sortFileName'},
    {label: 'File Path', field: 'filePath', translationKey: 'sortFilePath'},
    {label: 'Author', field: 'author', translationKey: 'sortAuthor'},
    {label: 'Author (Surname)', field: 'authorSurnameVorname', translationKey: 'sortAuthorSurname'},
    {label: 'Series Name', field: 'seriesName', translationKey: 'sortSeriesName'},
    {label: 'Series Number', field: 'seriesNumber', translationKey: 'sortSeriesNumber'},
    {label: 'Last Read', field: 'lastReadTime', translationKey: 'sortLastRead'},
    {label: 'Personal Rating', field: 'personalRating', translationKey: 'sortPersonalRating'},
    {label: 'Added On', field: 'addedOn', translationKey: 'sortAddedOn'},
    {label: 'File Size', field: 'fileSizeKb', translationKey: 'sortFileSize'},
    {label: 'Locked', field: 'locked', translationKey: 'sortLocked'},
    {label: 'Publisher', field: 'publisher', translationKey: 'sortPublisher'},
    {label: 'Published Date', field: 'publishedDate', translationKey: 'sortPublishedDate'},
    {label: 'Read Status', field: 'readStatus', translationKey: 'sortReadStatus'},
    {label: 'Date Finished', field: 'dateFinished', translationKey: 'sortDateFinished'},
    {label: 'Reading Progress', field: 'readingProgress', translationKey: 'sortReadingProgress'},
    {label: 'Book Type', field: 'bookType', translationKey: 'sortBookType'},
    {label: 'Amazon Rating', field: 'amazonRating', translationKey: 'sortAmazonRating'},
    {label: 'Amazon #', field: 'amazonReviewCount', translationKey: 'sortAmazonCount'},
    {label: 'Goodreads Rating', field: 'goodreadsRating', translationKey: 'sortGoodreadsRating'},
    {label: 'Goodreads #', field: 'goodreadsReviewCount', translationKey: 'sortGoodreadsCount'},
    {label: 'Hardcover Rating', field: 'hardcoverRating', translationKey: 'sortHardcoverRating'},
    {label: 'Hardcover #', field: 'hardcoverReviewCount', translationKey: 'sortHardcoverCount'},
    {label: 'Ranobedb Rating', field: 'ranobedbRating', translationKey: 'sortRanobedbRating'},
    {label: 'Narrator', field: 'narrator', translationKey: 'sortNarrator'},
    {label: 'Pages', field: 'pageCount', translationKey: 'sortPages'},
    {label: 'Random', field: 'random', translationKey: 'sortRandom'},
  ];

  entityTypeOptions: {label: string; value: string; translationKey: string}[] = [
    {label: 'Library', value: 'LIBRARY', translationKey: 'entityLibrary'},
    {label: 'Shelf', value: 'SHELF', translationKey: 'entityShelf'},
    {label: 'Magic Shelf', value: 'MAGIC_SHELF', translationKey: 'entityMagicShelf'}
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

  // SortOption[] versions for the multi-sort-popover component
  globalSortAsOptions: SortOption[] = [];
  allSortAsOptions: SortOption[] = [];

  // Visible sort fields configuration
  visibleSortFields: string[] = [];
  selectedAddSortField: string | null = null;
  readonly minSortFields = 3;
  readonly maxSortFields = 27;

  private readonly sortFieldList = viewChild<ElementRef<HTMLElement>>('sortFieldList');

  overrides: {
    entityType: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF';
    library: number;
    sort: string;
    sortDir: 'ASC' | 'DESC';
    sortCriteria: SortCriterion[];
    sortCriteriaAsOptions: SortOption[];
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

      // Build SortOption[] versions for the popover
      this.allSortAsOptions = this.sortOptions.map(o => ({
        label: this.t.translate('settingsView.librarySort.' + o.translationKey),
        field: o.field,
        direction: SortDirection.ASCENDING
      }));
      this.globalSortAsOptions = this.toSortOptions(this.sortCriteria);

      this.visibleSortFields = userState.user?.userSettings?.visibleSortFields
        ? [...userState.user.userSettings.visibleSortFields]
        : [...DEFAULT_VISIBLE_SORT_FIELDS];

      this.overrides = (prefs?.overrides ?? []).map(o => {
        const sc = o.preferences.sortCriteria?.length
          ? [...o.preferences.sortCriteria]
          : [{field: o.preferences.sortKey, direction: o.preferences.sortDir ?? 'ASC'} as SortCriterion];
        return {
          entityType: o.entityType,
          library: o.entityId,
          sort: o.preferences.sortKey,
          sortDir: o.preferences.sortDir ?? 'ASC',
          sortCriteria: sc,
          sortCriteriaAsOptions: this.toSortOptions(sc),
          view: o.preferences.view ?? 'GRID'
        };
      });

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
      const defaultCriteria: SortCriterion[] = [{field: 'title', direction: 'ASC'}];
      this.overrides.push({
        entityType: next.entityType,
        library: next.value,
        sort: 'title',
        sortDir: 'ASC',
        sortCriteria: defaultCriteria,
        sortCriteriaAsOptions: this.toSortOptions(defaultCriteria),
        view: 'GRID'
      });
    }
  }

  removeOverride(index: number): void {
    this.overrides.splice(index, 1);
  }

  // Conversion helpers
  private toSortOptions(criteria: SortCriterion[]): SortOption[] {
    return criteria.map(c => ({
      label: this.t.translate('settingsView.librarySort.' + (this.sortOptions.find(o => o.field === c.field)?.translationKey ?? c.field)),
      field: c.field,
      direction: c.direction === 'ASC' ? SortDirection.ASCENDING : SortDirection.DESCENDING
    }));
  }

  private toSortCriteria(options: SortOption[]): SortCriterion[] {
    return options.map(o => ({
      field: o.field,
      direction: o.direction === SortDirection.ASCENDING ? 'ASC' as const : 'DESC' as const
    }));
  }

  onGlobalSortCriteriaChange(criteria: SortOption[]): void {
    this.globalSortAsOptions = criteria;
    this.sortCriteria = this.toSortCriteria(criteria);
    this.syncLegacySort();
  }

  onOverrideSortCriteriaChange(index: number, criteria: SortOption[]): void {
    this.overrides[index].sortCriteriaAsOptions = criteria;
    this.overrides[index].sortCriteria = this.toSortCriteria(criteria);
    this.overrides[index].sort = criteria[0]?.field ?? 'title';
    this.overrides[index].sortDir = criteria[0]?.direction === SortDirection.ASCENDING ? 'ASC' : 'DESC';
  }

  private syncLegacySort(): void {
    if (this.sortCriteria.length > 0) {
      this.selectedSort = this.sortCriteria[0].field;
      this.selectedSortDir = this.sortCriteria[0].direction;
    }
  }

  // Visible sort fields management
  getSortFieldLabel(field: string): string {
    const key = this.sortOptions.find(opt => opt.field === field)?.translationKey;
    return key ? this.t.translate('settingsView.librarySort.' + key) : field;
  }

  get availableSortFieldsToAdd(): {label: string; value: string}[] {
    const used = new Set(this.visibleSortFields);
    return this.sortOptions
      .filter(opt => !used.has(opt.field))
      .map(opt => ({label: this.t.translate('settingsView.librarySort.' + opt.translationKey), value: opt.field}));
  }

  get sortFieldSelectionCountText(): string {
    return this.t.translate('settingsView.librarySort.sortFieldCount', {
      count: this.visibleSortFields.length,
      total: this.sortOptions.length
    });
  }

  onSortFieldDrop(event: CdkDragDrop<string[]>): void {
    moveItemInArray(this.visibleSortFields, event.previousIndex, event.currentIndex);
  }

  addSortField(): void {
    if (this.selectedAddSortField) {
      this.visibleSortFields.push(this.selectedAddSortField);
      this.selectedAddSortField = null;
      requestAnimationFrame(() => {
        const el = this.sortFieldList()?.nativeElement;
        if (el) el.scrollTop = el.scrollHeight;
      });
    }
  }

  removeSortField(index: number): void {
    if (this.visibleSortFields.length > this.minSortFields) {
      this.visibleSortFields.splice(index, 1);
    }
  }

  resetSortFieldsToDefaults(): void {
    this.visibleSortFields = [...DEFAULT_VISIBLE_SORT_FIELDS];
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
          sortKey: o.sortCriteria[0]?.field ?? o.sort,
          sortDir: o.sortCriteria[0]?.direction ?? o.sortDir,
          sortCriteria: [...o.sortCriteria],
          view: o.view,
          coverSize: existing?.coverSize ?? 1.0,
          seriesCollapsed: existing?.seriesCollapsed ?? false,
          overlayBookType: existing?.overlayBookType ?? true
        }
      };
    });

    this.userService.updateUserSetting(this.user.id, 'entityViewPreferences', prefs);
    this.userService.updateUserSetting(this.user.id, 'autoSaveMetadata', this.autoSaveMetadata);
    this.userService.updateUserSetting(this.user.id, 'visibleSortFields', this.visibleSortFields);

    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.librarySort.saveSuccess'),
      detail: this.t.translate('settingsView.librarySort.saveSuccessDetail')
    });
  }
}
