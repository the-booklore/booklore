import {DOCUMENT, isPlatformBrowser} from '@angular/common';
import {effect, inject, Injectable, PLATFORM_ID, signal} from '@angular/core';
import {$t} from '@primeng/themes';
import Aura from '@primeng/themes/aura';
import {AppState} from '../model/app-state.model';
import {UrlHelperService} from '../../utilities/service/url-helper.service';

type ColorPalette = Record<string, string>;

interface Palette {
  name: string;
  palette: ColorPalette;
}

@Injectable({
  providedIn: 'root',
})
export class AppConfigService {
  public static readonly DEFAULT_BACKGROUND_BLUR = 20;
  public static readonly DEFAULT_SURFACE_ALPHA = 0.88;
  public static readonly DEFAULT_PRIMARY_COLOR = 'indigo';

  private readonly STORAGE_KEY = 'appConfigState';
  appState = signal<AppState>({});
  document = inject(DOCUMENT);
  platformId = inject(PLATFORM_ID);
  private readonly urlHelper = inject(UrlHelperService);
  private initialized = false;

  readonly surfaces: Palette[] = [
    {
      name: 'slate',
      palette: {
        0: '#ffffff',
        50: '#f8fafc',
        100: '#f1f5f9',
        200: '#e2e8f0',
        300: '#cbd5e1',
        400: '#94a3b8',
        500: '#64748b',
        600: '#475569',
        700: '#334155',
        800: '#1e293b',
        900: '#0f172a',
        950: '#020617'
      }
    },
    {
      name: 'gray',
      palette: {
        0: '#ffffff',
        50: '#fafafa',
        100: '#f4f4f5',
        200: '#e4e4e7',
        300: '#d4d4d8',
        400: '#a1a1aa',
        500: '#71717a',
        600: '#52525b',
        700: '#3f3f46',
        800: '#27272a',
        900: '#18181b',
        950: '#09090b'
      }
    },
    {
      name: 'neutral',
      palette: {
        0: '#ffffff',
        50: '#fafafa',
        100: '#f5f5f5',
        200: '#e5e5e5',
        300: '#d4d4d4',
        400: '#a3a3a3',
        500: '#737373',
        600: '#525252',
        700: '#404040',
        800: '#262626',
        900: '#171717',
        950: '#0a0a0a'
      }
    },
    {
      name: 'zinc',
      palette: {
        0: '#ffffff',
        50: '#fafafa',
        100: '#f4f4f5',
        200: '#e4e4e7',
        300: '#d4d4d8',
        400: '#a1a1aa',
        500: '#71717a',
        600: '#52525b',
        700: '#3f3f46',
        800: '#27272a',
        900: '#18181b',
        950: '#09090b'
      }
    },
    {
      name: 'stone',
      palette: {
        0: '#ffffff',
        50: '#fafaf9',
        100: '#f5f5f4',
        200: '#e7e5e4',
        300: '#d6d3d1',
        400: '#a8a29e',
        500: '#78716c',
        600: '#57534e',
        700: '#44403c',
        800: '#292524',
        900: '#1c1917',
        950: '#0c0a09'
      }
    },
    {
      name: 'soho',
      palette: {
        0: '#ffffff',
        50: '#ececec',
        100: '#dedfdf',
        200: '#c4c4c6',
        300: '#adaeb0',
        400: '#97979b',
        500: '#7f8084',
        600: '#6a6b70',
        700: '#55565b',
        800: '#3f4046',
        900: '#2c2c34',
        950: '#16161d'
      }
    },
    {
      name: 'viva',
      palette: {
        0: '#ffffff',
        50: '#f3f3f3',
        100: '#e7e7e8',
        200: '#cfd0d0',
        300: '#b7b8b9',
        400: '#9fa1a1',
        500: '#87898a',
        600: '#6e7173',
        700: '#565a5b',
        800: '#3e4244',
        900: '#262b2c',
        950: '#0e1315'
      }
    },
    {
      name: 'ocean',
      palette: {
        0: '#ffffff',
        50: '#fbfcfc',
        100: '#F7F9F8',
        200: '#EFF3F2',
        300: '#DADEDD',
        400: '#B1B7B6',
        500: '#828787',
        600: '#5F7274',
        700: '#415B61',
        800: '#29444E',
        900: '#183240',
        950: '#0c1920'
      }
    }
  ];

  constructor() {
    const initialState = this.loadAppState();
    this.appState.set(initialState);
    this.document.documentElement.classList.add('p-dark');

    if (isPlatformBrowser(this.platformId)) {
      this.setBackendImage();
      setTimeout(() => {
        this.onPresetChange();
        this.initialized = true;
      }, 0);
    }

    effect(() => {
      const state = this.appState();
      if (!this.initialized || !state) {
        return;
      }
      this.saveAppState(state);
      this.onPresetChange();
    }, {allowSignalWrites: true});
  }

