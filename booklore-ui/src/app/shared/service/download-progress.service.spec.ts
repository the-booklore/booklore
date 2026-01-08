import {beforeEach, describe, expect, it, vi} from 'vitest';
import {DownloadProgressService} from './download-progress.service';
import {Subject, take} from 'rxjs';

describe('DownloadProgressService', () => {
  let service: DownloadProgressService;

  beforeEach(() => {
    service = new DownloadProgressService();
  });

  it('should initialize with default state', () => {
    service.downloadProgress$.pipe(take(1)).subscribe(progress => {
      expect(progress).toEqual({
        visible: false,
        filename: '',
        progress: 0,
        loaded: 0,
        total: 0
      });
    });
    expect(service.isDownloadInProgress()).toBe(false);
  });

  it('should start a download and set state', () => {
    const cancelSubject = new Subject<void>();
    service.startDownload('file.pdf', cancelSubject);
    service.downloadProgress$.pipe(take(1)).subscribe(progress => {
      expect(progress.visible).toBe(true);
      expect(progress.filename).toBe('file.pdf');
      expect(progress.progress).toBe(0);
      expect(progress.cancelSubject).toBe(cancelSubject);
    });
    expect(service.isDownloadInProgress()).toBe(true);
  });

  it('should update progress and throttle updates', async () => {
    const cancelSubject = new Subject<void>();
    service.startDownload('file.pdf', cancelSubject);

    // First update should be immediate
    service.updateProgress(50, 100);
    service.downloadProgress$.pipe(take(1)).subscribe(progress => {
      expect(progress.progress).toBe(50);
      expect(progress.loaded).toBe(50);
      expect(progress.total).toBe(100);
    });

    // Throttled update (simulate time)
    vi.useFakeTimers();
    service.updateProgress(60, 100);
    // Should not emit immediately
    let emitted = false;
    service.downloadProgress$.subscribe(progress => {
      if (progress.loaded === 60) emitted = true;
    });
    vi.advanceTimersByTime(100);
    expect(emitted).toBe(true);
    vi.useRealTimers();
  });

  it('should immediately update when progress is 100', () => {
    const cancelSubject = new Subject<void>();
    service.startDownload('file.pdf', cancelSubject);
    service.updateProgress(100, 100);
    service.downloadProgress$.pipe(take(1)).subscribe(progress => {
      expect(progress.progress).toBe(100);
      expect(progress.loaded).toBe(100);
      expect(progress.total).toBe(100);
    });
  });

  it('should complete download and reset state', () => {
    const cancelSubject = new Subject<void>();
    service.startDownload('file.pdf', cancelSubject);
    service.completeDownload();
    service.downloadProgress$.pipe(take(1)).subscribe(progress => {
      expect(progress.visible).toBe(false);
      expect(progress.filename).toBe('');
      expect(progress.progress).toBe(0);
    });
    expect(service.isDownloadInProgress()).toBe(false);
  });

  it('should cancel download and call cancelSubject', () => {
    const cancelSubject = new Subject<void>();
    const nextSpy = vi.fn();
    const completeSpy = vi.fn();
    cancelSubject.subscribe({next: nextSpy, complete: completeSpy});
    service.startDownload('file.pdf', cancelSubject);
    service.cancelDownload();
    expect(nextSpy).toHaveBeenCalled();
    expect(completeSpy).toHaveBeenCalled();
    expect(service.isDownloadInProgress()).toBe(false);
  });
});

describe('DownloadProgressService - API Contract Tests', () => {
  let service: DownloadProgressService;

  beforeEach(() => {
    service = new DownloadProgressService();
  });

  describe('DownloadProgress interface contract', () => {
    it('should have all required fields', () => {
      const cancelSubject = new Subject<void>();
      service.startDownload('test.txt', cancelSubject);
      service.downloadProgress$.pipe(take(1)).subscribe(progress => {
        expect(progress).toHaveProperty('visible');
        expect(progress).toHaveProperty('filename');
        expect(progress).toHaveProperty('progress');
        expect(progress).toHaveProperty('loaded');
        expect(progress).toHaveProperty('total');
        expect(progress).toHaveProperty('cancelSubject');
      });
    });

    it('should allow cancelSubject to be undefined after complete', () => {
      service.completeDownload();
      service.downloadProgress$.pipe(take(1)).subscribe(progress => {
        expect(progress.cancelSubject).toBeUndefined();
      });
    });
  });

  describe('Behavior contract', () => {
    it('should emit correct progress values', () => {
      const cancelSubject = new Subject<void>();
      service.startDownload('file.pdf', cancelSubject);
      service.updateProgress(25, 100);
      service.downloadProgress$.pipe(take(1)).subscribe(progress => {
        expect(progress.progress).toBe(25);
        expect(progress.loaded).toBe(25);
        expect(progress.total).toBe(100);
      });
    });

    it('should not exceed 100% progress', () => {
      const cancelSubject = new Subject<void>();
      service.startDownload('file.pdf', cancelSubject);
      service.updateProgress(150, 100);
      service.downloadProgress$.pipe(take(1)).subscribe(progress => {
        expect(progress.progress).toBe(100);
      });
    });

    it('should set progress to 0 if total is 0', () => {
      const cancelSubject = new Subject<void>();
      service.startDownload('file.pdf', cancelSubject);
      service.updateProgress(50, 0);
      service.downloadProgress$.pipe(take(1)).subscribe(progress => {
        expect(progress.progress).toBe(0);
      });
    });
  });

  describe('API contract', () => {
    it('should expose downloadProgress$ as observable', () => {
      expect(typeof service.downloadProgress$.subscribe).toBe('function');
    });

    it('should expose isDownloadInProgress as boolean', () => {
      expect(typeof service.isDownloadInProgress()).toBe('boolean');
    });

    it('should expose startDownload, updateProgress, completeDownload, cancelDownload', () => {
      expect(typeof service.startDownload).toBe('function');
      expect(typeof service.updateProgress).toBe('function');
      expect(typeof service.completeDownload).toBe('function');
      expect(typeof service.cancelDownload).toBe('function');
    });
  });
});
