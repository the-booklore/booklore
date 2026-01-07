import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {UrlHelperService} from './url-helper.service';
import {AuthService} from './auth.service';
import {BookService} from '../../features/book/service/book.service';
import {Router, UrlTree} from '@angular/router';
import {Book} from '../../features/book/model/book.model';
import {CoverGeneratorComponent} from '../components/cover-generator/cover-generator.component';

describe('UrlHelperService', () => {
  let service: UrlHelperService;
  let authServiceMock: any;
  let bookServiceMock: any;
  let routerMock: any;

  beforeEach(() => {
    authServiceMock = {
      getOidcAccessToken: vi.fn(),
      getInternalAccessToken: vi.fn()
    };
    bookServiceMock = {
      getBookByIdFromState: vi.fn()
    };
    routerMock = {
      createUrlTree: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        UrlHelperService,
        {provide: AuthService, useValue: authServiceMock},
        {provide: BookService, useValue: bookServiceMock},
        {provide: Router, useValue: routerMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(UrlHelperService));
  });

  describe('Token handling', () => {
    it('should append token from OIDC', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue('oidc-token');
      authServiceMock.getInternalAccessToken.mockReturnValue(null);
      const url = (service as any).appendToken('http://test');
      expect(url).toContain('token=oidc-token');
    });

    it('should append token from internal', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue(null);
      authServiceMock.getInternalAccessToken.mockReturnValue('internal-token');
      const url = (service as any).appendToken('http://test');
      expect(url).toContain('token=internal-token');
    });

    it('should not append token if none present', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue(null);
      authServiceMock.getInternalAccessToken.mockReturnValue(null);
      const url = (service as any).appendToken('http://test');
      expect(url).toBe('http://test');
    });

    it('should append token with correct separator', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      expect((service as any).appendToken('http://a')).toBe('http://a?token=tok');
      expect((service as any).appendToken('http://a?x=1')).toBe('http://a?x=1&token=tok');
    });

    it('should handle empty string token gracefully', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue('');
      authServiceMock.getInternalAccessToken.mockReturnValue('');
      const url = (service as any).appendToken('http://test');
      expect(url).toBe('http://test');
    });

    it('should handle undefined url in appendToken', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      expect(() => (service as any).appendToken(undefined)).toThrow();
    });
  });

  describe('getThumbnailUrl', () => {
    it('should use CoverGenerator if no coverUpdatedOn and book/metadata present', () => {
      const book: Book = {id: 1, metadata: {title: 'T', authors: ['A']}} as any;
      bookServiceMock.getBookByIdFromState.mockReturnValue(book);
      const genSpy = vi.spyOn(CoverGeneratorComponent.prototype, 'generateCover').mockReturnValue('cover-url');
      const url = service.getThumbnailUrl(1);
      expect(genSpy).toHaveBeenCalled();
      expect(url).toBe('cover-url');
      genSpy.mockRestore();
    });

    it('should use API url if coverUpdatedOn present', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      const url = service.getThumbnailUrl(2, 'v123');
      expect(url).toMatch(/\/api\/v1\/media\/book\/2\/thumbnail\?v123.*token=tok/);
    });

    it('should use API url if no book in state', () => {
      bookServiceMock.getBookByIdFromState.mockReturnValue(undefined);
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      const url = service.getThumbnailUrl(3);
      expect(url).toMatch(/\/api\/v1\/media\/book\/3\/thumbnail\?token=tok/);
    });

    it('should handle book with no metadata', () => {
      const book: Book = {id: 1} as any;
      bookServiceMock.getBookByIdFromState.mockReturnValue(book);
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      const url = service.getThumbnailUrl(1);
      expect(url).toMatch(/\/api\/v1\/media\/book\/1\/thumbnail\?token=tok/);
    });

    it('should handle book with empty metadata', () => {
      const book: Book = {id: 1, metadata: {}} as any;
      bookServiceMock.getBookByIdFromState.mockReturnValue(book);
      const genSpy = vi.spyOn(CoverGeneratorComponent.prototype, 'generateCover').mockReturnValue('cover-url');
      const url = service.getThumbnailUrl(1);
      expect(genSpy).toHaveBeenCalled();
      expect(url).toBe('cover-url');
      genSpy.mockRestore();
    });

    it('should handle coverUpdatedOn with empty string', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      const url = service.getThumbnailUrl(2, '');
      expect(url).toMatch(/\/api\/v1\/media\/book\/2\/thumbnail\?token=tok/);
    });
  });

  describe('getThumbnailUrl1', () => {
    it('should always use API url and append token', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      const url = service.getThumbnailUrl1(4, 'v456');
      expect(url).toMatch(/\/api\/v1\/media\/book\/4\/thumbnail\?v456.*token=tok/);
    });

    it('should handle undefined coverUpdatedOn', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      const url = service.getThumbnailUrl1(4);
      expect(url).toMatch(/\/api\/v1\/media\/book\/4\/thumbnail\?token=tok/);
    });
  });

  describe('getCoverUrl', () => {
    it('should use CoverGenerator if no coverUpdatedOn and book/metadata present', () => {
      const book: Book = {id: 5, metadata: {title: 'T', authors: ['A']}} as any;
      bookServiceMock.getBookByIdFromState.mockReturnValue(book);
      const genSpy = vi.spyOn(CoverGeneratorComponent.prototype, 'generateCover').mockReturnValue('cover-url2');
      const url = service.getCoverUrl(5);
      expect(genSpy).toHaveBeenCalled();
      expect(url).toBe('cover-url2');
      genSpy.mockRestore();
    });

    it('should use API url if coverUpdatedOn present', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      const url = service.getCoverUrl(6, 'v789');
      expect(url).toMatch(/\/api\/v1\/media\/book\/6\/cover\?v789.*token=tok/);
    });

    it('should use API url if no book in state', () => {
      bookServiceMock.getBookByIdFromState.mockReturnValue(undefined);
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      const url = service.getCoverUrl(7);
      expect(url).toMatch(/\/api\/v1\/media\/book\/7\/cover\?token=tok/);
    });

    it('should handle book with no metadata', () => {
      const book: Book = {id: 5} as any;
      bookServiceMock.getBookByIdFromState.mockReturnValue(book);
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      const url = service.getCoverUrl(5);
      expect(url).toMatch(/\/api\/v1\/media\/book\/5\/cover\?token=tok/);
    });

    it('should handle book with empty metadata', () => {
      const book: Book = {id: 5, metadata: {}} as any;
      bookServiceMock.getBookByIdFromState.mockReturnValue(book);
      const genSpy = vi.spyOn(CoverGeneratorComponent.prototype, 'generateCover').mockReturnValue('cover-url2');
      const url = service.getCoverUrl(5);
      expect(genSpy).toHaveBeenCalled();
      expect(url).toBe('cover-url2');
      genSpy.mockRestore();
    });

    it('should handle coverUpdatedOn with empty string', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      const url = service.getCoverUrl(6, '');
      expect(url).toMatch(/\/api\/v1\/media\/book\/6\/cover\?token=tok/);
    });
  });

  describe('getBackupCoverUrl', () => {
    it('should return backup cover url with token', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      const url = service.getBackupCoverUrl(8);
      expect(url).toMatch(/\/api\/v1\/media\/book\/8\/backup-cover\?token=tok/);
    });

    it('should handle missing token', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue(null);
      authServiceMock.getInternalAccessToken.mockReturnValue(null);
      const url = service.getBackupCoverUrl(8);
      expect(url).toMatch(/\/api\/v1\/media\/book\/8\/backup-cover$/);
    });
  });

  describe('getBookdropCoverUrl', () => {
    it('should return bookdrop cover url with token', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue('tok');
      const url = service.getBookdropCoverUrl(9);
      expect(url).toMatch(/\/api\/v1\/media\/bookdrop\/9\/cover\?token=tok/);
    });

    it('should handle missing token', () => {
      authServiceMock.getOidcAccessToken.mockReturnValue(null);
      authServiceMock.getInternalAccessToken.mockReturnValue(null);
      const url = service.getBookdropCoverUrl(9);
      expect(url).toMatch(/\/api\/v1\/media\/bookdrop\/9\/cover$/);
    });
  });

  describe('getBookUrl', () => {
    it('should call router.createUrlTree with correct args', () => {
      const book: Book = {id: 10} as any;
      routerMock.createUrlTree.mockReturnValue('url-tree');
      const urlTree = service.getBookUrl(book);
      expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/book', 10], {queryParams: {tab: 'view'}});
      expect(urlTree).toBe('url-tree');
    });

    it('should handle book with undefined id', () => {
      routerMock.createUrlTree.mockReturnValue('url-tree');
      const minimalBook = {
        id: undefined,
        bookType: undefined,
        libraryId: undefined,
        libraryName: undefined
      } as any;
      const urlTree = service.getBookUrl(minimalBook);
      expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/book', undefined], {queryParams: {tab: 'view'}});
      expect(urlTree).toBe('url-tree');
    });
  });

  describe('filterBooksBy', () => {
    it('should route to /series if filterKey is series', () => {
      routerMock.createUrlTree.mockReturnValue('series-url');
      const urlTree = service.filterBooksBy('series', 'My Series');
      expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/series', encodeURIComponent('My Series')]);
      expect(urlTree).toBe('series-url');
    });

    it('should route to /all-books with correct queryParams for other filters', () => {
      routerMock.createUrlTree.mockReturnValue('all-books-url');
      const urlTree = service.filterBooksBy('author', 'Author Name');
      expect(routerMock.createUrlTree).toHaveBeenCalledWith(
        ['/all-books'],
        {
          queryParams: {
            view: 'grid',
            sort: 'title',
            direction: 'asc',
            sidebar: true,
            filter: 'author:' + encodeURIComponent('Author Name')
          }
        }
      );
      expect(urlTree).toBe('all-books-url');
    });

    it('should handle empty filterValue', () => {
      routerMock.createUrlTree.mockReturnValue('empty-filter-url');
      const urlTree = service.filterBooksBy('author', '');
      expect(routerMock.createUrlTree).toHaveBeenCalledWith(
        ['/all-books'],
        expect.objectContaining({
          queryParams: expect.objectContaining({
            filter: 'author:'
          })
        })
      );
      expect(urlTree).toBe('empty-filter-url');
    });

    it('should handle empty filterKey', () => {
      routerMock.createUrlTree.mockReturnValue('empty-key-url');
      const urlTree = service.filterBooksBy('', 'SomeValue');
      expect(routerMock.createUrlTree).toHaveBeenCalledWith(
        ['/all-books'],
        expect.objectContaining({
          queryParams: expect.objectContaining({
            filter: ':' + encodeURIComponent('SomeValue')
          })
        })
      );
      expect(urlTree).toBe('empty-key-url');
    });
  });
});

