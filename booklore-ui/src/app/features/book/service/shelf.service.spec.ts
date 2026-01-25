import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, of, throwError} from 'rxjs';

import {ShelfService} from './shelf.service';
import {BookService} from './book.service';
import {UserService} from '../../settings/user-management/user.service';
import {Shelf} from '../model/shelf.model';

describe('ShelfService', () => {
  let service: ShelfService;
  let httpClientMock: any;
  let bookServiceMock: any;
  let userServiceMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn()
    };

    bookServiceMock = {
      removeBooksFromShelf: vi.fn(),
      bookState$: of({
        books: [
          {id: 1, shelves: [{id: 10}]},
          {id: 2, shelves: [{id: 20}]},
          {id: 3, shelves: []},
          {id: 4}
        ]
      })
    };

    userServiceMock = {
      userState$: of({user: {id: 1}})
    };

    TestBed.configureTestingModule({
      providers: [
        ShelfService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: BookService, useValue: bookServiceMock},
        {provide: UserService, useValue: userServiceMock},
        {provide: 'OAuthService', useValue: {}}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);

    service = runInInjectionContext(
      injector,
      () => TestBed.inject(ShelfService)
    );
  });

  it('should fetch shelves and update state', () => {
    const shelves: Shelf[] = [{id: 1, name: 'A', icon: 'icon'}];
    httpClientMock.get.mockReturnValue(of(shelves));
    service['fetchShelves']().subscribe(result => {
      expect(result).toEqual(shelves);
      expect(service['shelfStateSubject'].value.shelves).toEqual(shelves);
      expect(service['shelfStateSubject'].value.loaded).toBe(true);
    });
  });

  it('should handle fetch shelves error', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service['shelfStateSubject'].next({shelves: null, loaded: false, error: null});
    service['fetchShelves']().subscribe({
      error: (err: any) => {
        expect(service['shelfStateSubject'].value.error).toBe('fail');
      }
    });
  });

  it('should create a shelf and update state', () => {
    const shelf: Shelf = {id: 2, name: 'B', icon: 'icon'};
    httpClientMock.post.mockReturnValue(of(shelf));
    service['shelfStateSubject'].next({shelves: [], loaded: true, error: null});
    service.createShelf(shelf).subscribe(result => {
      expect(result).toEqual(shelf);
      expect(service['shelfStateSubject'].value.shelves).toContain(shelf);
    });
  });

  it('should update a shelf and update state', () => {
    const shelf: Shelf = {id: 3, name: 'C', icon: 'icon'};
    httpClientMock.put.mockReturnValue(of({...shelf, name: 'C2'}));
    service['shelfStateSubject'].next({shelves: [shelf], loaded: true, error: null});
    service.updateShelf({...shelf, name: 'C2'}, 3).subscribe(result => {
      expect(result.name).toBe('C2');
      expect(service['shelfStateSubject'].value.shelves?.find(s => s.id === 3)?.name).toBe('C2');
    });
  });

  it('should delete a shelf and update state', () => {
    httpClientMock.delete.mockReturnValue(of(void 0));
    const shelf: Shelf = {id: 4, name: 'D', icon: 'icon'};
    service['shelfStateSubject'].next({shelves: [shelf], loaded: true, error: null});
    service.deleteShelf(4).subscribe(() => {
      expect(service['shelfStateSubject'].value.shelves).toEqual([]);
      expect(bookServiceMock.removeBooksFromShelf).toHaveBeenCalledWith(4);
    });
  });

  it('should handle delete shelf error', () => {
    httpClientMock.delete.mockReturnValue(throwError(() => new Error('delete error')));
    service['shelfStateSubject'].next({shelves: [{id: 5, name: 'E', icon: 'icon'}], loaded: true, error: null});
    service.deleteShelf(5).subscribe({
      error: () => {
        expect(service['shelfStateSubject'].value.error).toBe('delete error');
      }
    });
  });

  it('should get shelf by id', () => {
    const shelf: Shelf = {id: 6, name: 'F', icon: 'icon'};
    service['shelfStateSubject'].next({shelves: [shelf], loaded: true, error: null});
    expect(service.getShelfById(6)).toEqual(shelf);
    expect(service.getShelfById(999)).toBeUndefined();
  });

  it('should get unshelved book count', async () => {
    const count = await firstValueFrom(service.getUnshelvedBookCount());
    expect(count).toBe(2);
  });
});
