import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {BookType} from '../../features/book/model/book.model';
import {ReadingSessionService, ReadingSession} from './reading-session.service';
import {ReadingSessionApiService, CreateReadingSessionDto} from './reading-session-api.service';
import {of} from 'rxjs';

describe('ReadingSessionService', () => {
  let service: ReadingSessionService;
  let apiServiceMock: any;

  const now = new Date();
  const mockSession: ReadingSession = {
    bookId: 42,
    bookType: 'PDF' as BookType,
    startTime: now,
    startLocation: 'start-cfi',
    startProgress: 10
  };

  beforeEach(() => {
    apiServiceMock = {
      createSession: vi.fn().mockReturnValue(of(void 0)),
      sendSessionBeacon: vi.fn().mockReturnValue(true)
    };

    TestBed.configureTestingModule({
      providers: [
        ReadingSessionService,
        {provide: ReadingSessionApiService, useValue: apiServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(ReadingSessionService));
  });

  it('should start a session', () => {
    service.startSession(1, 'PDF', 'loc', 5);
    expect(service.isSessionActive()).toBe(true);
  });

  it('should end a session and send to backend if duration is sufficient', () => {
    vi.useFakeTimers();
    service.startSession(1, 'PDF', 'loc', 5);
    // Simulate session duration > MIN_SESSION_DURATION_SECONDS
    (service as any).currentSession!.startTime = new Date(Date.now() - 60000);
    service.endSession('endloc', 10);
    expect(apiServiceMock.createSession).toHaveBeenCalled();
    expect(service.isSessionActive()).toBe(false);
    vi.useRealTimers();
  });

  it('should discard session if duration is too short', () => {
    vi.useFakeTimers();
    service.startSession(1, 'PDF', 'loc', 5);
    (service as any).currentSession!.startTime = new Date(Date.now() - 10000);
    service.endSession('endloc', 10);
    expect(apiServiceMock.createSession).not.toHaveBeenCalled();
    expect(service.isSessionActive()).toBe(false);
    vi.useRealTimers();
  });

  it('should update progress', () => {
    service.startSession(1, 'PDF', 'loc', 5);
    service.updateProgress('newloc', 15);
    const session = (service as any).currentSession;
    expect(session.endLocation).toBe('newloc');
    expect(session.endProgress).toBe(15);
  });

  it('should end session synchronously with sendSessionBeacon', () => {
    service.startSession(1, 'PDF', 'loc', 5);
    (service as any).currentSession!.startTime = new Date(Date.now() - 60000);
    service['endSessionSync']();
    expect(apiServiceMock.sendSessionBeacon).toHaveBeenCalled();
    expect(service.isSessionActive()).toBe(false);
  });

  it('should not send beacon if session duration is too short', () => {
    service.startSession(1, 'PDF', 'loc', 5);
    (service as any).currentSession!.startTime = new Date(Date.now() - 10000);
    service['endSessionSync']();
    expect(apiServiceMock.sendSessionBeacon).not.toHaveBeenCalled();
    expect(service.isSessionActive()).toBe(false);
  });

  it('should return false for isSessionActive when no session', () => {
    expect(service.isSessionActive()).toBe(false);
  });

  it('should build session data correctly', () => {
    const endTime = new Date(now.getTime() + 60000);
    const session: ReadingSession = {
      bookId: 1,
      bookType: 'PDF',
      startTime: now,
      endTime,
      durationSeconds: 60,
      startProgress: 10,
      endProgress: 20,
      progressDelta: 10,
      startLocation: 'loc1',
      endLocation: 'loc2'
    };
    const dto = (service as any).buildSessionData(session, endTime, 60);
    expect(dto.bookId).toBe(1);
    expect(dto.bookType).toBe('PDF');
    expect(dto.durationSeconds).toBe(60);
    expect(dto.startProgress).toBe(10);
    expect(dto.endProgress).toBe(20);
    expect(dto.progressDelta).toBe(10);
    expect(dto.startLocation).toBe('loc1');
    expect(dto.endLocation).toBe('loc2');
    expect(typeof dto.startTime).toBe('string');
    expect(typeof dto.endTime).toBe('string');
    expect(dto.durationFormatted).toBe('1m 0s');
  });

  it('should format duration correctly', () => {
    expect((service as any).formatDuration(3661)).toBe('1h 1m 1s');
    expect((service as any).formatDuration(61)).toBe('1m 1s');
    expect((service as any).formatDuration(10)).toBe('10s');
  });
});

describe('ReadingSessionService - API Contract Tests', () => {
  let service: ReadingSessionService;
  let apiServiceMock: any;

  beforeEach(() => {
    apiServiceMock = {
      createSession: vi.fn().mockReturnValue(of(void 0)),
      sendSessionBeacon: vi.fn().mockReturnValue(true)
    };

    TestBed.configureTestingModule({
      providers: [
        ReadingSessionService,
        {provide: ReadingSessionApiService, useValue: apiServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(ReadingSessionService));
  });

  it('should call createSession with correct payload', () => {
    const session: ReadingSession = {
      bookId: 1,
      bookType: 'PDF',
      startTime: new Date(Date.now() - 60000),
      endTime: new Date(),
      durationSeconds: 60,
      startProgress: 10,
      endProgress: 20,
      progressDelta: 10,
      startLocation: 'loc1',
      endLocation: 'loc2'
    };
    (service as any).sendSessionToBackend(session);
    expect(apiServiceMock.createSession).toHaveBeenCalledWith(expect.objectContaining({
      bookId: 1,
      bookType: 'PDF',
      durationSeconds: 60,
      startProgress: 10,
      endProgress: 20,
      progressDelta: 10,
      startLocation: 'loc1',
      endLocation: 'loc2'
    }));
  });

  it('should call sendSessionBeacon with correct payload', () => {
    const session: ReadingSession = {
      bookId: 2,
      bookType: 'EPUB',
      startTime: new Date(Date.now() - 120000),
      startProgress: 0,
      endProgress: 100,
      progressDelta: 100,
      startLocation: 'start',
      endLocation: 'end'
    };
    const endTime = new Date();
    const durationSeconds = 120;
    const dto = (service as any).buildSessionData(session, endTime, durationSeconds);
    (service as any).currentSession = session;
    apiServiceMock.sendSessionBeacon.mockClear();
    (service as any).endSessionSync();
    expect(apiServiceMock.sendSessionBeacon).toHaveBeenCalledWith(expect.objectContaining({
      bookId: 2,
      bookType: 'EPUB',
      startLocation: 'start',
      endLocation: 'end'
    }));
  });

  it('should not call createSession if endTime or durationSeconds missing', () => {
    const session: ReadingSession = {
      bookId: 1,
      bookType: 'PDF',
      startTime: new Date()
      // missing endTime and durationSeconds
    };
    (service as any).sendSessionToBackend(session);
    expect(apiServiceMock.createSession).not.toHaveBeenCalled();
  });

  it('should not call sendSessionBeacon if duration too short', () => {
    const session: ReadingSession = {
      bookId: 1,
      bookType: 'PDF',
      startTime: new Date(Date.now() - 10000)
    };
    (service as any).currentSession = session;
    apiServiceMock.sendSessionBeacon.mockClear();
    (service as any).endSessionSync();
    expect(apiServiceMock.sendSessionBeacon).not.toHaveBeenCalled();
  });

  it('should send correct types in CreateReadingSessionDto', () => {
    const session: ReadingSession = {
      bookId: 3,
      bookType: 'CBX',
      startTime: new Date(Date.now() - 180000),
      endTime: new Date(),
      durationSeconds: 180,
      startProgress: 0,
      endProgress: 50,
      progressDelta: 50,
      startLocation: 'foo',
      endLocation: 'bar'
    };
    const dto: CreateReadingSessionDto = (service as any).buildSessionData(session, session.endTime!, session.durationSeconds!);
    expect(typeof dto.bookId).toBe('number');
    expect(typeof dto.bookType).toBe('string');
    expect(typeof dto.startTime).toBe('string');
    expect(typeof dto.endTime).toBe('string');
    expect(typeof dto.durationSeconds).toBe('number');
    expect(typeof dto.durationFormatted).toBe('string');
    expect(typeof dto.startProgress).toBe('number');
    expect(typeof dto.endProgress).toBe('number');
    expect(typeof dto.progressDelta).toBe('number');
    expect(typeof dto.startLocation).toBe('string');
    expect(typeof dto.endLocation).toBe('string');
  });
});

