import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {CustomFontService} from './custom-font.service';
import {CustomFont, FontFormat} from '../model/custom-font.model';
import {AuthService} from './auth.service';

describe('CustomFontService', () => {
  let service: CustomFontService;
  let httpClientMock: any;
  let authServiceMock: any;
  let fontsSet: any[];
  let originalDocumentFonts: any;

  const mockFont: CustomFont = {
    id: 1,
    fontName: 'TestFont',
    originalFileName: 'testfont.ttf',
    format: FontFormat.TTF,
    fileSize: 1024,
    uploadedAt: '2024-01-01T00:00:00Z'
  };

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn()
    };

    authServiceMock = {
      getOidcAccessToken: vi.fn().mockReturnValue('token'),
      getInternalAccessToken: vi.fn().mockReturnValue(null)
    };

    (globalThis as any).FontFace = function (family: string, source: string, descriptors?: any) {
      this.family = family;
      this.source = source;
      this.descriptors = descriptors;
      this.load = vi.fn().mockResolvedValue(this);
    };

    // Store original and create mock fonts set
    originalDocumentFonts = document.fonts;
    fontsSet = [];

    // Mock document.fonts
    Object.defineProperty(document, 'fonts', {
      value: {
        add: vi.fn((font: any) => fontsSet.push(font)),
        delete: vi.fn((font: any) => {
          const idx = fontsSet.indexOf(font);
          if (idx !== -1) fontsSet.splice(idx, 1);
        }),
        [Symbol.iterator]: function* () {
          yield* fontsSet;
        }
      },
      writable: true,
      configurable: true
    });

    TestBed.configureTestingModule({
      providers: [
        CustomFontService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: AuthService, useValue: authServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(CustomFontService));
  });

  it('should initialize with empty fontsSubject', () => {
    expect(service['fontsSubject'].value).toEqual([]);
  });

  it('should upload font and update cache', () => {
    httpClientMock.post.mockReturnValue(of(mockFont));
    const file = new File(['dummy'], 'testfont.ttf');
    service.uploadFont(file, 'TestFont').subscribe(font => {
      expect(font).toEqual(mockFont);
      expect(service['fontsSubject'].value).toContainEqual(mockFont);
    });
  });

  it('should get user fonts and update cache', () => {
    httpClientMock.get.mockReturnValue(of([mockFont]));
    service.getUserFonts().subscribe(fonts => {
      expect(fonts).toEqual([mockFont]);
      expect(service['fontsSubject'].value).toEqual([mockFont]);
    });
  });

  it('should delete font and update cache', () => {
    service['fontsSubject'].next([mockFont]);
    httpClientMock.delete.mockReturnValue(of(void 0));
    service.deleteFont(1).subscribe(result => {
      expect(result).toBeUndefined();
      expect(service['fontsSubject'].value).toEqual([]);
    });
  });

  it('should get font URL', () => {
    expect(service.getFontUrl(123)).toMatch(/\/api\/v1\/custom-fonts\/123\/file$/);
  });

  it('should append token to URL', () => {
    const url = 'http://test.com/font.ttf';
    expect(service.appendToken(url)).toContain('token=');
  });

  it('should mark font as loaded after loadFontFace', async () => {
    await service.loadFontFace(mockFont);
    expect(service.isFontLoaded(mockFont.fontName)).toBe(true);
    const fontsArr = Array.from(document.fonts);
    expect(fontsArr.some((f: any) => f.family === mockFont.fontName)).toBe(true);
  });

  it('should call removeFontFace on delete', () => {
    const fontObj = new (globalThis as any).FontFace(mockFont.fontName, 'url(test.ttf)');
    document.fonts.add(fontObj);
    service['fontsSubject'].next([mockFont]);
    httpClientMock.delete.mockReturnValue(of(void 0));
    service.deleteFont(mockFont.id).subscribe(() => {
      const fontsArr = Array.from(document.fonts);
      expect(fontsArr.some((f: any) => f.family === mockFont.fontName)).toBe(false);
    });
  });

  it('should handle loadAllFonts', async () => {
    vi.spyOn(service, 'loadFontFace').mockResolvedValue(undefined);
    await service.loadAllFonts([mockFont]);
    expect(service.loadFontFace).toHaveBeenCalledWith(mockFont);
  });

  it('should return false for isFontLoaded if not loaded', () => {
    expect(service.isFontLoaded('NotLoaded')).toBe(false);
  });
});

