import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {CreateReadingSessionDto, PageableResponse, ReadingSessionApiService, ReadingSessionResponse} from './reading-session-api.service';
import {BookType} from '../../features/book/model/book.model';

describe('ReadingSessionApiService', () => {
  let service: ReadingSessionApiService;
  let httpClientMock: any;

  const BASE_URL = '/api/v1/reading-sessions';

  const mockSessionDto: CreateReadingSessionDto = {
    bookId: 1,
    bookType: 'PDF' as BookType,
    startTime: '2024-01-01T00:00:00Z',
    endTime: '2024-01-01T00:10:00Z',
    durationSeconds: 600,
    durationFormatted: '10m 0s',
    startProgress: 0,
    endProgress: 10,
    progressDelta: 10,
    startLocation: 'start',
    endLocation: 'end'
  };

  const mockPageableResponse: PageableResponse<ReadingSessionResponse> = {
    content: [{
      id: 1,
      bookId: 1,
      bookTitle: 'Book',
      bookType: 'PDF',
      startTime: '2024-01-01T00:00:00Z',
      endTime: '2024-01-01T00:10:00Z',
      durationSeconds: 600,
      startProgress: 0,
      endProgress: 10,
      progressDelta: 10,
      startLocation: 'start',
      endLocation: 'end',
      createdAt: '2024-01-01T00:10:01Z'
    }],
    pageable: {
      pageNumber: 0,
      pageSize: 5,
      sort: {empty: true, sorted: false, unsorted: true},
      offset: 0,
      paged: true,
      unpaged: false
    },
    totalElements: 1,
    last: true,
    totalPages: 1,
    numberOfElements: 1,
    first: true,
    size: 5,
    number: 0,
    sort: {empty: true, sorted: false, unsorted: true},
    empty: false
  };

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn(),
      get: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        ReadingSessionApiService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(ReadingSessionApiService));
  });

  it('should call POST to createSession', () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    service.createSession(mockSessionDto).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringContaining(BASE_URL),
        mockSessionDto
      );
    });
  });

  it('should call GET to getSessionsByBookId with correct params', () => {
    httpClientMock.get.mockReturnValue(of(mockPageableResponse));
    service.getSessionsByBookId(1, 2, 10).subscribe(resp => {
      expect(resp).toEqual(mockPageableResponse);
      expect(httpClientMock.get).toHaveBeenCalledWith(
        expect.stringContaining(`${BASE_URL}/book/1`),
        expect.objectContaining({
          params: expect.any(HttpParams)
        })
      );
      const params = httpClientMock.get.mock.calls[0][1].params;
      expect(params.get('page')).toBe('2');
      expect(params.get('size')).toBe('10');
    });
  });

  it('should default to page=0 and size=5 if not provided', () => {
    httpClientMock.get.mockReturnValue(of(mockPageableResponse));
    service.getSessionsByBookId(2).subscribe();
    const params = httpClientMock.get.mock.calls[0][1].params;
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('5');
  });

  it('should handle error in createSession', () => {
    httpClientMock.post.mockReturnValue(throwError(() => new Error('fail')));
    service.createSession(mockSessionDto).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle error in getSessionsByBookId', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getSessionsByBookId(1).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should send beacon successfully', () => {
    const sendBeaconMock = vi.fn().mockReturnValue(true);
    vi.stubGlobal('navigator', {sendBeacon: sendBeaconMock});
    const result = service.sendSessionBeacon(mockSessionDto);
    expect(sendBeaconMock).toHaveBeenCalledWith(
      expect.stringContaining(BASE_URL),
      expect.any(Blob)
    );
    expect(result).toBe(true);
  });

  it('should return false if sendBeacon throws', () => {
    vi.stubGlobal('navigator', {
      sendBeacon: vi.fn().mockImplementation(() => {
        throw new Error('fail');
      })
    });
    const result = service.sendSessionBeacon(mockSessionDto);
    expect(result).toBe(false);
  });
});

describe('ReadingSessionApiService - API Contract Tests', () => {
  let service: ReadingSessionApiService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn(),
      get: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        ReadingSessionApiService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(ReadingSessionApiService));
  });

  it('should call correct endpoint for createSession', () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    const dto: CreateReadingSessionDto = {
      bookId: 1,
      bookType: 'PDF',
      startTime: '2024-01-01T00:00:00Z',
      endTime: '2024-01-01T00:10:00Z',
      durationSeconds: 600,
      durationFormatted: '10m 0s'
    };
    service.createSession(dto).subscribe();
    expect(httpClientMock.post).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/reading-sessions$/),
      dto
    );
  });

  it('should call correct endpoint for getSessionsByBookId', () => {
    httpClientMock.get.mockReturnValue(of({content: []}));
    service.getSessionsByBookId(123).subscribe();
    expect(httpClientMock.get).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/reading-sessions\/book\/123$/),
      expect.objectContaining({params: expect.any(HttpParams)})
    );
  });

  it('should send beacon to correct endpoint', () => {
    const sendBeaconMock = vi.fn().mockReturnValue(true);
    vi.stubGlobal('navigator', {sendBeacon: sendBeaconMock});
    const dto: CreateReadingSessionDto = {
      bookId: 1,
      bookType: 'PDF',
      startTime: '2024-01-01T00:00:00Z',
      endTime: '2024-01-01T00:10:00Z',
      durationSeconds: 600,
      durationFormatted: '10m 0s'
    };
    service.sendSessionBeacon(dto);
    expect(sendBeaconMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/reading-sessions$/),
      expect.any(Blob)
    );
  });

  it('should expect PageableResponse<ReadingSessionResponse> from getSessionsByBookId', () => {
    const mockResponse: PageableResponse<ReadingSessionResponse> = {
      content: [],
      pageable: {
        pageNumber: 0,
        pageSize: 5,
        sort: {empty: true, sorted: false, unsorted: true},
        offset: 0,
        paged: true,
        unpaged: false
      },
      totalElements: 0,
      last: true,
      totalPages: 1,
      numberOfElements: 0,
      first: true,
      size: 5,
      number: 0,
      sort: {empty: true, sorted: false, unsorted: true},
      empty: true
    };
    httpClientMock.get.mockReturnValue(of(mockResponse));
    service.getSessionsByBookId(1).subscribe(resp => {
      expect(resp).toHaveProperty('content');
      expect(Array.isArray(resp.content)).toBe(true);
      expect(resp).toHaveProperty('totalElements');
    });
  });
});

