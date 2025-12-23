import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {IconService} from '../../services/icon.service';
import {IconCacheService} from '../../services/icon-cache.service';
import {DomSanitizer, SafeHtml} from '@angular/platform-browser';
import {UrlHelperService} from '../../service/url-helper.service';
import {MessageService} from 'primeng/api';
import {IconCategoriesHelper} from '../../helpers/icon-categories.helper';
import {Button} from 'primeng/button';
import {TabsModule} from 'primeng/tabs';
import {UserService} from '../../../features/settings/user-management/user.service';

interface SvgEntry {
  name: string;
  content: string;
  preview: SafeHtml | null;
  error: string;
}

interface IconSaveResult {
  iconName: string;
  success: boolean;
  errorMessage: string;
}

interface SvgIconBatchResponse {
  totalRequested: number;
  successCount: number;
  failureCount: number;
  results: IconSaveResult[];
}

@Component({
  selector: 'app-icon-picker-component',
  imports: [
    FormsModule,
    Button,
    TabsModule
  ],
  templateUrl: './icon-picker-component.html',
  styleUrl: './icon-picker-component.scss'
})
export class IconPickerComponent implements OnInit {

  private readonly SVG_PAGE_SIZE = 50;
  private readonly MAX_ICON_NAME_LENGTH = 255;
  private readonly MAX_SVG_SIZE = 1048576; // 1MB
  private readonly ICON_NAME_PATTERN = /^[a-zA-Z0-9_-]+$/;
  private readonly ERROR_MESSAGES = {
    NO_CONTENT: 'Please paste SVG content',
    NO_NAME: 'Please provide a name for the icon',
    INVALID_NAME: 'Icon name can only contain alphanumeric characters and hyphens',
    NAME_TOO_LONG: `Icon name must not exceed ${this.MAX_ICON_NAME_LENGTH} characters`,
    INVALID_SVG: 'Invalid SVG content. Please paste valid SVG code.',
    MISSING_SVG_TAG: 'Content must include <svg> tag',
    SVG_TOO_LARGE: 'SVG content must not exceed 1MB',
    PARSE_ERROR: 'Failed to parse SVG content',
    LOAD_ICONS_ERROR: 'Failed to load SVG icons. Please try again.',
    DELETE_ERROR: 'Failed to delete icon. Please try again.'
  };

  ref = inject(DynamicDialogRef);
  iconService = inject(IconService);
  iconCache = inject(IconCacheService);
  sanitizer = inject(DomSanitizer);
  urlHelper = inject(UrlHelperService);
  messageService = inject(MessageService);
  userService = inject(UserService);

  searchText: string = '';
  selectedIcon: string | null = null;
  icons: string[] = IconCategoriesHelper.createIconList();

  private _activeTabIndex: string = '0';

  get activeTabIndex(): string {
    return this._activeTabIndex;
  }

  set activeTabIndex(value: string) {
    this._activeTabIndex = value;
    if (value === '1' && this.svgIcons.length === 0 && !this.isLoadingSvgIcons) {
      this.loadSvgIcons(0);
    }
  }

  svgContent: string = '';
  svgName: string = '';
  svgPreview: SafeHtml | null = null;
  errorMessage: string = '';

  svgEntries: SvgEntry[] = [];
  isSavingBatch: boolean = false;
  batchErrorMessage: string = '';

  svgIcons: string[] = [];
  svgSearchText: string = '';
  currentSvgPage: number = 0;
  totalSvgPages: number = 0;
  isLoadingSvgIcons: boolean = false;
  svgIconsError: string = '';
  selectedSvgIcon: string | null = null;

  draggedSvgIcon: string | null = null;
  isTrashHover: boolean = false;

  ngOnInit(): void {
    if (this.activeTabIndex === '1') {
      this.loadSvgIcons(0);
    }
  }

  filteredIcons(): string[] {
    if (!this.searchText) return this.icons;
    return this.icons.filter(icon => icon.toLowerCase().includes(this.searchText.toLowerCase()));
  }

