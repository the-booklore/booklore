import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {IconService} from '../../services/icon.service';
import {DomSanitizer, SafeHtml} from '@angular/platform-browser';
import {UrlHelperService} from '../../service/url-helper.service';
import {MessageService} from 'primeng/api';
import {IconCategoriesHelper} from '../../helpers/icon-categories.helper';
import {Button} from 'primeng/button';
import {TabsModule} from 'primeng/tabs';

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
    SAVE_ERROR: 'Failed to save SVG. Please try again.',
    DELETE_ERROR: 'Failed to delete icon. Please try again.'
  };

  ref = inject(DynamicDialogRef);
  iconService = inject(IconService);
  sanitizer = inject(DomSanitizer);
  urlHelper = inject(UrlHelperService);
  messageService = inject(MessageService);

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
  isLoading: boolean = false;
  errorMessage: string = '';

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
      },
      error: () => {
        this.isLoadingSvgIcons = false;
        this.svgIconsError = this.ERROR_MESSAGES.LOAD_ICONS_ERROR;
      }
    });
  }

  getSvgIconUrl(iconName: string): string {
    return this.urlHelper.getIconUrl(iconName);
  }

  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor"%3E%3Ccircle cx="12" cy="12" r="10"/%3E%3Cline x1="15" y1="9" x2="9" y2="15"/%3E%3Cline x1="9" y1="9" x2="15" y2="15"/%3E%3C/svg%3E';
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

  saveSvg(): void {
    const validationError = this.validateSvgInput();
    if (validationError) {
      this.errorMessage = validationError;
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.iconService.saveSvgIcon(this.svgContent, this.svgName).subscribe({
      next: () => {
        this.isLoading = false;
        this.handleSuccessfulSave();
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error.error?.details?.join(', ')
          || error.error?.message
          || this.ERROR_MESSAGES.SAVE_ERROR;
      }
    });
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

  private handleSuccessfulSave(): void {
    this.activeTabIndex = '1';
    if (!this.svgIcons.includes(this.svgName)) {
      this.svgIcons.unshift(this.svgName);
    }
    this.selectedSvgIcon = this.svgName;
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
}
