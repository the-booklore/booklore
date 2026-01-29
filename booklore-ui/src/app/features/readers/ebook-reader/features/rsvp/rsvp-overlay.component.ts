import {Component, EventEmitter, HostListener, inject, Input, OnDestroy, OnInit, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {RsvpService, RsvpState, RsvpWord} from './rsvp.service';
import {Theme} from '../../state/themes.constant';

@Component({
  selector: 'app-rsvp-overlay',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './rsvp-overlay.component.html',
  styleUrls: ['./rsvp-overlay.component.scss']
})
export class RsvpOverlayComponent implements OnInit, OnDestroy {
  @Input() theme!: Theme;
  @Output() close = new EventEmitter<void>();
  @Output() requestNextPage = new EventEmitter<void>();

  private rsvpService = inject(RsvpService);
  private destroy$ = new Subject<void>();

  state: RsvpState | null = null;
  currentWord: RsvpWord | null = null;

  private touchStartX = 0;
  private touchStartY = 0;
  private touchStartTime = 0;
  private readonly SWIPE_THRESHOLD = 50;
  private readonly TAP_THRESHOLD = 10;

  get wordBefore(): string {
    if (!this.currentWord) return '';
    return this.currentWord.text.substring(0, this.currentWord.orpIndex);
  }

  get orpChar(): string {
    if (!this.currentWord) return '';
    return this.currentWord.text.charAt(this.currentWord.orpIndex);
  }

  get wordAfter(): string {
    if (!this.currentWord) return '';
    return this.currentWord.text.substring(this.currentWord.orpIndex + 1);
  }

  ngOnInit(): void {
    this.rsvpService.state$
      .pipe(takeUntil(this.destroy$))
      .subscribe(state => {
        this.state = state;
        this.currentWord = this.rsvpService.currentWord;
      });

    this.rsvpService.requestNextPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.requestNextPage.emit();
      });

    // Don't call start() here - the parent component is responsible for starting RSVP
    // via startFromBeginning() or startFromSavedPosition()
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onClose(): void {
    this.rsvpService.stop();
    this.close.emit();
  }

  onPlayPause(): void {
    this.rsvpService.togglePlayPause();
  }

  onSkipBackward(): void {
    this.rsvpService.skipBackward(10);
  }

  onSkipForward(): void {
    this.rsvpService.skipForward(10);
  }

  onDecreaseSpeed(): void {
    this.rsvpService.decreaseSpeed();
  }

  onIncreaseSpeed(): void {
    this.rsvpService.increaseSpeed();
  }

  @HostListener('document:keydown', ['$event'])
  handleKeyboard(event: KeyboardEvent): void {
    if (!this.state?.active) return;

    switch (event.key) {
      case ' ':
        event.preventDefault();
        this.onPlayPause();
        break;
      case 'Escape':
        event.preventDefault();
        this.onClose();
        break;
      case 'ArrowLeft':
        event.preventDefault();
        if (event.shiftKey) {
          this.onSkipBackward();
        } else {
          this.onDecreaseSpeed();
        }
        break;
      case 'ArrowRight':
        event.preventDefault();
        if (event.shiftKey) {
          this.onSkipForward();
        } else {
          this.onIncreaseSpeed();
        }
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.onIncreaseSpeed();
        break;
      case 'ArrowDown':
        event.preventDefault();
        this.onDecreaseSpeed();
        break;
    }
  }

  onTouchStart(event: TouchEvent): void {
    if (event.touches.length !== 1) return;
    const touch = event.touches[0];
    this.touchStartX = touch.clientX;
    this.touchStartY = touch.clientY;
    this.touchStartTime = Date.now();
  }

  onTouchEnd(event: TouchEvent): void {
    if (event.changedTouches.length !== 1) return;

    const touch = event.changedTouches[0];
    const deltaX = touch.clientX - this.touchStartX;
    const deltaY = touch.clientY - this.touchStartY;
    const duration = Date.now() - this.touchStartTime;

    if (Math.abs(deltaX) > this.SWIPE_THRESHOLD && Math.abs(deltaX) > Math.abs(deltaY)) {
      if (deltaX > 0) {
        this.onDecreaseSpeed();
      } else {
        this.onIncreaseSpeed();
      }
      return;
    }

    if (Math.abs(deltaX) < this.TAP_THRESHOLD && Math.abs(deltaY) < this.TAP_THRESHOLD && duration < 300) {
      const target = event.target as HTMLElement;
      if (target.closest('.rsvp-controls') || target.closest('.rsvp-header')) {
        return;
      }

      const screenWidth = window.innerWidth;
      const tapX = touch.clientX;

      if (tapX < screenWidth * 0.25) {
        this.onSkipBackward();
      } else if (tapX > screenWidth * 0.75) {
        this.onSkipForward();
      } else {
        this.onPlayPause();
      }
    }
  }

  onProgressBarClick(event: MouseEvent): void {
    const target = event.currentTarget as HTMLElement;
    const rect = target.getBoundingClientRect();
    const clickX = event.clientX - rect.left;
    const percentage = (clickX / rect.width) * 100;

    if (this.state && this.state.words.length > 0) {
      const newIndex = Math.floor((percentage / 100) * this.state.words.length);
      const wasPlaying = this.state.playing;

      if (wasPlaying) {
        this.rsvpService.pause();
      }

      const currentIndex = this.state.currentIndex;
      const diff = newIndex - currentIndex;

      if (diff > 0) {
        this.rsvpService.skipForward(diff);
      } else if (diff < 0) {
        this.rsvpService.skipBackward(-diff);
      }

      if (wasPlaying) {
        setTimeout(() => this.rsvpService.resume(), 50);
      }
    }
  }
}
