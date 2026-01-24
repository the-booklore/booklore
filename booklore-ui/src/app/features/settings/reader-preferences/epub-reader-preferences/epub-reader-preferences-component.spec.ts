import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EpubReaderPreferencesComponent} from './epub-reader-preferences-component';
import {CustomThemeData, UserSettings} from '../../user-management/user.service';
import {ReaderPreferencesService} from '../reader-preferences.service';
import {CustomFontService} from '../../../../shared/service/custom-font.service';
import {ConfirmationService} from 'primeng/api';
import {of, Subject} from 'rxjs';

describe('EpubReaderPreferencesComponent - Custom Themes', () => {
  let component: EpubReaderPreferencesComponent;
  let mockUserSettings: UserSettings;
  let mockReaderPreferencesService: any;
  let mockConfirmationService: any;
  let mockCustomFontService: any;

  beforeEach(async () => {
    mockReaderPreferencesService = {
      updatePreference: vi.fn()
    };

    const requireConfirmation$ = new Subject<any>();
    mockConfirmationService = {
      confirm: vi.fn((options: any) => {
        if (options.accept) {
          options.accept();
        }
      }),
      requireConfirmation$,
      accept: new Subject<any>(),
      onAccept: new Subject<any>()
    };

    mockCustomFontService = {
      fonts$: of([]),
      getUserFonts: vi.fn(() => of([]))
    };

    mockUserSettings = {
      perBookSetting: {pdf: '', epub: '', cbx: ''},
      pdfReaderSetting: {pageSpread: 'off', pageZoom: '', showSidebar: false},
      epubReaderSetting: {theme: '', font: '', fontSize: 1, flow: '', spread: '', lineHeight: 1, margin: 1, letterSpacing: 1},
      ebookReaderSetting: {
        lineHeight: 1.5,
        justify: false,
        hyphenate: false,
        maxColumnCount: 2,
        gap: 20,
        fontSize: 16,
        theme: 'default',
        maxInlineSize: 720,
        maxBlockSize: 1440,
        fontFamily: '',
        isDark: false,
        flow: 'paginated'
      },
      cbxReaderSetting: {} as any,
      newPdfReaderSetting: {} as any,
      sidebarLibrarySorting: {field: '', order: ''},
      sidebarShelfSorting: {field: '', order: ''},
      sidebarMagicShelfSorting: {field: '', order: ''},
      filterMode: 'and',
      filterSortingMode: 'alphabetical',
      metadataCenterViewMode: 'route',
      enableSeriesView: true,
      entityViewPreferences: {
        global: {sortKey: '', sortDir: 'ASC', view: 'GRID', coverSize: 1, seriesCollapsed: false, overlayBookType: false},
        overrides: []
      },
      koReaderEnabled: false,
      autoSaveMetadata: false,
      customThemes: []
    };

    await TestBed.configureTestingModule({
      imports: [EpubReaderPreferencesComponent],
      providers: [
        {provide: ReaderPreferencesService, useValue: mockReaderPreferencesService},
        {provide: CustomFontService, useValue: mockCustomFontService}
      ]
    })
      .overrideComponent(EpubReaderPreferencesComponent, {
        set: {
          providers: [
            {provide: ConfirmationService, useValue: mockConfirmationService}
          ]
        }
      })
      .compileComponents();

    const fixture = TestBed.createComponent(EpubReaderPreferencesComponent);
    component = fixture.componentInstance;
    component.userSettings = mockUserSettings;
  });

  describe('isValidHexColor', () => {
    it('should return true for valid 6-digit hex color', () => {
      expect(component.isValidHexColor('#ffffff')).toBe(true);
      expect(component.isValidHexColor('#000000')).toBe(true);
      expect(component.isValidHexColor('#1a2b3c')).toBe(true);
      expect(component.isValidHexColor('#AABBCC')).toBe(true);
    });

    it('should return true for valid 3-digit hex color', () => {
      expect(component.isValidHexColor('#fff')).toBe(true);
      expect(component.isValidHexColor('#000')).toBe(true);
      expect(component.isValidHexColor('#abc')).toBe(true);
      expect(component.isValidHexColor('#ABC')).toBe(true);
    });

    it('should return false for invalid hex colors', () => {
      expect(component.isValidHexColor('')).toBe(false);
      expect(component.isValidHexColor('ffffff')).toBe(false);
      expect(component.isValidHexColor('#fffff')).toBe(false);
      expect(component.isValidHexColor('#fffffff')).toBe(false);
      expect(component.isValidHexColor('#gggggg')).toBe(false);
      expect(component.isValidHexColor('rgb(255,255,255)')).toBe(false);
      expect(component.isValidHexColor('#ff')).toBe(false);
    });

    it('should return false for null/undefined', () => {
      expect(component.isValidHexColor(null as any)).toBe(false);
      expect(component.isValidHexColor(undefined as any)).toBe(false);
    });
  });

  describe('isFormValid', () => {
    beforeEach(() => {
      component.customThemeForm = {
        id: '',
        name: '',
        label: 'My Theme',
        light: {fg: '#000000', bg: '#ffffff', link: '#0066cc'},
        dark: {fg: '#e0e0e0', bg: '#222222', link: '#77bbee'}
      };
    });

    it('should return true when all fields are valid', () => {
      expect(component.isFormValid()).toBe(true);
    });

    it('should return false when label is empty', () => {
      component.customThemeForm.label = '';
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when label is only whitespace', () => {
      component.customThemeForm.label = '   ';
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when light.fg is invalid', () => {
      component.customThemeForm.light.fg = 'invalid';
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when light.bg is invalid', () => {
      component.customThemeForm.light.bg = '';
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when light.link is invalid', () => {
      component.customThemeForm.light.link = '#gg0000';
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when dark.fg is invalid', () => {
      component.customThemeForm.dark.fg = 'rgb(0,0,0)';
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when dark.bg is invalid', () => {
      component.customThemeForm.dark.bg = '#12345';
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when dark.link is invalid', () => {
      component.customThemeForm.dark.link = 'blue';
      expect(component.isFormValid()).toBe(false);
    });
  });

  describe('customThemesList', () => {
    it('should return empty array when customThemes is undefined', () => {
      component.userSettings.customThemes = undefined;
      expect(component.customThemesList).toEqual([]);
    });

    it('should return empty array when customThemes is empty', () => {
      component.userSettings.customThemes = [];
      expect(component.customThemesList).toEqual([]);
    });

    it('should return custom themes when they exist', () => {
      const themes: CustomThemeData[] = [
        {id: 'custom-1', name: 'theme-1', label: 'Theme 1', light: {fg: '#000', bg: '#fff', link: '#00f'}, dark: {fg: '#fff', bg: '#000', link: '#0ff'}}
      ];
      component.userSettings.customThemes = themes;
      expect(component.customThemesList).toEqual(themes);
    });
  });

  describe('getSelectedCustomTheme', () => {
    const customTheme: CustomThemeData = {
      id: 'custom-123',
      name: 'my-theme',
      label: 'My Theme',
      light: {fg: '#000000', bg: '#ffffff', link: '#0066cc'},
      dark: {fg: '#e0e0e0', bg: '#222222', link: '#77bbee'}
    };

    beforeEach(() => {
      component.userSettings.customThemes = [customTheme];
    });

    it('should return null when selected theme is not a custom theme', () => {
      component.userSettings.ebookReaderSetting.theme = 'default';
      expect(component.getSelectedCustomTheme()).toBeNull();
    });

    it('should return null when selected theme is a built-in theme', () => {
      component.userSettings.ebookReaderSetting.theme = 'sepia';
      expect(component.getSelectedCustomTheme()).toBeNull();
    });

    it('should return the custom theme when it is selected', () => {
      component.userSettings.ebookReaderSetting.theme = 'custom-123';
      expect(component.getSelectedCustomTheme()).toEqual(customTheme);
    });

    it('should return null when custom theme id does not exist', () => {
      component.userSettings.ebookReaderSetting.theme = 'custom-999';
      expect(component.getSelectedCustomTheme()).toBeNull();
    });

    it('should return null when selectedTheme is null', () => {
      component.userSettings.ebookReaderSetting.theme = null as any;
      expect(component.getSelectedCustomTheme()).toBeNull();
    });
  });

  describe('openCreateThemeDialog', () => {
    it('should set editingTheme to null', () => {
      component.editingTheme = {id: 'test'} as any;
      component.openCreateThemeDialog();
      expect(component.editingTheme).toBeNull();
    });

    it('should reset customThemeForm to empty values', () => {
      component.customThemeForm = {id: 'old', name: 'old', label: 'Old', light: {} as any, dark: {} as any};
      component.openCreateThemeDialog();
      expect(component.customThemeForm.id).toBe('');
      expect(component.customThemeForm.label).toBe('');
      expect(component.customThemeForm.light.fg).toBe('#000000');
      expect(component.customThemeForm.light.bg).toBe('#ffffff');
      expect(component.customThemeForm.dark.fg).toBe('#e0e0e0');
      expect(component.customThemeForm.dark.bg).toBe('#222222');
    });

    it('should show the dialog', () => {
      component.showCustomThemeDialog = false;
      component.openCreateThemeDialog();
      expect(component.showCustomThemeDialog).toBe(true);
    });
  });

  describe('openEditThemeDialog', () => {
    const existingTheme: CustomThemeData = {
      id: 'custom-123',
      name: 'my-theme',
      label: 'My Theme',
      light: {fg: '#111111', bg: '#eeeeee', link: '#0066cc'},
      dark: {fg: '#cccccc', bg: '#333333', link: '#77bbee'}
    };

    it('should set editingTheme to the provided theme', () => {
      component.openEditThemeDialog(existingTheme);
      expect(component.editingTheme).toEqual(existingTheme);
    });

    it('should populate customThemeForm with theme data', () => {
      component.openEditThemeDialog(existingTheme);
      expect(component.customThemeForm.id).toBe('custom-123');
      expect(component.customThemeForm.label).toBe('My Theme');
      expect(component.customThemeForm.light.fg).toBe('#111111');
      expect(component.customThemeForm.dark.bg).toBe('#333333');
    });

    it('should create a copy of light/dark objects (not reference)', () => {
      component.openEditThemeDialog(existingTheme);
      component.customThemeForm.light.fg = '#ffffff';
      expect(existingTheme.light.fg).toBe('#111111');
    });

    it('should show the dialog', () => {
      component.showCustomThemeDialog = false;
      component.openEditThemeDialog(existingTheme);
      expect(component.showCustomThemeDialog).toBe(true);
    });
  });

  describe('saveCustomTheme', () => {
    beforeEach(() => {
      component.customThemeForm = {
        id: '',
        name: '',
        label: 'New Theme',
        light: {fg: '#000000', bg: '#ffffff', link: '#0066cc'},
        dark: {fg: '#e0e0e0', bg: '#222222', link: '#77bbee'}
      };
      component.userSettings.customThemes = [];
    });

    it('should not save when label is empty', () => {
      component.customThemeForm.label = '';
      component.saveCustomTheme();
      expect(mockReaderPreferencesService.updatePreference).not.toHaveBeenCalled();
    });

    it('should not save when label is only whitespace', () => {
      component.customThemeForm.label = '   ';
      component.saveCustomTheme();
      expect(mockReaderPreferencesService.updatePreference).not.toHaveBeenCalled();
    });

    it('should create new theme with generated id', () => {
      component.editingTheme = null;
      component.saveCustomTheme();
      expect(mockReaderPreferencesService.updatePreference).toHaveBeenCalledWith(
        ['customThemes'],
        expect.arrayContaining([
          expect.objectContaining({
            label: 'New Theme',
            name: 'new-theme'
          })
        ])
      );
      const savedThemes = mockReaderPreferencesService.updatePreference.mock.calls[0][1];
      expect(savedThemes[0].id).toMatch(/^custom-[a-f0-9-]+$/);
    });

    it('should update existing theme when editingTheme is set', () => {
      const existingTheme: CustomThemeData = {
        id: 'custom-123',
        name: 'old-theme',
        label: 'Old Theme',
        light: {fg: '#000', bg: '#fff', link: '#00f'},
        dark: {fg: '#fff', bg: '#000', link: '#0ff'}
      };
      component.userSettings.customThemes = [existingTheme];
      component.editingTheme = existingTheme;
      component.customThemeForm = {
        id: 'custom-123',
        name: 'old-theme',
        label: 'Updated Theme',
        light: {fg: '#111111', bg: '#eeeeee', link: '#0066cc'},
        dark: {fg: '#cccccc', bg: '#333333', link: '#77bbee'}
      };

      component.saveCustomTheme();

      expect(mockReaderPreferencesService.updatePreference).toHaveBeenCalledWith(
        ['customThemes'],
        expect.arrayContaining([
          expect.objectContaining({
            id: 'custom-123',
            label: 'Updated Theme',
            name: 'updated-theme'
          })
        ])
      );
    });

    it('should close the dialog after saving', () => {
      component.showCustomThemeDialog = true;
      component.saveCustomTheme();
      expect(component.showCustomThemeDialog).toBe(false);
    });

    it('should generate name from label (lowercase, hyphenated)', () => {
      component.customThemeForm.label = 'My Custom Theme';
      component.saveCustomTheme();
      const savedThemes = mockReaderPreferencesService.updatePreference.mock.calls[0][1];
      expect(savedThemes[0].name).toBe('my-custom-theme');
    });
  });

  describe('deleteCustomTheme', () => {
    const theme1: CustomThemeData = {
      id: 'custom-1',
      name: 'theme-1',
      label: 'Theme 1',
      light: {fg: '#000', bg: '#fff', link: '#00f'},
      dark: {fg: '#fff', bg: '#000', link: '#0ff'}
    };
    const theme2: CustomThemeData = {
      id: 'custom-2',
      name: 'theme-2',
      label: 'Theme 2',
      light: {fg: '#111', bg: '#eee', link: '#00f'},
      dark: {fg: '#eee', bg: '#111', link: '#0ff'}
    };

    beforeEach(() => {
      component.userSettings.customThemes = [theme1, theme2];
    });

    it('should remove the specified theme', () => {
      component.deleteCustomTheme(theme1);
      expect(mockReaderPreferencesService.updatePreference).toHaveBeenCalledWith(
        ['customThemes'],
        [theme2]
      );
    });

    it('should update userSettings.customThemes', () => {
      component.deleteCustomTheme(theme1);
      expect(component.userSettings.customThemes).toEqual([theme2]);
    });

    it('should switch to default theme if deleted theme was selected', () => {
      component.userSettings.ebookReaderSetting.theme = 'custom-1';
      component.deleteCustomTheme(theme1);
      expect(component.userSettings.ebookReaderSetting.theme).toBe('default');
    });

    it('should not change selected theme if different theme was deleted', () => {
      component.userSettings.ebookReaderSetting.theme = 'custom-2';
      component.deleteCustomTheme(theme1);
      expect(component.userSettings.ebookReaderSetting.theme).toBe('custom-2');
    });

    it('should handle deleting from empty array gracefully', () => {
      component.userSettings.customThemes = [];
      expect(() => component.deleteCustomTheme(theme1)).not.toThrow();
    });
  });
});
