import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError, distinctUntilChanged, finalize, map, shareReplay, tap} from 'rxjs/operators';

import {Library} from '../model/library.model';
import {LibraryState} from '../model/state/library-state.model';
import {BookService} from './book.service';
import {API_CONFIG} from '../../../core/config/api-config';
import {AuthService} from '../../../shared/service/auth.service';

@Injectable({providedIn: 'root'})
export class LibraryService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/libraries`;
  private http = inject(HttpClient);
  private bookService = inject(BookService);
  private authService = inject(AuthService);

  private libraryStateSubject = new BehaviorSubject<LibraryState>({
    libraries: null,
    loaded: false,
    error: null,
  });

  private loading$: Observable<Library[]> | null = null;

  constructor() {
    this.authService.token$.pipe(
      distinctUntilChanged()
    ).subscribe(token => {
      if (token === null) {
        this.libraryStateSubject.next({
          libraries: null,
          loaded: true,
          error: null,
        });
        this.loading$ = null;
      } else {
        const current = this.libraryStateSubject.value;
        if (current.loaded && !current.libraries) {
          this.libraryStateSubject.next({
            libraries: null,
            loaded: false,
            error: null,
          });
          this.loading$ = null;
        }
      }
    });
  }

  libraryState$ = this.libraryStateSubject.asObservable().pipe(
    tap(state => {
      if (!state.loaded && !state.error && !this.loading$) {
        this.loading$ = this.fetchLibraries().pipe(
          shareReplay(1),
          finalize(() => (this.loading$ = null))
        );
        this.loading$.subscribe();
      }
    })
  );

  private fetchLibraries(): Observable<Library[]> {
    return this.http.get<Library[]>(this.url).pipe(
      tap(libs => this.libraryStateSubject.next({libraries: libs, loaded: true, error: null})),
      catchError(err => {
        const current = this.libraryStateSubject.value;
        this.libraryStateSubject.next({libraries: current.libraries, loaded: true, error: err.message});
        throw err;
      })
    );
  }

  createLibrary(lib: Library): Observable<Library> {
    return this.http.post<Library>(this.url, lib).pipe(
      map(created => {
        const curr = this.libraryStateSubject.value;
        const updated = curr.libraries ? [...curr.libraries, created] : [created];
        this.libraryStateSubject.next({...curr, libraries: updated});
        return created;
      })
    );
  }

  updateLibrary(lib: Library, id?: number): Observable<Library> {
    return this.http.put<Library>(`${this.url}/${id}`, lib).pipe(
      map(updated => {
        const curr = this.libraryStateSubject.value;
        const list = curr.libraries?.map(l => (l.id === updated.id ? updated : l)) || [updated];
        this.libraryStateSubject.next({...curr, libraries: list});
        return updated;
      })
    );
  }

  deleteLibrary(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`).pipe(
      tap(() => {
        this.bookService.removeBooksByLibraryId(id);
        const curr = this.libraryStateSubject.value;
        const filtered = curr.libraries?.filter(l => l.id !== id) || [];
        this.libraryStateSubject.next({...curr, libraries: filtered});
      }),
      catchError(err => {
        const curr = this.libraryStateSubject.value;
        this.libraryStateSubject.next({...curr, error: err.message});
        return of();
      })
    );
  }

  refreshLibrary(id: number): Observable<void> {
    return this.http.put<void>(`${this.url}/${id}/refresh`, {}).pipe(
      catchError(err => {
        const curr = this.libraryStateSubject.value;
        this.libraryStateSubject.next({...curr, error: err.message});
        throw err;
      })
    );
  }

  updateLibraryFileNamingPattern(id: number, pattern: string): Observable<Library> {
    return this.http
      .patch<Library>(`${this.url}/${id}/file-naming-pattern`, {fileNamingPattern: pattern})
      .pipe(
        map(updated => {
          const curr = this.libraryStateSubject.value;
          const list = curr.libraries?.map(l => (l.id === updated.id ? updated : l)) || [updated];
          this.libraryStateSubject.next({...curr, libraries: list});
          return updated;
        })
      );
  }


  doesLibraryExistByName(name: string): boolean {
    return (this.libraryStateSubject.value.libraries || []).some(l => l.name === name);
  }

  findLibraryById(id: number): Library | undefined {
    return this.libraryStateSubject.value.libraries?.find(l => l.id === id);
  }

  getLibrariesFromState(): Library[] {
    return this.libraryStateSubject.value.libraries || [];
  }

  getLibraryPathById(pathId: number): string | undefined {
    return this.libraryStateSubject.value.libraries
      ?.find(lib => lib.paths.some(p => p.id === pathId))
      ?.paths.find(p => p.id === pathId)?.path;
  }

  getBookCount(libraryId: number): Observable<number> {
    return this.bookService.bookState$.pipe(
      map(state => (state.books || []).filter(b => b.libraryId === libraryId).length)
    );
  }
}
