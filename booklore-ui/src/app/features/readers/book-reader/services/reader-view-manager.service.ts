import {Injectable} from '@angular/core';
import {defer, from, Observable, of, Subject, throwError, timer} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';

export interface ViewEvent {
  type: 'load' | 'relocate' | 'error';
  detail?: any;
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
  private view: any;
  private eventSubject = new Subject<ViewEvent>();
  private keydownHandler?: (event: KeyboardEvent) => void;

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

  getChapters(): { label: string; href: string }[] {
    if (!this.view?.book?.toc) return [];

    const flattenToc = (items: any[], result: any[] = []): any[] => {
      for (const item of items) {
        result.push(item);
        if (item.subitems?.length) {
          flattenToc(item.subitems, result);
        }
      }
      return result;
    };

    const flattened = flattenToc(this.view.book.toc);

    return flattened.map(item => ({
      label: item.label,
      href: item.href
    }));
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
      if (e.detail?.doc && this.keydownHandler) {
        e.detail.doc.addEventListener('keydown', this.keydownHandler);
      }
    });

    this.view.addEventListener('relocate', (e: any) => {
      this.eventSubject.next({type: 'relocate', detail: e.detail});
    });

    this.view.addEventListener('error', (e: any) => {
      this.eventSubject.next({type: 'error', detail: e.detail});
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
}
