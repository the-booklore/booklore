import {Injectable} from '@angular/core';
import {Translation, TranslocoLoader} from '@jsverse/transloco';
import {from, of, Observable} from 'rxjs';
import en from '../../../i18n/en';

export const EN_TRANSLATIONS: Translation = en;

// To add a new language: create src/i18n/<lang>/ with domain JSONs + index.ts, then add an entry here.
const LAZY_LANG_LOADERS: Record<string, () => Promise<{default: Translation}>> = {
  es: () => import('../../../i18n/es'),
  it: () => import('../../../i18n/it'),
  de: () => import('../../../i18n/de'),
  fr: () => import('../../../i18n/fr'),
  nl: () => import('../../../i18n/nl'),
  pl: () => import('../../../i18n/pl'),
  pt: () => import('../../../i18n/pt'),
  ru: () => import('../../../i18n/ru'),
};

export const AVAILABLE_LANGS = ['en', ...Object.keys(LAZY_LANG_LOADERS)];

export const LANG_LABELS: Record<string, string> = {
  en: 'English',
  es: 'Español',
  it: 'Italiano',
  de: 'Deutsch',
  fr: 'Français',
  nl: 'Nederlands',
  pl: 'Polski',
  pt: 'Português',
  ru: 'Русский',
};

@Injectable({providedIn: 'root'})
export class TranslocoInlineLoader implements TranslocoLoader {
  getTranslation(lang: string): Observable<Translation> {
    if (lang === 'en') {
      return of(EN_TRANSLATIONS);
    }
    const loader = LAZY_LANG_LOADERS[lang];
    if (loader) {
      return from(loader().then(m => m.default));
    }
    return of(EN_TRANSLATIONS);
  }
}