  filteredSvgIcons(): string[] {
    if (!this.svgSearchText) return this.svgIcons;
    return this.svgIcons.filter(icon => icon.toLowerCase().includes(this.svgSearchText.toLowerCase()));
  }

  selectIcon(icon: string): void {
    this.selectedIcon = icon;
    this.ref.close({type: 'PRIME_NG', value: icon});
  }

  loadSvgIcons(page: number): void {
    this.isLoadingSvgIcons = true;
    this.svgIconsError = '';

    this.iconService.getIconNames(page, this.SVG_PAGE_SIZE).subscribe({
      next: (response) => {
        this.svgIcons = response.content;
        this.currentSvgPage = response.number;
        this.totalSvgPages = response.totalPages;
        this.isLoadingSvgIcons = false;

        this.preloadSvgContent(response.content);
      },
      error: () => {
        this.isLoadingSvgIcons = false;
        this.svgIconsError = this.ERROR_MESSAGES.LOAD_ICONS_ERROR;
      }
    });
  }

  private preloadSvgContent(iconNames: string[]): void {
    iconNames.forEach(iconName => {
      if (!this.iconCache.isCached(iconName)) {
        this.loadSvgContent(iconName);
      }
    });
  }

  private loadSvgContent(iconName: string): void {
    this.iconService.getSanitizedSvgContent(iconName).subscribe({
      next: () => {
      },
      error: () => {
        const errorSvg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="red"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>';
        const sanitized = this.sanitizer.bypassSecurityTrustHtml(errorSvg);
        this.iconCache.cacheIcon(iconName, errorSvg, sanitized);
      }
    });
  }

  getSvgContent(iconName: string): SafeHtml | null {
    return this.iconCache.getCachedSanitized(iconName) || null;
  }

  selectSvgIcon(iconName: string): void {
    this.selectedSvgIcon = iconName;
    this.ref.close({type: 'CUSTOM_SVG', value: iconName});
  }

  onSvgContentChange(): void {
    this.errorMessage = '';

    if (!this.svgContent.trim()) {
      this.svgPreview = null;
      return;
    }

    const trimmedContent = this.svgContent.trim();
    if (!trimmedContent.includes('<svg')) {
      this.svgPreview = null;
      this.errorMessage = this.ERROR_MESSAGES.MISSING_SVG_TAG;
      return;
    }

    try {
      this.svgPreview = this.sanitizer.bypassSecurityTrustHtml(this.svgContent);
    } catch {
      this.svgPreview = null;
      this.errorMessage = this.ERROR_MESSAGES.PARSE_ERROR;
    }
  }

  addSvgEntry(): void {
    const validationError = this.validateSvgInput();
    if (validationError) {
      this.errorMessage = validationError;
      return;
    }

    const existingIndex = this.svgEntries.findIndex(entry => entry.name === this.svgName);
    if (existingIndex !== -1) {
      this.svgEntries[existingIndex] = {
        name: this.svgName,
        content: this.svgContent,
        preview: this.svgPreview,
        error: ''
      };
    } else {
      this.svgEntries.push({
        name: this.svgName,
        content: this.svgContent,
        preview: this.svgPreview,
        error: ''
      });
    }

    this.resetSvgForm();
    this.errorMessage = '';
  }

  removeSvgEntry(index: number): void {
    this.svgEntries.splice(index, 1);
  }

  clearAllEntries(): void {
    this.svgEntries = [];
    this.batchErrorMessage = '';
  }

  saveAllSvgs(): void {
    if (this.svgEntries.length === 0) {
      this.batchErrorMessage = 'No SVG icons to save';
      return;
    }

    this.isSavingBatch = true;
    this.batchErrorMessage = '';

    this.svgEntries.forEach(entry => entry.error = '');

    const svgData = this.svgEntries.map(entry => ({
      svgName: entry.name,
      svgData: entry.content
    }));

    this.iconService.saveBatchSvgIcons(svgData).subscribe({
      next: (response: SvgIconBatchResponse) => {
        this.isSavingBatch = false;
        this.handleBatchSaveResponse(response);
      },
      error: (error) => {
        this.isSavingBatch = false;
        this.batchErrorMessage = error.error?.message || 'Failed to save SVG icons. Please try again.';
      }
    });
  }

