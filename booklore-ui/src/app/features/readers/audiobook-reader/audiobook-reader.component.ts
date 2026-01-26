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

import { AudiobookService } from './audiobook.service';
import { AudiobookInfo, AudiobookChapter, AudiobookTrack, AudiobookProgress } from './audiobook.model';
import { BookService } from '../../book/service/book.service';
import { ReadingSessionService } from '../../../shared/service/reading-session.service';
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
    SelectButton
  ],
  templateUrl: './audiobook-reader.component.html',
  styleUrls: ['./audiobook-reader.component.scss']
})
export class AudiobookReaderComponent implements OnInit, OnDestroy {
  @ViewChild('audioElement') audioElement!: ElementRef<HTMLAudioElement>;

  private destroy$ = new Subject<void>();
  private audiobookService = inject(AudiobookService);
  private bookService = inject(BookService);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private location = inject(Location);
  private messageService = inject(MessageService);
  private readingSessionService = inject(ReadingSessionService);
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

    this.saveProgress();

    if (this.readingSessionService.isSessionActive()) {
      const percentage = this.duration > 0 ? (this.currentTime / this.duration) * 100 : 0;
      this.readingSessionService.endSession(this.formatTime(this.currentTime), percentage);
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

      // Start reading session
      const percentage = this.duration > 0 ? (this.currentTime / this.duration) * 100 : 0;
      this.readingSessionService.startSession(
        this.bookId,
        'AUDIOBOOK',
        this.formatTime(this.currentTime),
        percentage
      );
    }
  }

  onTimeUpdate(): void {
    const audio = this.audioElement?.nativeElement;
    if (audio) {
      this.currentTime = audio.currentTime;

      // Update reading session
      const percentage = this.duration > 0 ? (this.currentTime / this.duration) * 100 : 0;
      this.readingSessionService.updateProgress(this.formatTime(this.currentTime), percentage);
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
      }
    } else {
      this.isPlaying = false;
      this.stopProgressSaveInterval();
      this.saveProgress();
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

  // Playback controls
  togglePlay(): void {
    const audio = this.audioElement?.nativeElement;
    if (!audio) return;

    if (this.isPlaying) {
      audio.pause();
      this.stopProgressSaveInterval();
      this.saveProgress(); // Save on pause
    } else {
      audio.play();
      this.startProgressSaveInterval();
    }
    this.isPlaying = !this.isPlaying;
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
  }

  // Track navigation (folder-based)
  previousTrack(): void {
    if (this.currentTrackIndex > 0) {
      this.loadTrack(this.currentTrackIndex - 1);
      this.currentTime = 0;
      this.savedPosition = 0; // Reset saved position for new track
      if (this.isPlaying) {
        setTimeout(() => this.audioElement?.nativeElement?.play(), 100);
      }
    }
  }

  nextTrack(): void {
    if (this.audiobookInfo.tracks && this.currentTrackIndex < this.audiobookInfo.tracks.length - 1) {
      this.loadTrack(this.currentTrackIndex + 1);
      this.currentTime = 0;
      this.savedPosition = 0; // Reset saved position for new track
      if (this.isPlaying) {
        setTimeout(() => this.audioElement?.nativeElement?.play(), 100);
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
    }, 100);
  }

  // Chapter navigation (single-file)
  selectChapter(chapter: AudiobookChapter): void {
    const audio = this.audioElement?.nativeElement;
    if (audio) {
      audio.currentTime = chapter.startTimeMs / 1000;
      this.currentTime = chapter.startTimeMs / 1000;
      this.showTrackList = false;
      if (!this.isPlaying) {
        audio.play();
        this.isPlaying = true;
        this.startProgressSaveInterval();
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
    if (this.readingSessionService.isSessionActive()) {
      const percentage = this.duration > 0 ? (this.currentTime / this.duration) * 100 : 0;
      this.readingSessionService.endSession(this.formatTime(this.currentTime), percentage);
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
}
