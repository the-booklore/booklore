import {beforeEach, describe, expect, it, vi, afterEach} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient, HttpEventType, HttpHeaders, HttpResponse} from '@angular/common/http';
import {of, Subject} from 'rxjs';
import {FileDownloadService} from './file-download.service';
import {DownloadProgressService} from './download-progress.service';
import {MessageService} from 'primeng/api';

describe('FileDownloadService', () => {
  let service: FileDownloadService;
  let httpClientMock: any;
  let downloadProgressServiceMock: any;
  let messageServiceMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn()
    };
    downloadProgressServiceMock = {
      isDownloadInProgress: vi.fn().mockReturnValue(false),
      startDownload: vi.fn(),
      updateProgress: vi.fn(),
      completeDownload: vi.fn()
    };
    messageServiceMock = {
      add: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        FileDownloadService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: DownloadProgressService, useValue: downloadProgressServiceMock},
        {provide: MessageService, useValue: messageServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(FileDownloadService));
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.runAllTimers();
    vi.useRealTimers();
  });

  it('should trigger download and handle progress and completion', () => {
    const events = [
      {type: HttpEventType.DownloadProgress, loaded: 50, total: 100},
      {
        type: HttpEventType.Response,
        body: new Blob(['test']),
        headers: new HttpHeaders(),
        clone: () => this,
        status: 200,
        statusText: 'OK',
        url: '',
        ok: true
      }
    ];
    httpClientMock.get.mockReturnValue(of(...events));
    // Mock DOM for download
    const link = document.createElement('a');
    vi.spyOn(link, 'click').mockImplementation(() => {});
    vi.spyOn(document, 'createElement').mockReturnValue(link);
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => link);
    vi.spyOn(document.body, 'removeChild').mockImplementation(() => link);
    vi.spyOn(document.body, 'contains').mockReturnValue(true);
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:url');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});

    service.downloadFile('/api/file', 'file.txt');
    expect(httpClientMock.get).toHaveBeenCalledWith('/api/file', expect.objectContaining({observe: 'events'}));
    expect(downloadProgressServiceMock.startDownload).toHaveBeenCalledWith('file.txt', expect.any(Subject));
    expect(downloadProgressServiceMock.updateProgress).toHaveBeenCalledWith(50, 100);
    expect(downloadProgressServiceMock.completeDownload).toHaveBeenCalled();
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
  });

  it('should extract filename from Content-Disposition header', () => {
    const headers = new HttpHeaders({'Content-Disposition': "attachment; filename*=UTF-8''test%20file.txt"});
    const response = new HttpResponse({body: new Blob(['test']), headers});
    const filename = (service as any).extractFilenameFromResponse(response, 'default.txt');
    expect(filename).toBe('test file.txt');
  });

  it('should fallback to default filename if Content-Disposition is missing', () => {
    const headers = new HttpHeaders();
    const response = new HttpResponse({body: new Blob(['test']), headers});
    const filename = (service as any).extractFilenameFromResponse(response, 'default.txt');
    expect(filename).toBe('default.txt');
  });

  it('should throw if no file content received', () => {
    const response = new HttpResponse({body: undefined, headers: new HttpHeaders()});
    expect(() => (service as any).handleDownloadComplete(response, 'file.txt')).toThrow('No file content received');
  });
});

describe('FileDownloadService - API Contract Tests', () => {
  let service: FileDownloadService;
  let httpClientMock: any;
  let downloadProgressServiceMock: any;
  let messageServiceMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn()
    };
    downloadProgressServiceMock = {
      isDownloadInProgress: vi.fn().mockReturnValue(false),
      startDownload: vi.fn(),
      updateProgress: vi.fn(),
      completeDownload: vi.fn()
    };
    messageServiceMock = {
      add: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        FileDownloadService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: DownloadProgressService, useValue: downloadProgressServiceMock},
        {provide: MessageService, useValue: messageServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(FileDownloadService));
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.runAllTimers();
    vi.useRealTimers();
  });

  describe('API contract', () => {
    it('should expose downloadFile as a function', () => {
      expect(typeof service.downloadFile).toBe('function');
    });
  });

  describe('HTTP endpoint contract', () => {
    it('should call correct endpoint and options for downloadFile', () => {
      httpClientMock.get.mockReturnValue(of());
      service.downloadFile('/api/file', 'file.txt');
      expect(httpClientMock.get).toHaveBeenCalledWith(
        '/api/file',
        expect.objectContaining({
          responseType: 'blob',
          observe: 'events',
          reportProgress: true
        })
      );
    });
  });

  describe('Request/Response contract', () => {
    it('should expect Blob in HttpResponse', () => {
      const headers = new HttpHeaders();
      const response = new HttpResponse({body: new Blob(['test']), headers});
      expect((service as any).extractFilenameFromResponse(response, 'file.txt')).toBe('file.txt');
    });

    it('should handle Content-Disposition with encoded filename', () => {
      const headers = new HttpHeaders({'Content-Disposition': "attachment; filename*=UTF-8''encoded%20file.pdf"});
      const response = new HttpResponse({body: new Blob(['test']), headers});
      expect((service as any).extractFilenameFromResponse(response, 'fallback.pdf')).toBe('encoded file.pdf');
    });
  });

  describe('Behavior contract', () => {
    it('should call startDownload and updateProgress on DownloadProgressService', () => {
      httpClientMock.get.mockReturnValue(of({type: HttpEventType.DownloadProgress, loaded: 10, total: 20}));
      service.downloadFile('/api/file', 'file.txt');
      expect(downloadProgressServiceMock.startDownload).toHaveBeenCalled();
      expect(downloadProgressServiceMock.updateProgress).toHaveBeenCalledWith(10, 20);
    });

    it('should call completeDownload on finalize', () => {
      httpClientMock.get.mockReturnValue(of({type: HttpEventType.Response, body: new Blob(['test']), headers: new HttpHeaders(), clone: () => this, status: 200, statusText: 'OK', url: '', ok: true}));
      service.downloadFile('/api/file', 'file.txt');
      expect(downloadProgressServiceMock.completeDownload).toHaveBeenCalled();
    });

    it('should show success message on download complete', () => {
      httpClientMock.get.mockReturnValue(of({type: HttpEventType.Response, body: new Blob(['test']), headers: new HttpHeaders(), clone: () => this, status: 200, statusText: 'OK', url: '', ok: true}));
      service.downloadFile('/api/file', 'file.txt');
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });
  });
});