  private setBackendImage(): void {
    const backendUrl = this.urlHelper.getBackgroundImageUrl(Date.now());
    this.appState.update(state => ({
      ...state,
      backgroundImage: backendUrl,
      lastUpdated: Date.now()
    }));
  }

  refreshBackgroundImage(): void {
    const timestamp = Date.now();
    const backendUrl = this.urlHelper.getBackgroundImageUrl(timestamp);
    this.appState.update(state => ({
      ...state,
      backgroundImage: backendUrl,
      lastUpdated: timestamp
    }));
  }

  private loadAppState(): AppState {
    const defaultState: AppState = {
      preset: 'Aura',
      primary: AppConfigService.DEFAULT_PRIMARY_COLOR,
      surface: 'neutral',
      backgroundBlur: AppConfigService.DEFAULT_BACKGROUND_BLUR,
      showBackground: true,
      surfaceAlpha: AppConfigService.DEFAULT_SURFACE_ALPHA,
    };

    if (isPlatformBrowser(this.platformId)) {
      const storedState = localStorage.getItem(this.STORAGE_KEY);
      if (storedState) {
        try {
          const parsed = JSON.parse(storedState);
          return {
            preset: parsed.preset || defaultState.preset,
            primary: parsed.primary || defaultState.primary,
            surface: parsed.surface || defaultState.surface,
            backgroundBlur: parsed.backgroundBlur ?? defaultState.backgroundBlur,
            showBackground: parsed.showBackground ?? defaultState.showBackground,
            surfaceAlpha: parsed.surfaceAlpha ?? defaultState.surfaceAlpha,
          };
        } catch (error) {
          return defaultState;
        }
      }
    }
    return defaultState;
  }

  private saveAppState(state: AppState): void {
    if (isPlatformBrowser(this.platformId)) {
      const {backgroundImage, lastUpdated, ...stateToSave} = state;
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(stateToSave));
    }
  }

  private getSurfacePalette(surface: string): ColorPalette {
    const palette = this.surfaces.find(s => s.name === surface)?.palette ?? {};
    const alpha = this.appState().surfaceAlpha ?? AppConfigService.DEFAULT_SURFACE_ALPHA;
    const transparentPalette: ColorPalette = {};

    // Text/content colors that should remain opaque (not transparent)
    const opaqueKeys = ['0', '50', '100', '200', '300', '400'];

    Object.entries(palette).forEach(([key, hex]) => {
      if (opaqueKeys.includes(key)) {
        // Keep text colors opaque
        transparentPalette[key] = hex;
      } else {
        // Apply transparency to background colors (500-950)
        transparentPalette[key] = this.hexToRgba(hex, alpha);
      }
    });

    return transparentPalette;
  }

  getPresetExt(): object {
    const surfacePalette = this.getSurfacePalette(this.appState().surface ?? 'neutral');
    const primaryName = this.appState().primary ?? AppConfigService.DEFAULT_PRIMARY_COLOR;
    const presetPalette = (Aura.primitive ?? {}) as Record<string, ColorPalette>;
    const color = presetPalette[primaryName] ?? {};

    if (primaryName === 'noir') {
      return {
        semantic: {
          primary: {...surfacePalette},
          colorScheme: {
            dark: {
              primary: {
                color: '{primary.50}',
                contrastColor: '{primary.950}',
                hoverColor: '{primary.200}',
                activeColor: '{primary.300}'
              },
              highlight: {
                background: '{primary.50}',
                focusBackground: '{primary.300}',
                color: '{primary.950}',
                focusColor: '{primary.950}'
              }
            }
          }
        }
      };
    }

    return {
      semantic: {
        primary: color,
        colorScheme: {
          dark: {
            primary: {
              color: '{primary.400}',
              contrastColor: '{surface.900}',
              hoverColor: '{primary.300}',
              activeColor: '{primary.200}'
            },
            highlight: {
              background: 'color-mix(in srgb, {primary.400}, transparent 84%)',
              focusBackground: 'color-mix(in srgb, {primary.400}, transparent 76%)',
              color: 'rgba(255,255,255,.88)',
              focusColor: 'rgba(255,255,255,.88)'
            }
          }
        }
      }
    };
  }

  private hexToRgba(hex: string, alpha: number = AppConfigService.DEFAULT_SURFACE_ALPHA): string {
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  }

  onPresetChange(): void {
    const surfacePalette = this.getSurfacePalette(this.appState().surface ?? 'neutral');
    const preset = this.getPresetExt();
    $t().preset(Aura).preset(preset).surfacePalette(surfacePalette).use({useDefaultOptions: true});
  }

  updateBackgroundBlur(blur: number): void {
    this.appState.update(state => ({
      ...state,
      backgroundBlur: blur
    }));
  }

  updateSurfaceAlpha(alpha: number): void {
    this.appState.update(state => ({
      ...state,
      surfaceAlpha: alpha
    }));
  }
}
