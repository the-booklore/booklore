import {inject, Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';
import {MessageService} from 'primeng/api';
import {TableColumnPreference, UserService} from '../../../settings/user-management/user.service';

@Injectable({
  providedIn: 'root'
})
export class TableColumnPreferenceService {
  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);

  private readonly preferencesSubject = new BehaviorSubject<TableColumnPreference[]>([]);
  readonly preferences$ = this.preferencesSubject.asObservable();

  private readonly allAvailableColumns = [
    {field: 'readStatus', header: 'Read', width: '80px'},
    {field: 'title', header: 'Title', width: '350px'},
    {field: 'authors', header: 'Authors', width: '200px'},
    {field: 'publisher', header: 'Publisher', width: '150px'},
    {field: 'seriesName', header: 'Series', width: '200px'},
    {field: 'seriesNumber', header: 'Series #', width: '100px'},
    {field: 'categories', header: 'Genres', width: '150px'},
    {field: 'publishedDate', header: 'Published', width: '120px'},
    {field: 'lastReadTime', header: 'Last Read', width: '120px'},
    {field: 'addedOn', header: 'Added', width: '120px'},
    {field: 'fileSizeKb', header: 'File Size', width: '100px'},
    {field: 'language', header: 'Language', width: '100px'},
    {field: 'isbn', header: 'ISBN', width: '140px'},
    {field: 'pageCount', header: 'Pages', width: '100px'},
    {field: 'amazonRating', header: 'Amazon', width: '140px'},
    {field: 'amazonReviewCount', header: 'AZ #', width: '100px'},
    {field: 'goodreadsRating', header: 'Goodreads', width: '140px'},
    {field: 'goodreadsReviewCount', header: 'GR #', width: '100px'},
    {field: 'hardcoverRating', header: 'Hardcover', width: '140px'},
    {field: 'hardcoverReviewCount', header: 'HC #', width: '100px'},
    {field: 'ranobedbRating', header: 'Ranobedb', width: '140px'},
  ];

  private readonly fallbackPreferences: TableColumnPreference[] = this.allAvailableColumns.map((col, index) => ({
    field: col.field,
    visible: true,
    order: index,
    width: col.width
  }));

  initPreferences(savedPrefs: TableColumnPreference[] | undefined): void {
    const effectivePrefs = savedPrefs?.length ? savedPrefs : this.fallbackPreferences;
    this.preferencesSubject.next(this.mergeWithAllColumns(effectivePrefs));
  }

  get allColumns(): { field: string; header: string }[] {
    return this.allAvailableColumns;
  }

  get visibleColumns(): { field: string; header: string; width?: string }[] {
    return this.preferencesSubject.value
      .filter(pref => pref.visible)
      .sort((a, b) => a.order - b.order)
      .map(pref => ({
        field: pref.field,
        header: this.getColumnHeader(pref.field),
        width: pref.width
      }));
  }

  get preferences(): TableColumnPreference[] {
    return this.preferencesSubject.value;
  }

  saveVisibleColumns(selectedColumns: { field: string }[]): void {
    const selectedFieldSet = new Set(selectedColumns.map(c => c.field));
    const currentPrefsMap = new Map(this.preferencesSubject.value.map(p => [p.field, p]));

    const updatedPreferences: TableColumnPreference[] = this.allAvailableColumns.map((col, index) => {
      const selectionIndex = selectedColumns.findIndex(c => c.field === col.field);
      const outputIndex = selectionIndex >= 0 ? selectionIndex : index;
      const existingPref = currentPrefsMap.get(col.field);

      return {
        field: col.field,
        visible: selectedFieldSet.has(col.field),
        order: outputIndex,
        width: existingPref?.width // Preserve existing width
      };
    });

    this.updateAndSavePreferences(updatedPreferences);
  }

  private getColumnHeader(field: string): string {
    return this.allAvailableColumns.find(col => col.field === field)?.header ?? field;
  }

  private mergeWithAllColumns(savedPrefs: TableColumnPreference[]): TableColumnPreference[] {
    const savedPrefMap = new Map(savedPrefs.map(p => [p.field, p]));

    return this.allAvailableColumns.map((col, index) => {
      const saved = savedPrefMap.get(col.field);
      return {
        field: col.field,
        visible: saved?.visible ?? true,
        order: saved?.order ?? index,
        width: saved?.width ?? col.width
      };
    });
  }

  saveColumnWidths(columnWidths: { field: string, width: string }[]): void {
    const currentPrefs = this.preferencesSubject.value;
    const widthMap = new Map(columnWidths.map(c => [c.field, c.width]));

    const updatedPreferences = currentPrefs.map(pref => ({
      ...pref,
      width: widthMap.has(pref.field) ? widthMap.get(pref.field) : pref.width
    }));

    this.updateAndSavePreferences(updatedPreferences, false);
  }

  resetPreferences(): void {
    this.updateAndSavePreferences(this.fallbackPreferences, true);
  }

  private updateAndSavePreferences(preferences: TableColumnPreference[], notify: boolean = true): void {
    this.preferencesSubject.next(preferences);

    const currentUser = this.userService.getCurrentUser();
    if (!currentUser) return;

    this.userService.updateUserSetting(currentUser.id, 'tableColumnPreference', preferences);

    if (notify) {
      this.messageService.add({
        severity: 'success',
        summary: 'Preferences Saved',
        detail: 'Your column layout has been saved.',
        life: 1500
      });
    }
  }
}