describe('UrlHelperService - API Contract Tests', () => {
  let service: UrlHelperService;
  let authServiceMock: any;
  let bookServiceMock: any;
  let routerMock: any;

  beforeEach(() => {
    authServiceMock = {
      getOidcAccessToken: vi.fn(),
      getInternalAccessToken: vi.fn()
    };
    bookServiceMock = {
      getBookByIdFromState: vi.fn()
    };
    routerMock = {
      createUrlTree: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        UrlHelperService,
        {provide: AuthService, useValue: authServiceMock},
        {provide: BookService, useValue: bookServiceMock},
        {provide: Router, useValue: routerMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(UrlHelperService));
  });

  it('should generate correct API endpoints for covers and thumbnails', () => {
    authServiceMock.getOidcAccessToken.mockReturnValue('tok');
    expect(service.getThumbnailUrl1(11)).toMatch(/\/api\/v1\/media\/book\/11\/thumbnail\?token=tok/);
    expect(service.getCoverUrl(12, 'v1')).toMatch(/\/api\/v1\/media\/book\/12\/cover\?v1.*token=tok/);
    expect(service.getBackupCoverUrl(13)).toMatch(/\/api\/v1\/media\/book\/13\/backup-cover\?token=tok/);
    expect(service.getBookdropCoverUrl(14)).toMatch(/\/api\/v1\/media\/bookdrop\/14\/cover\?token=tok/);
  });

  it('should encode filter values in filterBooksBy', () => {
    routerMock.createUrlTree.mockReturnValue('tree');
    service.filterBooksBy('author', 'A&B');
    expect(routerMock.createUrlTree).toHaveBeenCalledWith(
      ['/all-books'],
      expect.objectContaining({
        queryParams: expect.objectContaining({
          filter: 'author:' + encodeURIComponent('A&B')
        })
      })
    );
  });

  it('should always return a string or UrlTree from getBookUrl/filterBooksBy', () => {
    routerMock.createUrlTree.mockReturnValueOnce('tree1').mockReturnValueOnce({} as UrlTree);
    expect(typeof service.getBookUrl({id: 1} as any)).toMatch(/string|object/);
    expect(typeof service.filterBooksBy('series', 'X')).toMatch(/string|object/);
  });
});