  private handleBatchSaveResponse(response: SvgIconBatchResponse): void {
    if (response.failureCount === 0) {
      this.handleSuccessfulBatchSave();
      return;
    }

    response.results.forEach(result => {
      if (!result.success) {
        const entryIndex = this.svgEntries.findIndex(entry => entry.name === result.iconName);
        if (entryIndex !== -1) {
          this.svgEntries[entryIndex].error = result.errorMessage;
        }
      }
    });

    const successfulNames = response.results
      .filter(result => result.success)
      .map(result => result.iconName);

    this.svgEntries = this.svgEntries.filter(entry => !successfulNames.includes(entry.name));

    if (response.successCount > 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Partial Success',
        detail: `${response.successCount} of ${response.totalRequested} icon(s) saved successfully. ${response.failureCount} failed.`,
        life: 5000
      });
      this.loadSvgIcons(0);
    } else {
      this.batchErrorMessage = `Failed to save ${response.failureCount} icon(s). Please fix the errors and try again.`;
    }
  }

  private validateSvgInput(): string | null {
    if (!this.svgContent.trim()) {
      return this.ERROR_MESSAGES.NO_CONTENT;
    }

    if (!this.svgName.trim()) {
      return this.ERROR_MESSAGES.NO_NAME;
    }

    if (!this.ICON_NAME_PATTERN.test(this.svgName)) {
      return this.ERROR_MESSAGES.INVALID_NAME;
    }

    if (this.svgName.length > this.MAX_ICON_NAME_LENGTH) {
      return this.ERROR_MESSAGES.NAME_TOO_LONG;
    }

    if (!this.svgContent.trim().includes('<svg')) {
      return this.ERROR_MESSAGES.INVALID_SVG;
    }

    if (this.svgContent.length > this.MAX_SVG_SIZE) {
      return this.ERROR_MESSAGES.SVG_TOO_LARGE;
    }

    return null;
  }

  private handleSuccessfulBatchSave(): void {
    this.messageService.add({
      severity: 'success',
      summary: 'Icons Saved',
      detail: `${this.svgEntries.length} SVG icon(s) saved successfully.`,
      life: 3000
    });

    this.activeTabIndex = '1';
    this.loadSvgIcons(0);
    this.clearAllEntries();
    this.resetSvgForm();
  }

  private resetSvgForm(): void {
    this.svgSearchText = '';
    this.svgContent = '';
    this.svgName = '';
    this.svgPreview = null;
  }

  onSvgIconDragStart(iconName: string): void {
    this.draggedSvgIcon = iconName;
  }

  onSvgIconDragEnd(): void {
    this.draggedSvgIcon = null;
    this.isTrashHover = false;
  }

  onTrashDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isTrashHover = true;
  }

  onTrashDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.isTrashHover = false;
  }

  onTrashDrop(event: DragEvent): void {
    event.preventDefault();
    this.isTrashHover = false;

    if (!this.draggedSvgIcon) {
      return;
    }

    this.deleteSvgIcon(this.draggedSvgIcon);
    this.draggedSvgIcon = null;
  }

  private deleteSvgIcon(iconName: string): void {
    this.isLoadingSvgIcons = true;

    this.iconService.deleteSvgIcon(iconName).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Icon Deleted',
          detail: 'SVG icon deleted successfully.',
          life: 2500
        });
        this.loadSvgIcons(this.currentSvgPage);
      },
      error: (error) => {
        this.isLoadingSvgIcons = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Delete Failed',
          detail: error.error?.message || this.ERROR_MESSAGES.DELETE_ERROR,
          life: 4000
        });
      }
    });
  }

  get canManageIcons(): boolean {
    const user = this.userService.getCurrentUser();
    return user?.permissions.canManageIcons || user?.permissions.admin || false;
  }
}
