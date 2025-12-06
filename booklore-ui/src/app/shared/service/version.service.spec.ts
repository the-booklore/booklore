import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { VersionService, AppVersion, ReleaseNote } from './version.service';
import { API_CONFIG } from '../../core/config/api-config';

describe('VersionService', () => {
  let service: VersionService;
  let httpMock: HttpTestingController;
  const baseUrl = API_CONFIG.BASE_URL;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [VersionService]
    });

    service = TestBed.inject(VersionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getVersion', () => {
    it('should return app version from API', () => {
      const mockVersion: AppVersion = {
        current: '1.0.0',
        latest: '1.1.0'
      };

      service.getVersion().subscribe(version => {
        expect(version).toEqual(mockVersion);
        expect(version.current).toBe('1.0.0');
        expect(version.latest).toBe('1.1.0');
      });

      const req = httpMock.expectOne(`${baseUrl}/api/v1/version`);
      expect(req.request.method).toBe('GET');
      req.flush(mockVersion);
    });

    it('should handle version when current equals latest', () => {
      const mockVersion: AppVersion = {
        current: '1.0.0',
        latest: '1.0.0'
      };

      service.getVersion().subscribe(version => {
        expect(version.current).toBe(version.latest);
      });

      const req = httpMock.expectOne(`${baseUrl}/api/v1/version`);
      req.flush(mockVersion);
    });

    it('should handle unknown latest version', () => {
      const mockVersion: AppVersion = {
        current: '1.0.0',
        latest: 'unknown'
      };

      service.getVersion().subscribe(version => {
        expect(version.latest).toBe('unknown');
      });

      const req = httpMock.expectOne(`${baseUrl}/api/v1/version`);
      req.flush(mockVersion);
    });

    it('should handle HTTP error', () => {
      service.getVersion().subscribe({
        next: () => fail('Expected an error'),
        error: (error) => {
          expect(error.status).toBe(500);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/api/v1/version`);
      req.flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    });
  });

  describe('getChangelog', () => {
    it('should return changelog from API', () => {
      const mockChangelog: ReleaseNote[] = [
        {
          version: 'v1.1.0',
          name: 'Version 1.1.0',
          changelog: '- New feature\n- Bug fix',
          url: 'https://github.com/booklore-app/booklore/releases/tag/v1.1.0',
          publishedAt: '2024-01-15T10:00:00Z'
        },
        {
          version: 'v1.0.1',
          name: 'Version 1.0.1',
          changelog: '- Bug fix',
          url: 'https://github.com/booklore-app/booklore/releases/tag/v1.0.1',
          publishedAt: '2024-01-10T10:00:00Z'
        }
      ];

      service.getChangelog().subscribe(changelog => {
        expect(changelog).toEqual(mockChangelog);
        expect(changelog.length).toBe(2);
        expect(changelog[0].version).toBe('v1.1.0');
      });

      const req = httpMock.expectOne(`${baseUrl}/api/v1/version/changelog`);
      expect(req.request.method).toBe('GET');
      req.flush(mockChangelog);
    });

    it('should return empty array when no new releases', () => {
      const mockChangelog: ReleaseNote[] = [];

      service.getChangelog().subscribe(changelog => {
        expect(changelog).toEqual([]);
        expect(changelog.length).toBe(0);
      });

      const req = httpMock.expectOne(`${baseUrl}/api/v1/version/changelog`);
      req.flush(mockChangelog);
    });

    it('should handle HTTP error', () => {
      service.getChangelog().subscribe({
        next: () => fail('Expected an error'),
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/api/v1/version/changelog`);
      req.flush('Not Found', { status: 404, statusText: 'Not Found' });
    });
  });
});
