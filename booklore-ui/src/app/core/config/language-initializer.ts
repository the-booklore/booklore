import {inject} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {firstValueFrom} from 'rxjs';
import {AVAILABLE_LANGS} from './transloco-loader';

export const LANG_STORAGE_KEY = 'booklore-lang';

function detectLanguage(available: string[]): string {
  const saved = localStorage.getItem(LANG_STORAGE_KEY);
  if (saved && available.includes(saved)) {
    return saved;
  }

  const browserLocale = navigator.language;
  if (browserLocale && available.includes(browserLocale)) {
    return browserLocale;
  }

  const baseLang = browserLocale?.split('-')[0];
  if (baseLang && available.includes(baseLang)) {
    return baseLang;
  }

  return 'en';
}

export function initializeLanguage() {
  return () => {
    const translocoService = inject(TranslocoService);
    const lang = detectLanguage(AVAILABLE_LANGS);
    translocoService.setActiveLang(lang);
    return firstValueFrom(translocoService.load(lang));
  };
}
