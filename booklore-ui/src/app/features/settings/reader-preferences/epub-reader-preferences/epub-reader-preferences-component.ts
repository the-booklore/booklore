import {Component, inject, Input} from '@angular/core';
import {Button} from 'primeng/button';
import {FormsModule} from '@angular/forms';
import {ReaderPreferencesService} from '../reader-preferences-service';
import {UserSettings} from '../../user-management/user.service';
import {Tooltip} from 'primeng/tooltip';

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
export class EpubReaderPreferencesComponent {

  @Input() userSettings!: UserSettings;

  private readonly readerPreferencesService = inject(ReaderPreferencesService);

  readonly fonts = [
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
    return this.userSettings.epubReaderSetting.font;
  }

  set selectedFont(value: string | null) {
    if (typeof value === "string") {
      this.userSettings.epubReaderSetting.font = value;
    }
    this.readerPreferencesService.updatePreference(['epubReaderSetting', 'font'], value);
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
}
