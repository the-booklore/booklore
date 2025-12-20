import {Injectable} from '@angular/core';
import {fromEvent, merge, Subscription} from 'rxjs';
import {debounceTime} from 'rxjs/operators';

export interface ReadingSession {
  bookId: number;
  startTime: Date;
  endTime?: Date;
  durationSeconds?: number;
  startCfi?: string;
  endCfi?: string;
  startProgress?: number;
  endProgress?: number;
  progressDelta?: number;
}

@Injectable({
  providedIn: 'root'
})
export class ReadingSessionService {
  private currentSession: ReadingSession | null = null;
  private readonly IDLE_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
  private readonly MIN_SESSION_DURATION_SECONDS = 15; // 15 seconds
  private idleTimer: ReturnType<typeof setTimeout> | null = null;
  private activitySubscription: Subscription | null = null;

  constructor() {
    this.setupBrowserLifecycleListeners();
  }

  private setupBrowserLifecycleListeners(): void {
    // Handle tab/window close, page refresh, navigation away
    window.addEventListener('beforeunload', () => {
      if (this.currentSession) {
        this.endSessionSync();
      }
    });

    // Handle tab visibility changes (tab switching, minimizing)
    document.addEventListener('visibilitychange', () => {
      if (document.hidden && this.currentSession) {
        console.log('👁️ Tab hidden, pausing session');
        this.pauseIdleDetection();
      } else if (!document.hidden && this.currentSession) {
        console.log('👁️ Tab visible, resuming session');
        this.resumeIdleDetection();
      }
    });
  }

  startSession(bookId: number, _bookTitle: string, startCfi?: string, startProgress?: number): void {
    // End any existing session first
    if (this.currentSession) {
      this.endSession();
    }

    this.currentSession = {
      bookId,
      startTime: new Date(),
      startCfi,
      startProgress
    };

    console.log('📖 Reading session started:', {
      bookId: this.currentSession.bookId,
      startTime: this.currentSession.startTime.toISOString(),
      startProgress: this.currentSession.startProgress != null
        ? `${this.currentSession.startProgress.toFixed(1)}%`
        : 'unknown'
    });

    this.startIdleDetection();
  }

  updateProgress(currentCfi?: string, currentProgress?: number): void {
    if (!this.currentSession) {
      return;
    }

    this.currentSession.endCfi = currentCfi;
    this.currentSession.endProgress = currentProgress;
    this.resetIdleTimer();
  }

  endSession(endCfi?: string, endProgress?: number): void {
    if (!this.currentSession) {
      return;
    }

    this.stopIdleDetection();

    this.currentSession.endTime = new Date();
    this.currentSession.endCfi = endCfi ?? this.currentSession.endCfi;
    this.currentSession.endProgress = endProgress ?? this.currentSession.endProgress;

    // Calculate duration
    const durationMs = this.currentSession.endTime.getTime() - this.currentSession.startTime.getTime();
    this.currentSession.durationSeconds = Math.floor(durationMs / 1000);

    // Calculate progress delta
    if (this.currentSession.startProgress != null && this.currentSession.endProgress != null) {
      this.currentSession.progressDelta = this.currentSession.endProgress - this.currentSession.startProgress;
    }

    // Only log/send if session was meaningful
    if (this.currentSession.durationSeconds >= this.MIN_SESSION_DURATION_SECONDS) {
      this.sendSessionToBackend(this.currentSession);
    } else {
      console.log('📚 Session too short, discarding:', {
        durationSeconds: this.currentSession.durationSeconds
      });
    }

    this.currentSession = null;
  }

  private endSessionSync(): void {
    if (!this.currentSession) {
      return;
    }

    const endTime = new Date();
    const durationMs = endTime.getTime() - this.currentSession.startTime.getTime();
    const durationSeconds = Math.floor(durationMs / 1000);

    // Only send if session was meaningful
    if (durationSeconds >= this.MIN_SESSION_DURATION_SECONDS) {
      const sessionData = {
        bookId: this.currentSession.bookId,
        startTime: this.currentSession.startTime.toISOString(),
        endTime: endTime.toISOString(),
        durationSeconds,
        durationFormatted: this.formatDuration(durationSeconds),
        startProgress: this.currentSession.startProgress,
        endProgress: this.currentSession.endProgress,
        progressDelta: (this.currentSession.startProgress != null && this.currentSession.endProgress != null)
          ? this.currentSession.endProgress - this.currentSession.startProgress
          : undefined,
        startCfi: this.currentSession.startCfi,
        endCfi: this.currentSession.endCfi
      };

      console.log('📚 Reading session ended (sync):', sessionData);

      // Use sendBeacon for reliable sending during page unload
      try {
        const blob = new Blob([JSON.stringify(sessionData)], {type: 'application/json'});
        const success = navigator.sendBeacon('/api/reading-sessions', blob);
        if (!success) {
          console.warn('⚠️ sendBeacon failed, request may not have been queued');
        }
      } catch (error) {
        console.error('❌ Failed to send session data:', error);
      }
    }

    this.stopIdleDetection();
    this.currentSession = null;
  }

  private sendSessionToBackend(session: ReadingSession): void {
    const sessionData = {
      bookId: session.bookId,
      startTime: session.startTime.toISOString(),
      endTime: session.endTime!.toISOString(),
      durationSeconds: session.durationSeconds,
      durationFormatted: this.formatDuration(session.durationSeconds!),
      startProgress: session.startProgress,
      endProgress: session.endProgress,
      progressDelta: session.progressDelta,
      startCfi: session.startCfi,
      endCfi: session.endCfi
    };

    console.log('📚 Reading session completed:', sessionData);

    // TODO: Send to backend API
    // this.http.post('/api/reading-sessions', sessionData).subscribe({
    //   next: () => console.log('✅ Session saved to backend'),
    //   error: (err) => console.error('❌ Failed to save session:', err)
    // });
  }

  private formatDuration(seconds: number): string {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (hours > 0) {
      return `${hours}h ${minutes}m ${secs}s`;
    } else if (minutes > 0) {
      return `${minutes}m ${secs}s`;
    }
    return `${secs}s`;
  }

  private startIdleDetection(): void {
    this.stopIdleDetection();

    const activity$ = merge(
      fromEvent(document, 'mousemove'),
      fromEvent(document, 'mousedown'),
      fromEvent(document, 'keypress'),
      fromEvent(document, 'scroll'),
      fromEvent(document, 'touchstart')
    ).pipe(
      debounceTime(1000)
    );

    this.activitySubscription = activity$.subscribe(() => {
      this.resetIdleTimer();
    });

    this.resetIdleTimer();
  }

  private pauseIdleDetection(): void {
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
      this.idleTimer = null;
    }
    if (this.activitySubscription) {
      this.activitySubscription.unsubscribe();
      this.activitySubscription = null;
    }
  }

  private resumeIdleDetection(): void {
    if (this.currentSession) {
      this.startIdleDetection();
    }
  }

  private resetIdleTimer(): void {
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
    }

    this.idleTimer = setTimeout(() => {
      console.log('⏱️ User idle detected, ending session');
      this.endSession();
    }, this.IDLE_TIMEOUT_MS);
  }

  private stopIdleDetection(): void {
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
      this.idleTimer = null;
    }
    if (this.activitySubscription) {
      this.activitySubscription.unsubscribe();
      this.activitySubscription = null;
    }
  }

  isSessionActive(): boolean {
    return this.currentSession !== null;
  }
}