describe('CustomFontService - API Contract Tests', () => {
  let service: CustomFontService;
  let httpClientMock: any;
  let authServiceMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn()
    };

    authServiceMock = {
      getOidcAccessToken: vi.fn().mockReturnValue('token'),
      getInternalAccessToken: vi.fn().mockReturnValue(null)
    };

    TestBed.configureTestingModule({
      providers: [
        CustomFontService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: AuthService, useValue: authServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(CustomFontService));
  });

  describe('CustomFont interface contract', () => {
    it('should validate all required CustomFont fields exist', () => {
      const requiredFields: (keyof CustomFont)[] = [
        'id', 'fontName', 'originalFileName', 'format', 'fileSize', 'uploadedAt'
      ];
      const mockResponse: CustomFont = {
        id: 1,
        fontName: 'Font',
        originalFileName: 'font.ttf',
        format: FontFormat.TTF,
        fileSize: 1234,
        uploadedAt: '2024-01-01T00:00:00Z'
      };
      httpClientMock.get.mockReturnValue(of([mockResponse]));
      service.getUserFonts().subscribe(fonts => {
        requiredFields.forEach(field => {
          expect(fonts[0]).toHaveProperty(field);
          expect(fonts[0][field]).toBeDefined();
        });
      });
    });

    it('should fail if API returns CustomFont without required id field', () => {
      const invalidResponse = [{
        fontName: 'Font',
        originalFileName: 'font.ttf',
        format: FontFormat.TTF,
        fileSize: 1234,
        uploadedAt: '2024-01-01T00:00:00Z'
      }];
      httpClientMock.get.mockReturnValue(of(invalidResponse));
      service.getUserFonts().subscribe(fonts => {
        expect(fonts[0]).not.toHaveProperty('id');
      });
    });
  });

  describe('Enum value contract', () => {
    it('should validate FontFormat enum values from API', () => {
      const validValues = [FontFormat.TTF, FontFormat.OTF, FontFormat.WOFF, FontFormat.WOFF2];
      validValues.forEach(value => {
        expect(Object.values(FontFormat)).toContain(value);
      });
      expect(Object.keys(FontFormat)).toHaveLength(4);
    });
  });

  describe('HTTP endpoint contract', () => {
    it('should call correct endpoint for getUserFonts', () => {
      httpClientMock.get.mockReturnValue(of([]));
      service.getUserFonts().subscribe();
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/custom-fonts$/));
    });

    it('should call correct endpoint for uploadFont', () => {
      httpClientMock.post.mockReturnValue(of({}));
      const file = new File(['dummy'], 'font.ttf');
      service.uploadFont(file, 'Font').subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/custom-fonts\/upload$/),
        expect.any(FormData)
      );
    });

    it('should call correct endpoint for deleteFont', () => {
      httpClientMock.delete.mockReturnValue(of(void 0));
      service.deleteFont(123).subscribe();
      expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/custom-fonts\/123$/));
    });

    it('should call getFontUrl with correct format', () => {
      expect(service.getFontUrl(42)).toMatch(/\/api\/v1\/custom-fonts\/42\/file$/);
    });
  });

  describe('Request payload contract', () => {
    it('should send FormData with file and fontName for uploadFont', () => {
      httpClientMock.post.mockReturnValue(of({}));
      const file = new File(['dummy'], 'font.ttf');
      service.uploadFont(file, 'Font').subscribe();
      const formData = httpClientMock.post.mock.calls[0][1];
      expect(formData instanceof FormData).toBe(true);
      expect(formData.has('file')).toBe(true);
      expect(formData.has('fontName')).toBe(true);
    });

    it('should send FormData with only file if fontName not provided', () => {
      httpClientMock.post.mockReturnValue(of({}));
      const file = new File(['dummy'], 'font.ttf');
      service.uploadFont(file).subscribe();
      const formData = httpClientMock.post.mock.calls[0][1];
      expect(formData instanceof FormData).toBe(true);
      expect(formData.has('file')).toBe(true);
      expect(formData.has('fontName')).toBe(false);
    });
  });

  describe('Response type contract', () => {
    it('should expect CustomFont array from getUserFonts', () => {
      const mockFonts: CustomFont[] = [{
        id: 1,
        fontName: 'Font',
        originalFileName: 'font.ttf',
        format: FontFormat.TTF,
        fileSize: 1234,
        uploadedAt: '2024-01-01T00:00:00Z'
      }];
      httpClientMock.get.mockReturnValue(of(mockFonts));
      service.getUserFonts().subscribe(fonts => {
        expect(Array.isArray(fonts)).toBe(true);
        expect(fonts[0]).toHaveProperty('id');
        expect(fonts[0]).toHaveProperty('fontName');
      });
    });

    it('should expect CustomFont from uploadFont', () => {
      const mockFont: CustomFont = {
        id: 2,
        fontName: 'Font2',
        originalFileName: 'font2.ttf',
        format: FontFormat.OTF,
        fileSize: 2048,
        uploadedAt: '2024-01-02T00:00:00Z'
      };
      httpClientMock.post.mockReturnValue(of(mockFont));
      const file = new File(['dummy'], 'font2.ttf');
      service.uploadFont(file, 'Font2').subscribe(font => {
        expect(font).toHaveProperty('id');
        expect(font).toHaveProperty('fontName');
        expect(font).toHaveProperty('format');
      });
    });

    it('should expect void from deleteFont', () => {
      httpClientMock.delete.mockReturnValue(of(void 0));
      service.deleteFont(1).subscribe(result => {
        expect(result).toBeUndefined();
      });
    });
  });
});

