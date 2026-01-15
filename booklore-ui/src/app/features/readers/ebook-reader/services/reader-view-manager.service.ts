import {Injectable} from '@angular/core';
import {defer, from, Observable, of, Subject, throwError, timer} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';
import {PageInfo, ReaderHeaderFooterUtil, ThemeInfo} from '../utils/reader-header-footer.util';

export interface ViewEvent {
  type: 'load' | 'relocate' | 'error' | 'middle-single-tap';
  detail?: any;
}

interface TocItem {
  label: string;
  href: string;
  subitems?: TocItem[];
}

export interface BookMetadata {
  title?: string;
  authors?: string[];
  language?: string;
  publisher?: string;
  description?: string;
  identifier?: string;
  coverUrl?: string;

  [key: string]: any;
}

@Injectable({
  providedIn: 'root'
})
export class ReaderViewManagerService {
  private readonly DOUBLE_CLICK_INTERVAL_MS = 300;
  private readonly LONG_HOLD_THRESHOLD_MS = 500;
  private readonly LEFT_ZONE_PERCENT = 0.3;
  private readonly RIGHT_ZONE_PERCENT = 0.7;

  private view: any;
  private isNavigating = false;
  private lastClickTime = 0;
  private lastClickZone: 'left' | 'middle' | 'right' | null = null;
  private longHoldTimeout: ReturnType<typeof setTimeout> | null = null;
  private keydownHandler?: (event: KeyboardEvent) => void;
  private clickedDocs = new WeakSet<Document>();

  private eventSubject = new Subject<ViewEvent>();
  public events$ = this.eventSubject.asObservable();

  createView(container: HTMLElement): void {
    this.view = document.createElement('foliate-view');
    this.view.style.width = '100%';
    this.view.style.height = '100%';
    this.view.style.display = 'block';
    container.appendChild(this.view);

    this.attachEventListeners();
    this.attachKeyboardHandler();
  }

  loadEpub(epubPath: string): Observable<void> {
    if (!this.view) {
      return throwError(() => new Error('View not created'));
    }

    return timer(100).pipe(
      switchMap(() => from(fetch(epubPath))),
      switchMap(response => {
        if (!response.ok) {
          throw new Error(`EPUB not found: ${response.status}`);
        }
        return from(response.blob());
      }),
      switchMap(blob => {
        const file = new File([blob], epubPath.split('/').pop() || 'book.epub', {
          type: 'application/epub+zip'
        });
        return from(this.view.open(file) as Promise<void>);
      }),
      map(() => undefined),
      catchError(err => throwError(() => err))
    );
  }

  destroy(): void {
    if (this.keydownHandler) {
      document.removeEventListener('keydown', this.keydownHandler);
      this.keydownHandler = undefined;
    }
    this.view?.remove();
    this.view = null;
  }

  goTo(target?: string | number | null): Observable<void> {
    const resolvedTarget = target ?? 0;
    if (!this.view) {
      return of(undefined);
    }
    return defer(() =>
      from(this.view.goTo(resolvedTarget) as Promise<void>)
    ).pipe(
      map(() => undefined)
    );
  }

  goToSection(index: number): Observable<void> {
    return this.goTo(index);
  }

  goToFraction(fraction: number): Observable<void> {
    if (!this.view) {
      return of(undefined);
    }
    return defer(() => from(this.view.goToFraction(fraction) as Promise<void>)).pipe(
      map(() => undefined)
    );
  }

  prev(): void {
    this.view?.prev();
  }

  next(): void {
    this.view?.next();
  }

  getRenderer(): any {
    return this.view?.renderer;
  }

  updateHeadersAndFooters(chapterName: string, pageInfo?: PageInfo, theme?: ThemeInfo): void {
    const renderer = this.getRenderer();
    ReaderHeaderFooterUtil.updateHeadersAndFooters(renderer, chapterName, pageInfo, theme);
  }

  getChapters(): TocItem[] {
    if (!this.view?.book?.toc) return [];

    const mapToc = (items: any[]): TocItem[] =>
      items.map(item => ({
        label: item.label,
        href: item.href,
        subitems: item.subitems?.length ? mapToc(item.subitems) : undefined
      }));

    return mapToc(this.view.book.toc);
  }

  getSectionFractions(): number[] {
    if (!this.view?.getSectionFractions) return [];
    return this.view.getSectionFractions();
  }

  getMetadata(): Observable<BookMetadata> {
    if (!this.view?.book?.metadata) {
      return of({});
    }

    const {metadata} = this.view.book;

    return this.getCoverUrl().pipe(
      map(coverUrl => ({
        title: metadata.title,
        authors: metadata.authors,
        language: metadata.language,
        publisher: metadata.publisher,
        description: metadata.description,
        identifier: metadata.identifier,
        coverUrl,
        ...metadata
      }))
    );
  }

