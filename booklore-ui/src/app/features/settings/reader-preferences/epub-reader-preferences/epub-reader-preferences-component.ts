import {Component, inject, Input, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {FormsModule} from '@angular/forms';
import {ReaderPreferencesService} from '../reader-preferences-service';
import {UserSettings} from '../../user-management/user.service';
import {Tooltip} from 'primeng/tooltip';
import {CustomFontService} from '../../../../shared/service/custom-font.service';
import {CustomFont} from '../../../../shared/model/custom-font.model';
import {firstValueFrom} from 'rxjs';

@Component({
  selector: 'app-epub-reader-preferences-component',
  imports: [
    Button,
    FormsModule,
    Tooltip
  ],
  templateUrl: './epub-reader-preferences-component.html',
  styleUrl: './epub-reader-preferences-component.scss'
})
export class EpubReaderPreferencesComponent implements OnInit {

  @Input() userSettings!: UserSettings;

  private readonly readerPreferencesService = inject(ReaderPreferencesService);
  private readonly customFontService = inject(CustomFontService);

  customFonts: CustomFont[] = [];

  fonts = [
    {name: 'Book Default', displayName: 'Default', key: null},
    {name: 'Serif', displayName: 'Aa', key: 'serif'},
    {name: 'Sans Serif', displayName: 'Aa', key: 'sans-serif'},
    {name: 'Roboto', displayName: 'Aa', key: 'roboto'},
    {name: 'Cursive', displayName: 'Aa', key: 'cursive'},
    {name: 'Monospace', displayName: 'Aa', key: 'monospace'}
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
    this.loadCustomFonts();
  }

  private async loadCustomFonts(): Promise<void> {
    try {
      // Fetch custom fonts from server
      const fonts = await firstValueFrom(this.customFontService.getUserFonts());
      this.customFonts = fonts;

      // Wait for all fonts to load into browser before allowing selection
      await this.customFontService.loadAllFonts(fonts);

      // Add to fonts array after fonts are loaded
      if (fonts.length > 0) {
        this.fonts.push({name: '--- Custom Fonts ---', displayName: '---', key: 'separator'});
        fonts.forEach(font => {
          this.fonts.push({
            name: font.fontName,
            displayName: font.fontName.substring(0, 12),
            key: `custom:${font.id}`
          });
        });
      }

      this.customFontsReady = true;
    } catch (err) {
      console.error('Failed to load custom fonts:', err);
      this.customFontsReady = true; // Allow UI to proceed even on error
    }
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

      this.userSettings.epubReaderSetting.customFontId = fontId;
      this.userSettings.epubReaderSetting.font = value;
      // Single API call for both customFontId and font
      this.readerPreferencesService.updatePreference(['epubReaderSetting', 'customFontId'], fontId);
    } else {
      // Clear customFontId for non-custom fonts
      this.userSettings.epubReaderSetting.customFontId = null;
      if (typeof value === "string") {
        this.userSettings.epubReaderSetting.font = value;
      }
      // Single API call for both customFontId and font
      this.readerPreferencesService.updatePreference(['epubReaderSetting', 'font'], value);
    }
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
