import { Component, ElementRef, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { Button } from 'primeng/button';
import { Slider, SliderChangeEvent } from 'primeng/slider';
import { ProgressSpinner } from 'primeng/progressspinner';
import { Tooltip } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { SelectButton } from 'primeng/selectbutton';
import { Menu } from 'primeng/menu';
import { MenuItem } from 'primeng/api';

import { AudiobookService } from './audiobook.service';
import { AudiobookInfo, AudiobookChapter, AudiobookTrack, AudiobookProgress } from './audiobook.model';
import { BookService } from '../../book/service/book.service';
import { BookMarkService, BookMark, CreateBookMarkRequest } from '../../../shared/service/book-mark.service';
import { AudiobookSessionService } from '../../../shared/service/audiobook-session.service';
import { PageTitleService } from '../../../shared/service/page-title.service';
import { AuthService } from '../../../shared/service/auth.service';
import { API_CONFIG } from '../../../core/config/api-config';

@Component({
  selector: 'app-audiobook-reader',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Button,
    Slider,
    ProgressSpinner,
    Tooltip,
    SelectButton,
    Menu
  ],
  templateUrl: './audiobook-reader.component.html',
  styleUrls: ['./audiobook-reader.component.scss']
})
export class AudiobookReaderComponent implements OnInit, OnDestroy {
  @ViewChild('audioElement') audioElement!: ElementRef<HTMLAudioElement>;

  private destroy$ = new Subject<void>();
  private audiobookService = inject(AudiobookService);
  private bookService = inject(BookService);
  private bookMarkService = inject(BookMarkService);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private location = inject(Location);
  private messageService = inject(MessageService);
  private audiobookSessionService = inject(AudiobookSessionService);
  private pageTitle = inject(PageTitleService);

  // Loading state
  isLoading = true;
  audioLoading = true;

  // Book data
  bookId!: number;
  audiobookInfo!: AudiobookInfo;
  coverUrl?: string;
  bookCoverUrl?: string;

  // Audio state
  isPlaying = false;
  currentTime = 0;
  duration = 0;
  volume = 1;
  previousVolume = 1;
  isMuted = false;
  playbackRate = 1;
  buffered = 0;

  // Saved position to restore after audio loads
  private savedPosition = 0;

  // Track state (for folder-based audiobooks)
  currentTrackIndex = 0;
  audioSrc = '';

  // UI state
  showTrackList = false;
  showBookmarkList = false;

  // Sleep timer
  sleepTimerActive = false;
  sleepTimerRemaining = 0; // seconds remaining
  sleepTimerEndOfChapter = false;
  private sleepTimerInterval?: ReturnType<typeof setInterval>;
  private originalVolume = 1;

  sleepTimerOptions: MenuItem[] = [
    { label: '15 minutes', command: () => this.setSleepTimer(15) },
    { label: '30 minutes', command: () => this.setSleepTimer(30) },
    { label: '45 minutes', command: () => this.setSleepTimer(45) },
    { label: '60 minutes', command: () => this.setSleepTimer(60) },
    { label: 'End of chapter', command: () => this.setSleepTimerEndOfChapter() },
    { separator: true },
    { label: 'Cancel timer', command: () => this.cancelSleepTimer(), visible: false }
  ];

  // Bookmarks
  bookmarks: BookMark[] = [];

  // Playback speed options
  playbackRates = [
    { label: '0.5x', value: 0.5 },
    { label: '0.75x', value: 0.75 },
    { label: '1x', value: 1 },
    { label: '1.25x', value: 1.25 },
    { label: '1.5x', value: 1.5 },
    { label: '2x', value: 2 }
  ];

  // Progress save interval
  private progressSaveInterval?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.bookId = +params.get('bookId')!;
      this.loadAudiobook();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();

    if (this.progressSaveInterval) {
      clearInterval(this.progressSaveInterval);
    }

    if (this.sleepTimerInterval) {
      clearInterval(this.sleepTimerInterval);
    }

    this.saveProgress();