  getCover(): Observable<Blob | null> {
    if (!this.view?.book?.getCover) {
      return of(null);
    }
    return defer(() => {
      const coverPromise = this.view.book.getCover();
      return coverPromise ? from(coverPromise as Promise<Blob | null>) : of(null);
    });
  }

  getCoverUrl(): Observable<string | null> {
    return this.getCover().pipe(
      map(blob => blob ? URL.createObjectURL(blob) : null)
    );
  }

  private attachEventListeners(): void {
    this.view.addEventListener('load', (e: any) => {
      this.eventSubject.next({type: 'load', detail: e.detail});
      if (e.detail?.doc) {
        if (this.keydownHandler) {
          e.detail.doc.addEventListener('keydown', this.keydownHandler);
        }
        this.attachIframeEventHandlers(e.detail.doc);
      }
    });

    this.view.addEventListener('relocate', (e: any) => {
      this.eventSubject.next({type: 'relocate', detail: e.detail});
    });

    this.view.addEventListener('error', (e: any) => {
      this.eventSubject.next({type: 'error', detail: e.detail});
    });

    window.addEventListener('message', (event) => {
      if (event.data?.type === 'iframe-click') {
        this.handleIframeClickMessage(event.data);
      }
    });
  }

  private attachKeyboardHandler(): void {
    this.keydownHandler = (event: KeyboardEvent) => {
      const k = event.key;
      if (k === 'ArrowLeft' || k === 'h' || k === 'PageUp') {
        this.prev();
        event.preventDefault();
      } else if (k === 'ArrowRight' || k === 'l' || k === 'PageDown') {
        this.next();
        event.preventDefault();
      }
    };
    document.addEventListener('keydown', this.keydownHandler);
  }

  private attachIframeEventHandlers(doc: Document): void {
    if (this.clickedDocs.has(doc)) {
      return;
    }
    this.clickedDocs.add(doc);

    doc.addEventListener('mousedown', (event: MouseEvent) => {
      this.longHoldTimeout = setTimeout(() => {
        this.longHoldTimeout = null;
      }, this.LONG_HOLD_THRESHOLD_MS);
    }, true);

    doc.addEventListener('click', (event: MouseEvent) => {
      const iframe = doc.defaultView?.frameElement as HTMLIFrameElement | null;
      if (!iframe) return;

      const iframeRect = iframe.getBoundingClientRect();
      const viewportX = iframeRect.left + event.clientX;
      const viewportY = iframeRect.top + event.clientY;

      window.postMessage({
        type: 'iframe-click',
        clientX: viewportX,
        clientY: viewportY,
        iframeLeft: iframeRect.left,
        iframeWidth: iframeRect.width,
        eventClientX: event.clientX,
        target: (event.target as HTMLElement)?.tagName
      }, '*');
    }, true);
  }

  private handleIframeClickMessage(data: any): void {
    const now = Date.now();
    const timeSinceLastClick = now - this.lastClickTime;

    const viewRect = this.view.getBoundingClientRect();
    const x = data.clientX - viewRect.left;
    const width = viewRect.width;

    const leftThreshold = width * this.LEFT_ZONE_PERCENT;
    const rightThreshold = width * this.RIGHT_ZONE_PERCENT;

    let currentZone: 'left' | 'middle' | 'right';
    if (x < leftThreshold) {
      currentZone = 'left';
    } else if (x > rightThreshold) {
      currentZone = 'right';
    } else {
      currentZone = 'middle';
    }

    if (timeSinceLastClick < this.DOUBLE_CLICK_INTERVAL_MS && this.lastClickZone === currentZone) {
      this.lastClickTime = now;
      this.lastClickZone = currentZone;

      if (currentZone !== 'middle') {
      }
      return;
    }

    this.lastClickTime = now;
    this.lastClickZone = currentZone;

    setTimeout(() => {
      if (Date.now() - this.lastClickTime >= this.DOUBLE_CLICK_INTERVAL_MS) {
        this.processIframeClick(data);
      }
    }, this.DOUBLE_CLICK_INTERVAL_MS);
  }

  private processIframeClick(data: any): void {
    if (!this.longHoldTimeout) {
      return;
    }

    if (this.isNavigating) {
      return;
    }

    const viewRect = this.view.getBoundingClientRect();
    const x = data.clientX - viewRect.left;
    const width = viewRect.width;

    const leftThreshold = width * this.LEFT_ZONE_PERCENT;
    const rightThreshold = width * this.RIGHT_ZONE_PERCENT;

    if (x < leftThreshold) {
      this.isNavigating = true;
      this.prev();
      setTimeout(() => this.isNavigating = false, 300);
    } else if (x > rightThreshold) {
      this.isNavigating = true;
      this.next();
      setTimeout(() => this.isNavigating = false, 300);
    } else {
      this.eventSubject.next({type: 'middle-single-tap'});
    }
  }
}
