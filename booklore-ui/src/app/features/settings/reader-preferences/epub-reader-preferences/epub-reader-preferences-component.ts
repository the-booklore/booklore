import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {FormsModule} from '@angular/forms';
import {ReaderPreferencesService} from '../reader-preferences.service';
import {UserSettings} from '../../user-management/user.service';
import {Tooltip} from 'primeng/tooltip';
import {CustomFontService} from '../../../../shared/service/custom-font.service';
import {CustomFont} from '../../../../shared/model/custom-font.model';
import {skip, Subject, takeUntil} from 'rxjs';
import {addCustomFontsToDropdown} from '../../../../shared/util/custom-font.util';
import {Skeleton} from 'primeng/skeleton';

@Component({
  selector: 'app-epub-reader-preferences-component',
  imports: [
    Button,
    FormsModule,
    Tooltip,
    Skeleton
  ],
  templateUrl: './epub-reader-preferences-component.html',
  styleUrl: './epub-reader-preferences-component.scss'
})
export class EpubReaderPreferencesComponent implements OnInit, OnDestroy {

  @Input() userSettings!: UserSettings;

  private readonly readerPreferencesService = inject(ReaderPreferencesService);
  private readonly customFontService = inject(CustomFontService);
  private readonly destroy$ = new Subject<void>();

  customFonts: CustomFont[] = [];

  fonts = [
    {name: 'Book Default', displayName: 'Default', key: null},
    {name: 'Serif', displayName: 'Serif', key: 'serif'},
    {name: 'Sans Serif', displayName: 'Sans Serif', key: 'sans-serif'},
    {name: 'Roboto', displayName: 'Roboto', key: 'roboto'},
    {name: 'Cursive', displayName: 'Cursive', key: 'cursive'},
    {name: 'Monospace', displayName: 'Monospace', key: 'monospace'}
  ];

  readonly flowOptions = [
    {name: 'Paginated', key: 'paginated', icon: 'pi pi-book'},
    {name: 'Scrolled', key: 'scrolled', icon: 'pi pi-sort-alt'}
  ];

  readonly spreadOptions = [
    {name: 'Single', key: 'single', icon: 'pi pi-file'},
    {name: 'Double', key: 'double', icon: 'pi pi-copy'}
  ];

  readonly themes = [
    {name: 'White', key: 'white', color: '#FFFFFF'},
    {name: 'Black', key: 'black', color: '#1A1A1A'},
    {name: 'Grey', key: 'grey', color: '#4B5563'},
    {name: 'Sepia', key: 'sepia', color: '#F4ECD8'},
    {name: 'Green', key: 'green', color: '#D1FAE5'},
    {name: 'Lavender', key: 'lavender', color: '#E9D5FF'},
    {name: 'Cream', key: 'cream', color: '#FEF3C7'},
    {name: 'Light Blue', key: 'light-blue', color: '#DBEAFE'},
    {name: 'Peach', key: 'peach', color: '#FECACA'},
    {name: 'Mint', key: 'mint', color: '#A7F3D0'},
    {name: 'Dark Slate', key: 'dark-slate', color: '#1E293B'},
    {name: 'Dark Olive', key: 'dark-olive', color: '#3F3F2C'},
    {name: 'Dark Purple', key: 'dark-purple', color: '#3B2F4A'},
    {name: 'Dark Teal', key: 'dark-teal', color: '#0F3D3E'},
    {name: 'Dark Brown', key: 'dark-brown', color: '#3E2723'}
  ];

  customFontsReady = false;

