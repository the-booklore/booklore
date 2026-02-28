import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {LibraryHealthService} from './library-health.service';
import {HttpClient} from '@angular/common/http';
import {RxStompService} from '../../../shared/websocket/rx-stomp.service';
import {of, Subject} from 'rxjs';
import {IMessage} from '@stomp/rx-stomp';

describe('LibraryHealthService', () => {
  let service: LibraryHealthService;
  let httpGetSpy: ReturnType<typeof vi.fn>;
  let wsSubject: Subject<IMessage>;

  beforeEach(() => {
    httpGetSpy = vi.fn().mockReturnValue(of({}));
    wsSubject = new Subject<IMessage>();

    TestBed.configureTestingModule({
      providers: [
        LibraryHealthService,
        {provide: HttpClient, useValue: {get: httpGetSpy}},
        {provide: RxStompService, useValue: {watch: vi.fn(() => wsSubject.asObservable())}},
      ]
    });

    service = TestBed.inject(LibraryHealthService);
  });

  it('should fetch initial health on initialize', () => {
    httpGetSpy.mockReturnValue(of({1: true, 2: false}));
    service.initialize();

    expect(httpGetSpy).toHaveBeenCalled();
  });

  it('should emit unhealthy for a library with false health', () => {
    httpGetSpy.mockReturnValue(of({1: true, 2: false}));
    service.initialize();

    const results: boolean[] = [];
    service.isUnhealthy$(2).subscribe(v => results.push(v));

    expect(results).toContain(true);
  });

  it('should emit healthy for a library with true health', () => {
    httpGetSpy.mockReturnValue(of({1: true}));
    service.initialize();

    const results: boolean[] = [];
    service.isUnhealthy$(1).subscribe(v => results.push(v));

    expect(results[results.length - 1]).toBe(false);
  });

  it('should emit false for unknown library', () => {
    httpGetSpy.mockReturnValue(of({}));
    service.initialize();

    const results: boolean[] = [];
    service.isUnhealthy$(99).subscribe(v => results.push(v));

    expect(results[results.length - 1]).toBe(false);
  });

  it('should update state from websocket messages', () => {
    httpGetSpy.mockReturnValue(of({1: true}));
    service.initialize();

    const results: boolean[] = [];
    service.isUnhealthy$(1).subscribe(v => results.push(v));

    wsSubject.next({body: JSON.stringify({libraryHealth: {1: false}})} as IMessage);

    expect(results[results.length - 1]).toBe(true);
  });

  it('should use distinctUntilChanged to avoid duplicate emissions', () => {
    httpGetSpy.mockReturnValue(of({1: true}));
    service.initialize();

    const results: boolean[] = [];
    service.isUnhealthy$(1).subscribe(v => results.push(v));

    // Same state again via websocket
    wsSubject.next({body: JSON.stringify({libraryHealth: {1: true}})} as IMessage);
    wsSubject.next({body: JSON.stringify({libraryHealth: {1: true}})} as IMessage);

    expect(results.length).toBe(1);
  });
});
