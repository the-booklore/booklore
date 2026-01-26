import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {AppVersion, ReleaseNote, VersionService} from './version.service';

describe('VersionService', () => {
  let service: VersionService;
  let httpClientMock: any;

  const mockVersion: AppVersion = {
    current: '1.0.0',
    latest: '1.2.0'
  };

  const mockChangelog: ReleaseNote[] = [
    {
      version: '1.2.0',
      name: 'Release 1.2.0',
      changelog: 'Bug fixes and improvements',
      url: 'https://example.com/release/1.2.0',
      publishedAt: '2024-01-01T00:00:00Z'
    }
  ];

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    service = TestBed.inject(VersionService);
  });

  it('should get version', () => {
    httpClientMock.get.mockReturnValue(of(mockVersion));
    service.getVersion().subscribe(version => {
      expect(version).toEqual(mockVersion);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/version$/));
    });
  });

  it('should get changelog', () => {
    httpClientMock.get.mockReturnValue(of(mockChangelog));
    service.getChangelog().subscribe(changelog => {
      expect(changelog).toEqual(mockChangelog);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/version\/changelog$/));
    });
  });

  it('should handle getVersion error', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getVersion().subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle getChangelog error', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getChangelog().subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });
});

describe('VersionService - API Contract Tests', () => {
  let service: VersionService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    service = TestBed.inject(VersionService);
  });

  it('should expect AppVersion from getVersion', () => {
    const response: AppVersion = {current: '1.0.0', latest: '1.2.0'};
    httpClientMock.get.mockReturnValue(of(response));
    service.getVersion().subscribe(version => {
      expect(typeof version.current).toBe('string');
      expect(typeof version.latest).toBe('string');
    });
  });

  it('should expect ReleaseNote[] from getChangelog', () => {
    const response: ReleaseNote[] = [
      {
        version: '1.2.0',
        name: 'Release 1.2.0',
        changelog: 'Bug fixes and improvements',
        url: 'https://example.com/release/1.2.0',
        publishedAt: '2024-01-01T00:00:00Z'
      }
    ];
    httpClientMock.get.mockReturnValue(of(response));
    service.getChangelog().subscribe(notes => {
      expect(Array.isArray(notes)).toBe(true);
      expect(notes[0]).toHaveProperty('version');
      expect(notes[0]).toHaveProperty('name');
      expect(notes[0]).toHaveProperty('changelog');
      expect(notes[0]).toHaveProperty('url');
      expect(notes[0]).toHaveProperty('publishedAt');
    });
  });

  it('should call correct endpoints', () => {
    httpClientMock.get.mockReturnValue(of({}));
    service.getVersion().subscribe();
    expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/version$/));
    service.getChangelog().subscribe();
    expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/version\/changelog$/));
  });
});

