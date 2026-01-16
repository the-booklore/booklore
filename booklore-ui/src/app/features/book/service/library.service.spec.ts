import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, of, throwError} from 'rxjs';

import {LibraryService} from './library.service';
import {BookService} from './book.service';
import {AuthService} from '../../../shared/service/auth.service';
import {Library} from '../model/library.model';

describe('LibraryService', () => {
  let service: LibraryService;
  let httpClientMock: any;
  let bookServiceMock: any;
  let authServiceMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn()
    };

    bookServiceMock = {
      removeBooksByLibraryId: vi.fn(),
      bookState$: of({
        books: [
          {id: 1, libraryId: 10},
          {id: 2, libraryId: 20},
          {id: 3, libraryId: 10},
          {id: 4}
        ]
      })
    };

    authServiceMock = {
      token$: of('token')
    };

    TestBed.configureTestingModule({
      providers: [
        LibraryService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: BookService, useValue: bookServiceMock},
        {provide: AuthService, useValue: authServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);

    service = runInInjectionContext(
      injector,
      () => TestBed.inject(LibraryService)
    );
  });

  it('should fetch libraries and update state', () => {
    const libraries: Library[] = [{id: 1, name: 'LibA', icon: 'icon', watch: true, paths: []}];
    httpClientMock.get.mockReturnValue(of(libraries));
    service['fetchLibraries']().subscribe(result => {
      expect(result).toEqual(libraries);
      expect(service['libraryStateSubject'].value.libraries).toEqual(libraries);
      expect(service['libraryStateSubject'].value.loaded).toBe(true);
    });
  });

  it('should handle fetch libraries error', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service['libraryStateSubject'].next({libraries: null, loaded: false, error: null});
    service['fetchLibraries']().subscribe({
      error: (err: any) => {
        expect(service['libraryStateSubject'].value.error).toBe('fail');
      }
    });
  });

  it('should create a library and update state', () => {
    const library: Library = {id: 2, name: 'LibB', icon: 'icon', watch: true, paths: []};
    httpClientMock.post.mockReturnValue(of(library));
    service['libraryStateSubject'].next({libraries: [], loaded: true, error: null});
    service.createLibrary(library).subscribe(result => {
      expect(result).toEqual(library);
      expect(service['libraryStateSubject'].value.libraries).toContain(library);
    });
  });

  it('should update a library and update state', () => {
    const library: Library = {id: 3, name: 'LibC', icon: 'icon', watch: true, paths: []};
    httpClientMock.put.mockReturnValue(of({...library, name: 'LibC2'}));
    service['libraryStateSubject'].next({libraries: [library], loaded: true, error: null});
    service.updateLibrary({...library, name: 'LibC2'}, 3).subscribe(result => {
      expect(result.name).toBe('LibC2');
      expect(service['libraryStateSubject'].value.libraries?.find(l => l.id === 3)?.name).toBe('LibC2');
    });
  });

  it('should delete a library and update state', () => {
    httpClientMock.delete.mockReturnValue(of(void 0));
    const library: Library = {id: 4, name: 'LibD', icon: 'icon', watch: true, paths: []};
    service['libraryStateSubject'].next({libraries: [library], loaded: true, error: null});
    service.deleteLibrary(4).subscribe(() => {
      expect(service['libraryStateSubject'].value.libraries).toEqual([]);
      expect(bookServiceMock.removeBooksByLibraryId).toHaveBeenCalledWith(4);
    });
  });

  it('should handle delete library error', () => {
    httpClientMock.delete.mockReturnValue(throwError(() => new Error('delete error')));
    service['libraryStateSubject'].next({libraries: [{id: 5, name: 'LibE', icon: 'icon', watch: true, paths: []}], loaded: true, error: null});
    service.deleteLibrary(5).subscribe({
      error: () => {
        expect(service['libraryStateSubject'].value.error).toBe('delete error');
      }
    });
  });

  it('should refresh a library and handle error', () => {
    httpClientMock.put.mockReturnValue(throwError(() => new Error('refresh error')));
    service['libraryStateSubject'].next({libraries: [{id: 6, name: 'LibF', icon: 'icon', watch: true, paths: []}], loaded: true, error: null});
    service.refreshLibrary(6).subscribe({
      error: () => {
        expect(service['libraryStateSubject'].value.error).toBe('refresh error');
      }
    });
  });

  it('should update library file naming pattern', () => {
    const library: Library = {id: 7, name: 'LibG', icon: 'icon', watch: true, paths: [], fileNamingPattern: 'old'};
    const updatedLibrary = {...library, fileNamingPattern: 'new'};
    httpClientMock.patch.mockReturnValue(of(updatedLibrary));
    service['libraryStateSubject'].next({libraries: [library], loaded: true, error: null});
    service.updateLibraryFileNamingPattern(7, 'new').subscribe(result => {
      expect(result.fileNamingPattern).toBe('new');
      expect(service['libraryStateSubject'].value.libraries?.find(l => l.id === 7)?.fileNamingPattern).toBe('new');
    });
  });

  it('should check if library exists by name', () => {
    const library: Library = {id: 8, name: 'LibH', icon: 'icon', watch: true, paths: []};
    service['libraryStateSubject'].next({libraries: [library], loaded: true, error: null});
    expect(service.doesLibraryExistByName('LibH')).toBe(true);
    expect(service.doesLibraryExistByName('NonExistent')).toBe(false);
  });

  it('should find library by id', () => {
    const library: Library = {id: 9, name: 'LibI', icon: 'icon', watch: true, paths: []};
    service['libraryStateSubject'].next({libraries: [library], loaded: true, error: null});
    expect(service.findLibraryById(9)).toEqual(library);
    expect(service.findLibraryById(999)).toBeUndefined();
  });

  it('should get libraries from state', () => {
    const libraries: Library[] = [
      {id: 10, name: 'LibJ', icon: 'icon', watch: true, paths: []}
    ];
    service['libraryStateSubject'].next({libraries, loaded: true, error: null});
    expect(service.getLibrariesFromState()).toEqual(libraries);
  });

  it('should get book count for library', async () => {
    const count = await firstValueFrom(service.getBookCount(10));
    expect(count).toBe(2);
  });
});
