import { Book, EpubCFI, Location, TocItem } from 'epubjs';

export const FALLBACK_EPUB_SETTINGS = {
  maxFontSize: 300,
  minFontSize: 50
};

export function flatten(chapters: TocItem[]): TocItem[] {
  if (!chapters || !Array.isArray(chapters)) return [];
  return chapters.flatMap((chapter: TocItem) => [chapter, ...flatten(chapter.subitems || [])]);
}

export function getCfiFromHref(book: Book, href: string): string | null {
  const [_, id] = href.split('#');
  const section = book.spine.get(href);

  if (!section || !section.document) {
    console.debug('Section or section.document is undefined for href:', href);
    return null;
  }

  const el = id ? section.document.getElementById(id) : section.document.body;
  if (!el) {
    console.debug('Element not found in section.document for href:', href);
    return null;
  }

  return section.cfiFromElement(el);
}

export function getChapter(book: Book, location: Location): TocItem | null {
  const locationHref = location.start.href;
  return flatten(book.navigation.toc || [])
    .filter((chapter: TocItem) => {
      return chapter.href && book.canonical && typeof book.canonical === 'function' &&
        book.canonical(chapter.href).includes(book.canonical(locationHref));
    })
    .reduce((result: TocItem | null, chapter: TocItem) => {
      if (!chapter.href) return result;
      const chapterCfi = getCfiFromHref(book, chapter.href);
      if (!chapterCfi) {
        return result;
      }
      const locationAfterChapter = (EpubCFI.prototype as any).compare(location.start.cfi, chapterCfi) > 0;
      return locationAfterChapter ? chapter : result;
    }, null);
}