    if (this.audiobookSessionService.isSessionActive()) {
      this.audiobookSessionService.endSession(Math.round(this.currentTime * 1000));
    }
  }

  private loadAudiobook(): void {
    // Reset all state when loading a new audiobook
    this.resetState();
    this.isLoading = true;

    const audiobookInfo$ = this.audiobookService.getAudiobookInfo(this.bookId);
    const book$ = this.bookService.getBookByIdFromAPI(this.bookId, false);

    forkJoin([audiobookInfo$, book$]).subscribe({
      next: ([info, book]) => {
        this.audiobookInfo = info;
        this.pageTitle.setBookPageTitle(book);

        // Set cover URL with auth token
        const token = this.authService.getInternalAccessToken() || this.authService.getOidcAccessToken();
        this.bookCoverUrl = `${API_CONFIG.BASE_URL}/api/v1/media/cover/${this.bookId}?token=${encodeURIComponent(token || '')}`;
        this.coverUrl = this.audiobookService.getEmbeddedCoverUrl(this.bookId);

        // Restore progress and load audio
        if (book.audiobookProgress) {
          // Store saved position - will be applied when audio loads
          this.savedPosition = book.audiobookProgress.positionMs
            ? book.audiobookProgress.positionMs / 1000
            : 0;
        }

        if (info.folderBased && info.tracks && info.tracks.length > 0) {
          // Folder-based audiobook
          const trackIndex = book.audiobookProgress?.trackIndex ?? 0;
          this.currentTrackIndex = trackIndex;
          this.loadTrack(trackIndex);
        } else {
          // Single-file audiobook
          this.audioSrc = this.audiobookService.getStreamUrl(this.bookId);
        }

        this.isLoading = false;

        // Load bookmarks
        this.loadBookmarks();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to load audiobook'
        });
        this.isLoading = false;
      }
    });
  }

  private loadTrack(index: number): void {
    if (!this.audiobookInfo.tracks || index < 0 || index >= this.audiobookInfo.tracks.length) {
      return;
    }
    this.currentTrackIndex = index;
    this.audioSrc = this.audiobookService.getTrackStreamUrl(this.bookId, index);
    this.audioLoading = true;
    // Reset buffered since it's a new track
    this.buffered = 0;
  }

  private resetState(): void {
    // Stop any existing intervals
    this.stopProgressSaveInterval();

    // Reset audio state
    this.isPlaying = false;
    this.currentTime = 0;
    this.duration = 0;
    this.buffered = 0;
    this.savedPosition = 0;
    this.currentTrackIndex = 0;
    this.audioSrc = '';
    this.audioLoading = true;

    // Reset UI state
    this.showTrackList = false;
    this.coverUrl = undefined;
    this.bookCoverUrl = undefined;
  }

  // Audio event handlers
  onAudioLoaded(): void {
    this.audioLoading = false;
    const audio = this.audioElement?.nativeElement;
    if (audio) {
      this.duration = audio.duration;
      audio.volume = this.volume;
      audio.playbackRate = this.playbackRate;

      // Apply saved position now that we know the duration
      if (this.savedPosition > 0 && this.savedPosition < this.duration) {
        audio.currentTime = this.savedPosition;
        this.currentTime = this.savedPosition;
      }

      // Setup Media Session for background playback
      this.setupMediaSession();

      // Note: Session will be started when user presses play, not on load
    }
  }

  onTimeUpdate(): void {
    const audio = this.audioElement?.nativeElement;
    if (audio) {
      const previousChapterIndex = this.getCurrentChapterIndex();
      this.currentTime = audio.currentTime;

      // Update audiobook session position
      this.audiobookSessionService.updatePosition(
        Math.round(this.currentTime * 1000),
        this.audiobookInfo?.folderBased ? this.currentTrackIndex : undefined
      );

      // Update Media Session position state (throttled to every 5 seconds)
      if (Math.floor(this.currentTime) % 5 === 0) {
        this.updateMediaSessionPositionState();
      }

      // Update metadata if chapter changed (for single-file audiobooks)
      if (!this.audiobookInfo.folderBased && this.getCurrentChapterIndex() !== previousChapterIndex) {
        this.updateMediaSessionMetadata();
      }

      // Check sleep timer end of chapter
      this.checkSleepTimerEndOfChapter();
    }
  }

  onProgress(): void {
    const audio = this.audioElement?.nativeElement;
    if (audio && audio.buffered.length > 0) {
      this.buffered = audio.buffered.end(audio.buffered.length - 1);
    }
  }

  onAudioEnded(): void {
    // For folder-based audiobooks, play next track
    if (this.audiobookInfo.folderBased && this.audiobookInfo.tracks) {
      if (this.currentTrackIndex < this.audiobookInfo.tracks.length - 1) {
        this.nextTrack();
      } else {
        this.isPlaying = false;
        this.stopProgressSaveInterval();
        this.saveProgress();
        this.updateMediaSessionPlaybackState();
        // Pause session when audiobook ends
        this.audiobookSessionService.pauseSession(Math.round(this.currentTime * 1000));
      }
    } else {
      this.isPlaying = false;
      this.stopProgressSaveInterval();
      this.saveProgress();
      this.updateMediaSessionPlaybackState();
      // Pause session when audiobook ends
      this.audiobookSessionService.pauseSession(Math.round(this.currentTime * 1000));
    }
  }

  onAudioError(): void {
    this.audioLoading = false;
    this.messageService.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load audio'
    });
  }

  // Media Session API for background playback
  private setupMediaSession(): void {
    if (!('mediaSession' in navigator)) return;

    this.updateMediaSessionMetadata();

    navigator.mediaSession.setActionHandler('play', () => {
      if (!this.isPlaying) this.togglePlay();
    });
    navigator.mediaSession.setActionHandler('pause', () => {
      if (this.isPlaying) this.togglePlay();
    });
    navigator.mediaSession.setActionHandler('seekbackward', () => this.seekRelative(-30));
    navigator.mediaSession.setActionHandler('seekforward', () => this.seekRelative(30));
    navigator.mediaSession.setActionHandler('previoustrack', () => {
      this.audiobookInfo.folderBased ? this.previousTrack() : this.previousChapter();
    });
    navigator.mediaSession.setActionHandler('nexttrack', () => {
      this.audiobookInfo.folderBased ? this.nextTrack() : this.nextChapter();
    });
    navigator.mediaSession.setActionHandler('seekto', (details) => {
      if (details.seekTime !== undefined) {
        const audio = this.audioElement?.nativeElement;
        if (audio) {
          audio.currentTime = details.seekTime;
          this.currentTime = details.seekTime;
        }
      }
    });
  }

  private updateMediaSessionMetadata(): void {
    if (!('mediaSession' in navigator)) return;

    const title = this.audiobookInfo.folderBased
      ? this.currentTrack?.title
      : this.getCurrentChapter()?.title || this.audiobookInfo.title;

    navigator.mediaSession.metadata = new MediaMetadata({
      title: title || 'Untitled',
      artist: this.audiobookInfo.author || 'Unknown Author',
      album: this.audiobookInfo.title,
      artwork: this.coverUrl
        ? [{ src: this.coverUrl, sizes: '512x512', type: 'image/png' }]
        : []
    });
  }

  private updateMediaSessionPlaybackState(): void {
    if ('mediaSession' in navigator) {
      navigator.mediaSession.playbackState = this.isPlaying ? 'playing' : 'paused';
    }
  }

  private updateMediaSessionPositionState(): void {
    if ('mediaSession' in navigator && 'setPositionState' in navigator.mediaSession) {
      try {
        navigator.mediaSession.setPositionState({
          duration: this.duration,
          playbackRate: this.playbackRate,
          position: this.currentTime
        });
      } catch {
        // Ignore errors from invalid position state
      }
    }
  }

  // Playback controls
  togglePlay(): void {
    const audio = this.audioElement?.nativeElement;
    if (!audio) return;

    if (this.isPlaying) {
      audio.pause();
      this.stopProgressSaveInterval();
      this.saveProgress();
      // Pause the listening session
      this.audiobookSessionService.pauseSession(Math.round(this.currentTime * 1000));
    } else {
      audio.play();
      this.startProgressSaveInterval();
      // Start or resume the listening session
      if (this.audiobookSessionService.isSessionActive()) {
        this.audiobookSessionService.resumeSession(Math.round(this.currentTime * 1000));
      } else {
        this.audiobookSessionService.startSession(
          this.bookId,
          Math.round(this.currentTime * 1000),
          this.playbackRate,
          this.audiobookInfo?.bookFileId,
          this.audiobookInfo?.folderBased ? this.currentTrackIndex : undefined
        );
      }
    }
    this.isPlaying = !this.isPlaying;
    this.updateMediaSessionPlaybackState();
  }

  seek(event: SliderChangeEvent): void {
    const audio = this.audioElement?.nativeElement;
    if (audio && this.duration > 0 && event.value !== undefined) {
      audio.currentTime = event.value as number;
      this.currentTime = event.value as number;
    }
  }

  seekRelative(seconds: number): void {
    const audio = this.audioElement?.nativeElement;
    if (audio) {
      const newTime = Math.max(0, Math.min(this.duration, audio.currentTime + seconds));
      audio.currentTime = newTime;
      this.currentTime = newTime;
    }
  }

  // Volume controls
  setVolume(event: SliderChangeEvent): void {
    const audio = this.audioElement?.nativeElement;
    if (event.value !== undefined) {
      this.volume = event.value as number;
      this.isMuted = this.volume === 0;
      if (audio) {
        audio.volume = this.volume;
      }
    }
  }

  toggleMute(): void {
    const audio = this.audioElement?.nativeElement;
    if (!audio) return;

    if (this.isMuted) {
      this.volume = this.previousVolume || 0.5;
      audio.volume = this.volume;
      this.isMuted = false;
    } else {
      this.previousVolume = this.volume;
      this.volume = 0;
      audio.volume = 0;
      this.isMuted = true;
    }
  }

  // Playback rate
  setPlaybackRate(rate: number): void {
    if (rate === undefined || rate === null) return;
    const audio = this.audioElement?.nativeElement;
    this.playbackRate = rate;
    if (audio) {
      audio.playbackRate = rate;
    }
    this.updateMediaSessionPositionState();
    // Update session with new playback rate
    this.audiobookSessionService.updatePlaybackRate(rate);
  }

  // Track navigation (folder-based)
  previousTrack(): void {
    if (this.currentTrackIndex > 0) {
      this.loadTrack(this.currentTrackIndex - 1);
      this.currentTime = 0;
      this.savedPosition = 0; // Reset saved position for new track
      if (this.isPlaying) {
        setTimeout(() => {
          this.audioElement?.nativeElement?.play();
          this.updateMediaSessionMetadata();
        }, 100);
      }
    }
  }

  nextTrack(): void {
    if (this.audiobookInfo.tracks && this.currentTrackIndex < this.audiobookInfo.tracks.length - 1) {
      this.loadTrack(this.currentTrackIndex + 1);
      this.currentTime = 0;
      this.savedPosition = 0; // Reset saved position for new track
      if (this.isPlaying) {
        setTimeout(() => {
          this.audioElement?.nativeElement?.play();
          this.updateMediaSessionMetadata();
        }, 100);
      }
    }
  }

  selectTrack(track: AudiobookTrack): void {
    this.loadTrack(track.index);
    this.currentTime = 0;
    this.savedPosition = 0; // Reset saved position for new track
    this.showTrackList = false;
    setTimeout(() => {
      this.audioElement?.nativeElement?.play();
      this.isPlaying = true;
      this.startProgressSaveInterval();
      this.updateMediaSessionMetadata();
      this.updateMediaSessionPlaybackState();
      // Start or resume listening session
      if (this.audiobookSessionService.isSessionActive()) {
        this.audiobookSessionService.resumeSession(0);
      } else {
        this.audiobookSessionService.startSession(
          this.bookId, 0, this.playbackRate,
          this.audiobookInfo?.bookFileId, track.index
        );
      }
    }, 100);
  }

  // Chapter navigation (single-file)
  selectChapter(chapter: AudiobookChapter): void {
    const audio = this.audioElement?.nativeElement;
    if (audio) {
      audio.currentTime = chapter.startTimeMs / 1000;
      this.currentTime = chapter.startTimeMs / 1000;
      this.showTrackList = false;
      this.updateMediaSessionMetadata();
      if (!this.isPlaying) {
        audio.play();
        this.isPlaying = true;
        this.startProgressSaveInterval();
        this.updateMediaSessionPlaybackState();
        // Start or resume listening session
        if (this.audiobookSessionService.isSessionActive()) {
          this.audiobookSessionService.resumeSession(chapter.startTimeMs);
        } else {
          this.audiobookSessionService.startSession(
            this.bookId, chapter.startTimeMs, this.playbackRate,
            this.audiobookInfo?.bookFileId
          );
        }
      }
    }
  }

  getCurrentChapter(): AudiobookChapter | undefined {
    if (!this.audiobookInfo?.chapters) return undefined;
    const currentMs = this.currentTime * 1000;
    return this.audiobookInfo.chapters.find(
      ch => currentMs >= ch.startTimeMs && currentMs < ch.endTimeMs
    );
  }

  getCurrentChapterIndex(): number {
    const chapter = this.getCurrentChapter();
    return chapter?.index ?? 0;
  }

  hasMultipleChapters(): boolean {
    return (this.audiobookInfo?.chapters?.length ?? 0) > 1;
  }

  canGoPreviousChapter(): boolean {
    return this.getCurrentChapterIndex() > 0;
  }

  canGoNextChapter(): boolean {
    const chapters = this.audiobookInfo?.chapters;
    if (!chapters) return false;
    return this.getCurrentChapterIndex() < chapters.length - 1;
  }

  previousChapter(): void {
    const chapters = this.audiobookInfo?.chapters;
    if (!chapters) return;

    const currentIndex = this.getCurrentChapterIndex();
    if (currentIndex > 0) {
      const prevChapter = chapters[currentIndex - 1];
      this.selectChapter(prevChapter);
    }
  }

  nextChapter(): void {
    const chapters = this.audiobookInfo?.chapters;
    if (!chapters) return;

    const currentIndex = this.getCurrentChapterIndex();
    if (currentIndex < chapters.length - 1) {
      const nextChapter = chapters[currentIndex + 1];
      this.selectChapter(nextChapter);
    }
  }

  // Progress management - save every 5 seconds while playing
  private startProgressSaveInterval(): void {
    if (this.progressSaveInterval) return; // Already running

    this.progressSaveInterval = setInterval(() => {
      if (this.isPlaying) {
        this.saveProgress();
      }
    }, 5000);
  }

  private stopProgressSaveInterval(): void {
    if (this.progressSaveInterval) {
      clearInterval(this.progressSaveInterval);
      this.progressSaveInterval = undefined;
    }
  }

  private saveProgress(): void {
    if (!this.audiobookInfo) return;

    const totalDuration = this.getTotalDuration();
    const currentPosition = this.getCurrentTotalPosition();
    const percentage = totalDuration > 0 ? (currentPosition / totalDuration) * 100 : 0;

    // For folder-based: positionMs = track position (for seeking within track)
    // For single-file: positionMs = absolute position
    const positionMs = this.audiobookInfo.folderBased
      ? Math.round(this.currentTime * 1000)  // Track position
      : Math.round(currentPosition * 1000);   // Absolute position

    const progress: AudiobookProgress = {
      positionMs: positionMs,
      trackIndex: this.audiobookInfo.folderBased ? this.currentTrackIndex : undefined,
      percentage: Math.round(percentage * 10) / 10
    };

    this.audiobookService.saveProgress(
      this.bookId,
      progress,
      this.audiobookInfo.bookFileId
    ).subscribe();
  }

  private getTotalDuration(): number {
    if (this.audiobookInfo.folderBased && this.audiobookInfo.tracks) {
      return this.audiobookInfo.tracks.reduce((sum, t) => sum + t.durationMs, 0) / 1000;
    }
    return this.duration;
  }

  private getCurrentTotalPosition(): number {
    if (this.audiobookInfo.folderBased && this.audiobookInfo.tracks) {
      const currentTrack = this.audiobookInfo.tracks[this.currentTrackIndex];
      return (currentTrack?.cumulativeStartMs || 0) / 1000 + this.currentTime;
    }
    return this.currentTime;
  }

  // Utility methods
  formatTime(seconds: number): string {
    if (!seconds || !isFinite(seconds)) return '0:00';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);

    if (h > 0) {
      return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
    }
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  formatDuration(ms: number): string {
    return this.formatTime(ms / 1000);
  }

  getProgressPercent(): number {
    return this.duration > 0 ? (this.currentTime / this.duration) * 100 : 0;
  }

  getBufferedPercent(): number {
    return this.duration > 0 ? (this.buffered / this.duration) * 100 : 0;
  }

  onCoverError(): void {
    // Fallback to book cover if embedded cover fails
    // Only fallback once to prevent infinite loop
    if (this.coverUrl !== this.bookCoverUrl) {
      this.coverUrl = this.bookCoverUrl;
    } else {
      // Both covers failed, use a placeholder
      this.coverUrl = undefined;
    }
  }

  toggleTrackList(): void {
    this.showTrackList = !this.showTrackList;
  }

  closeReader(): void {
    this.saveProgress();
    if (this.audiobookSessionService.isSessionActive()) {
      this.audiobookSessionService.endSession(Math.round(this.currentTime * 1000));
    }
    this.location.back();
  }

  getVolumeIcon(): string {
    if (this.isMuted || this.volume === 0) return 'pi pi-volume-off';
    if (this.volume < 0.5) return 'pi pi-volume-down';
    return 'pi pi-volume-up';
  }

  get currentTrack(): AudiobookTrack | undefined {
    return this.audiobookInfo?.tracks?.[this.currentTrackIndex];
  }

  // ==================== SLEEP TIMER ====================

  setSleepTimer(minutes: number): void {
    this.cancelSleepTimer();
    this.sleepTimerRemaining = minutes * 60;
    this.sleepTimerActive = true;
    this.sleepTimerEndOfChapter = false;
    this.originalVolume = this.volume;
    this.updateSleepTimerMenuVisibility();

    this.sleepTimerInterval = setInterval(() => {
      this.sleepTimerRemaining--;

      // Fade out volume in last 30 seconds
      if (this.sleepTimerRemaining <= 30 && this.sleepTimerRemaining > 0) {
        const fadeRatio = this.sleepTimerRemaining / 30;
        const audio = this.audioElement?.nativeElement;
        if (audio) {
          audio.volume = this.originalVolume * fadeRatio;
        }
      }

      if (this.sleepTimerRemaining <= 0) {
        this.triggerSleepTimerStop();
      }
    }, 1000);

    this.messageService.add({
      severity: 'info',
      summary: 'Sleep Timer',
      detail: `Playback will stop in ${minutes} minutes`
    });
  }

  setSleepTimerEndOfChapter(): void {
    this.cancelSleepTimer();
    this.sleepTimerActive = true;
    this.sleepTimerEndOfChapter = true;
    this.sleepTimerRemaining = 0;
    this.originalVolume = this.volume;
    this.updateSleepTimerMenuVisibility();

    this.messageService.add({
      severity: 'info',
      summary: 'Sleep Timer',
      detail: 'Playback will stop at end of chapter'
    });
  }

  cancelSleepTimer(): void {
    if (this.sleepTimerInterval) {
      clearInterval(this.sleepTimerInterval);
      this.sleepTimerInterval = undefined;
    }

    // Restore original volume if we were fading
    if (this.sleepTimerActive && this.originalVolume > 0) {
      const audio = this.audioElement?.nativeElement;
      if (audio) {
        audio.volume = this.originalVolume;
        this.volume = this.originalVolume;
      }
    }

    this.sleepTimerActive = false;
    this.sleepTimerRemaining = 0;
    this.sleepTimerEndOfChapter = false;
    this.updateSleepTimerMenuVisibility();
  }

  private triggerSleepTimerStop(): void {
    const audio = this.audioElement?.nativeElement;
    if (audio) {
      audio.pause();
      audio.volume = this.originalVolume;
      this.volume = this.originalVolume;
    }
    this.isPlaying = false;
    this.stopProgressSaveInterval();
    this.saveProgress();
    this.cancelSleepTimer();
    this.updateMediaSessionPlaybackState();
    // Pause the listening session
    this.audiobookSessionService.pauseSession(Math.round(this.currentTime * 1000));

    this.messageService.add({
      severity: 'info',
      summary: 'Sleep Timer',
      detail: 'Playback stopped by sleep timer'
    });
  }

  private updateSleepTimerMenuVisibility(): void {
    // Show/hide cancel option based on timer state
    const cancelItem = this.sleepTimerOptions.find(item => item.label === 'Cancel timer');
    if (cancelItem) {
      cancelItem.visible = this.sleepTimerActive;
    }
  }

  formatSleepTimerRemaining(): string {
    if (this.sleepTimerEndOfChapter) {
      return 'End of chapter';
    }
    const minutes = Math.floor(this.sleepTimerRemaining / 60);
    const seconds = this.sleepTimerRemaining % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  // Check for end of chapter in onTimeUpdate for sleep timer
  private checkSleepTimerEndOfChapter(): void {
    if (!this.sleepTimerEndOfChapter || !this.sleepTimerActive) return;

    const currentChapter = this.getCurrentChapter();
    if (currentChapter) {
      const currentMs = this.currentTime * 1000;
      // If we're within 1 second of chapter end, trigger stop
      if (currentMs >= currentChapter.endTimeMs - 1000) {
        this.triggerSleepTimerStop();
      }
    }
  }

  // ==================== BOOKMARKS ====================

  loadBookmarks(): void {
    this.bookMarkService.getBookmarksForBook(this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(bookmarks => {
        this.bookmarks = bookmarks;
      });
  }

  toggleBookmarkList(): void {
    this.showBookmarkList = !this.showBookmarkList;
    if (this.showBookmarkList && this.bookmarks.length === 0) {
      this.loadBookmarks();
    }
  }

  addBookmark(): void {
    const currentChapter = this.getCurrentChapter();
    const currentTrack = this.currentTrack;

    let title: string;
    if (this.audiobookInfo.folderBased && currentTrack) {
      title = `${currentTrack.title} - ${this.formatTime(this.currentTime)}`;
    } else if (currentChapter) {
      title = `${currentChapter.title} - ${this.formatTime(this.currentTime)}`;
    } else {
      title = `Bookmark at ${this.formatTime(this.currentTime)}`;
    }

    const request: CreateBookMarkRequest = {
      bookId: this.bookId,
      title: title,
      positionMs: Math.round(this.currentTime * 1000),
      trackIndex: this.audiobookInfo.folderBased ? this.currentTrackIndex : undefined
    };

    this.bookMarkService.createBookmark(request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (bookmark) => {
          this.bookmarks = [...this.bookmarks, bookmark];
          this.messageService.add({
            severity: 'success',
            summary: 'Bookmark Added',
            detail: title
          });
        },
        error: (err) => {
          const isDuplicate = err?.status === 409;
          this.messageService.add({
            severity: isDuplicate ? 'warn' : 'error',
            summary: isDuplicate ? 'Bookmark Exists' : 'Error',
            detail: isDuplicate ? 'A bookmark already exists at this position' : 'Failed to add bookmark'
          });
        }
      });
  }

  goToBookmark(bookmark: BookMark): void {
    // Handle track switching for folder-based audiobooks
    if (this.audiobookInfo.folderBased && bookmark.trackIndex !== undefined && bookmark.trackIndex !== null) {
      if (bookmark.trackIndex !== this.currentTrackIndex) {
        this.loadTrack(bookmark.trackIndex);
        // Wait for track to load, then seek
        this.savedPosition = (bookmark.positionMs || 0) / 1000;
      } else {
        // Same track, just seek
        const audio = this.audioElement?.nativeElement;
        if (audio && bookmark.positionMs) {
          audio.currentTime = bookmark.positionMs / 1000;
          this.currentTime = bookmark.positionMs / 1000;
        }
      }
    } else {
      // Single-file audiobook
      const audio = this.audioElement?.nativeElement;
      if (audio && bookmark.positionMs) {
        audio.currentTime = bookmark.positionMs / 1000;
        this.currentTime = bookmark.positionMs / 1000;
      }
    }

    this.showBookmarkList = false;

    if (!this.isPlaying) {
      setTimeout(() => {
        this.audioElement?.nativeElement?.play();
        this.isPlaying = true;
        this.startProgressSaveInterval();
        // Start or resume listening session
        const positionMs = bookmark.positionMs || 0;
        if (this.audiobookSessionService.isSessionActive()) {
          this.audiobookSessionService.resumeSession(positionMs);
        } else {
          this.audiobookSessionService.startSession(
            this.bookId, positionMs, this.playbackRate,
            this.audiobookInfo?.bookFileId,
            this.audiobookInfo?.folderBased ? bookmark.trackIndex : undefined
          );
        }
      }, 100);
    }
  }

  deleteBookmark(event: MouseEvent, bookmarkId: number): void {
    event.stopPropagation();
    this.bookMarkService.deleteBookmark(bookmarkId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.bookmarks = this.bookmarks.filter(b => b.id !== bookmarkId);
        this.messageService.add({
          severity: 'info',
          summary: 'Bookmark Deleted'
        });
      });
  }

  formatBookmarkPosition(bookmark: BookMark): string {
    if (bookmark.positionMs) {
      return this.formatTime(bookmark.positionMs / 1000);
    }
    return '';
  }
}