  ngOnInit(): void {
    this.subscribeToFontUpdates();

    this.customFontService.getUserFonts().subscribe({
      next: () => {
        this.customFontsReady = true;
      },
      error: (err) => {
        console.error('Failed to load custom fonts:', err);
        this.customFontsReady = true;
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private subscribeToFontUpdates(): void {
    this.customFontService.fonts$
      .pipe(
        skip(1),
        takeUntil(this.destroy$)
      )
      .subscribe(async fonts => {
        try {
          const selectedFontDeleted = this.isCurrentlySelectedFontDeleted(fonts);

          if (this.hasCustomFontsChanged(fonts)) {
            this.customFonts = fonts;
            await this.customFontService.loadAllFonts(fonts);
            this.updateFontsDropdown(fonts);
          }

          if (selectedFontDeleted) {
            this.resetToDefaultFont();
          }
        } catch (err) {
          console.error('Failed to process custom fonts:', err);
        }
      });
  }

  private hasCustomFontsChanged(newFonts: CustomFont[]): boolean {
    if (newFonts.length !== this.customFonts.length) {
      return true;
    }
    const newIds = new Set(newFonts.map(f => f.id));
    const currentIds = new Set(this.customFonts.map(f => f.id));
    return newFonts.some(f => !currentIds.has(f.id)) || this.customFonts.some(f => !newIds.has(f.id));
  }

  private updateFontsDropdown(fonts: CustomFont[]): void {
    this.fonts = this.fonts.filter(font => !font.key || !font.key.startsWith('custom:'));
    addCustomFontsToDropdown(fonts, this.fonts, 'preference');
  }

  private isCurrentlySelectedFontDeleted(newFonts: CustomFont[]): boolean {
    const customFontId = this.userSettings.epubReaderSetting.customFontId;
    if (!customFontId) {
      return false;
    }

    const fontStillExists = newFonts.some(font => font.id === customFontId);
    return !fontStillExists;
  }

  private resetToDefaultFont(): void {
    console.log('Selected custom font was deleted, resetting to default font');
    this.selectedFont = null;
  }

  get selectedTheme(): string | null {
    return this.userSettings.epubReaderSetting.theme;
  }

  set selectedTheme(value: string | null) {
    if (typeof value === "string") {
      this.userSettings.epubReaderSetting.theme = value;
    }
    this.readerPreferencesService.updatePreference(['epubReaderSetting', 'theme'], value);
  }

  get selectedFont(): string | null {
    // If customFontId is set, return the custom font key
    if (this.userSettings.epubReaderSetting.customFontId) {
      return `custom:${this.userSettings.epubReaderSetting.customFontId}`;
    }
    return this.userSettings.epubReaderSetting.font;
  }

  set selectedFont(value: string | null) {
    // Handle custom fonts
    if (value && value.startsWith('custom:')) {
      const fontIdStr = value.split(':')[1];
      const fontId = parseInt(fontIdStr, 10);

      if (isNaN(fontId)) {
        console.error('Invalid custom font ID:', value);
        return;
      }

      // Update both fields in local state
      this.userSettings.epubReaderSetting.customFontId = fontId;
      this.userSettings.epubReaderSetting.font = value;
    } else {
      // Clear customFontId and set font in local state
      this.userSettings.epubReaderSetting.customFontId = null;
      if (typeof value === "string") {
        this.userSettings.epubReaderSetting.font = value;
      } else {
        // value is null - set to default font
        this.userSettings.epubReaderSetting.font = null as any;
      }
    }

    // Single API call with entire epubReaderSetting object
    this.readerPreferencesService.updatePreference(['epubReaderSetting'], this.userSettings.epubReaderSetting);
  }

  get selectedFlow(): string | null {
    return this.userSettings.epubReaderSetting.flow;
  }

  set selectedFlow(value: string | null) {
    if (typeof value === "string") {
      this.userSettings.epubReaderSetting.flow = value;
    }
    this.readerPreferencesService.updatePreference(['epubReaderSetting', 'flow'], value);
  }

  get selectedSpread(): string | null {
    return this.userSettings.epubReaderSetting.spread;
  }

  set selectedSpread(value: string | null) {
    if (typeof value === "string") {
      this.userSettings.epubReaderSetting.spread = value;
    }
    this.readerPreferencesService.updatePreference(['epubReaderSetting', 'spread'], value);
  }

  get fontSize(): number {
    return this.userSettings.epubReaderSetting.fontSize;
  }

  set fontSize(value: number) {
    this.userSettings.epubReaderSetting.fontSize = value;
    this.readerPreferencesService.updatePreference(['epubReaderSetting', 'fontSize'], value);
  }

  increaseFontSize() {
    if (this.fontSize < 250) {
      this.fontSize += 10;
    }
  }

  decreaseFontSize() {
    if (this.fontSize > 50) {
      this.fontSize -= 10;
    }
  }

  getCustomFontName(fontKey: string): string | null {
    if (!fontKey || !fontKey.startsWith('custom:')) {
      return null;
    }
    const fontId = parseInt(fontKey.split(':')[1]);
    const customFont = this.customFonts.find(f => f.id === fontId);
    return customFont ? customFont.fontName : null;
  }
}
