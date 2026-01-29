import {ComponentFixture, TestBed} from '@angular/core/testing';
import {vi, describe, it, expect, beforeEach} from 'vitest';
import {RsvpOverlayComponent, FlatChapter} from './rsvp-overlay.component';
import {RsvpService, RsvpState, RsvpWord} from './rsvp.service';
import {BehaviorSubject, Subject} from 'rxjs';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TocItem} from '../../core/view-manager.service';

describe('RsvpOverlayComponent', () => {
  let component: RsvpOverlayComponent;
  let fixture: ComponentFixture<RsvpOverlayComponent>;
  let stateSubject: BehaviorSubject<RsvpState>;
  let countdownSubject: BehaviorSubject<number | null>;
  let requestNextPageSubject: Subject<void>;
  let rsvpServiceMock: any;

  const mockTheme = {
    bg: '#1a1a2e',
    fg: '#e0e0e0',
    link: '#ff6b6b',
    name: 'dark'
  };

  const createMockState = (overrides: Partial<RsvpState> = {}): RsvpState => ({
    active: true,
    playing: false,
    words: [],
    currentIndex: 0,
    wpm: 300,
    progress: 0,
    resumedFromIndex: null,
    ...overrides
  });

  const createMockWords = (count: number): RsvpWord[] => {
    return Array.from({length: count}, (_, i) => ({
      text: `word${i}`,
      orpIndex: 1,
      pauseMultiplier: 1.0
    }));
  };

  beforeEach(async () => {
    stateSubject = new BehaviorSubject<RsvpState>(createMockState());
    countdownSubject = new BehaviorSubject<number | null>(null);
    requestNextPageSubject = new Subject<void>();

    rsvpServiceMock = {
      state$: stateSubject.asObservable(),
      countdown$: countdownSubject.asObservable(),
      requestNextPage$: requestNextPageSubject.asObservable(),
      get currentWord() {
        const state = stateSubject.value;
        if (state.currentIndex >= 0 && state.currentIndex < state.words.length) {
          return state.words[state.currentIndex];
        }
        return null;
      },
      togglePlayPause: vi.fn(),
      pause: vi.fn(),
      resume: vi.fn(),
      stop: vi.fn(),
      increaseSpeed: vi.fn(),
      decreaseSpeed: vi.fn(),
      skipForward: vi.fn(),
      skipBackward: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [RsvpOverlayComponent, CommonModule, FormsModule],
      providers: [
        {provide: RsvpService, useValue: rsvpServiceMock}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RsvpOverlayComponent);
    component = fixture.componentInstance;
    component.theme = mockTheme as any;
    component.chapters = [];
    fixture.detectChanges();
  });

  describe('word display getters', () => {
    const setCurrentWord = (text: string, orpIndex: number) => {
      const words: RsvpWord[] = [{text, orpIndex, pauseMultiplier: 1.0}];
      stateSubject.next(createMockState({words, currentIndex: 0}));
      fixture.detectChanges();
      // Manually update currentWord since mock doesn't auto-update
      (component as any).currentWord = words[0];
    };

    it('should split word correctly at ORP position', () => {
      setCurrentWord('reading', 2);
      expect(component.wordBefore).toBe('re');
      expect(component.orpChar).toBe('a');
      expect(component.wordAfter).toBe('ding');
    });

    it('should handle ORP at beginning', () => {
      setCurrentWord('the', 0);
      expect(component.wordBefore).toBe('');
      expect(component.orpChar).toBe('t');
      expect(component.wordAfter).toBe('he');
    });

    it('should handle ORP at end', () => {
      setCurrentWord('cat', 2);
      expect(component.wordBefore).toBe('ca');
      expect(component.orpChar).toBe('t');
      expect(component.wordAfter).toBe('');
    });

    it('should return empty strings when no current word', () => {
      (component as any).currentWord = null;
      expect(component.wordBefore).toBe('');
      expect(component.orpChar).toBe('');
      expect(component.wordAfter).toBe('');
    });
  });

  describe('timeRemaining', () => {
    it('should return null when no words', () => {
      stateSubject.next(createMockState({words: [], wpm: 300}));
      fixture.detectChanges();
      expect(component.timeRemaining).toBeNull();
    });

    it('should show seconds when under 1 minute', () => {
      // 10 words at 300 WPM = 2 seconds
      const words = createMockWords(10);
      stateSubject.next(createMockState({words, currentIndex: 0, wpm: 300}));
      fixture.detectChanges();
      expect(component.timeRemaining).toBe('2s');
    });

    it('should show minutes and seconds when under 1 hour', () => {
      // 450 words at 300 WPM = 1.5 minutes = 1m 30s
      const words = createMockWords(450);
      stateSubject.next(createMockState({words, currentIndex: 0, wpm: 300}));
      fixture.detectChanges();
      expect(component.timeRemaining).toBe('1m 30s');
    });

    it('should show hours and minutes when over 1 hour', () => {
      // 30000 words at 300 WPM = 100 minutes = 1h 40m
      const words = createMockWords(30000);
      stateSubject.next(createMockState({words, currentIndex: 0, wpm: 300}));
      fixture.detectChanges();
      expect(component.timeRemaining).toBe('1h 40m');
    });

    it('should calculate based on remaining words', () => {
      // 100 words total, at index 50, 50 remaining at 300 WPM = 10 seconds
      const words = createMockWords(100);
      stateSubject.next(createMockState({words, currentIndex: 50, wpm: 300}));
      fixture.detectChanges();
      expect(component.timeRemaining).toBe('10s');
    });
  });

  describe('context text', () => {
    it('should return empty strings when no words', () => {
      stateSubject.next(createMockState({words: []}));
      fixture.detectChanges();
      expect(component.contextBefore).toBe('');
      expect(component.contextAfter).toBe('');
    });

    it('should show words before current position', () => {
      const words = createMockWords(10);
      stateSubject.next(createMockState({words, currentIndex: 5}));
      fixture.detectChanges();

      const contextBefore = component.contextBefore;
      expect(contextBefore).toContain('word0');
      expect(contextBefore).toContain('word4');
      expect(contextBefore).not.toContain('word5');
    });

    it('should show words after current position', () => {
      const words = createMockWords(10);
      stateSubject.next(createMockState({words, currentIndex: 5}));
      fixture.detectChanges();

      const contextAfter = component.contextAfter;
      expect(contextAfter).toContain('word6');
      expect(contextAfter).toContain('word9');
      expect(contextAfter).not.toContain('word5');
    });

    it('should return current word text', () => {
      const words: RsvpWord[] = [{text: 'current', orpIndex: 1, pauseMultiplier: 1.0}];
      stateSubject.next(createMockState({words, currentIndex: 0}));
      (component as any).currentWord = words[0];
      fixture.detectChanges();

      expect(component.currentWordText).toBe('current');
    });
  });

  describe('chapter flattening', () => {
    it('should flatten nested chapters with correct levels', () => {
      const chapters: TocItem[] = [
        {
          label: 'Chapter 1',
          href: 'ch1.html',
          subitems: [
            {label: 'Section 1.1', href: 'ch1-1.html', subitems: []},
            {label: 'Section 1.2', href: 'ch1-2.html', subitems: []}
          ]
        },
        {
          label: 'Chapter 2',
          href: 'ch2.html',
          subitems: []
        }
      ];

      component.chapters = chapters;
      component.ngOnInit();

      expect(component.flatChapters.length).toBe(4);
      expect(component.flatChapters[0]).toEqual({label: 'Chapter 1', href: 'ch1.html', level: 0});
      expect(component.flatChapters[1]).toEqual({label: 'Section 1.1', href: 'ch1-1.html', level: 1});
      expect(component.flatChapters[2]).toEqual({label: 'Section 1.2', href: 'ch1-2.html', level: 1});
      expect(component.flatChapters[3]).toEqual({label: 'Chapter 2', href: 'ch2.html', level: 0});
    });
  });

  describe('chapter selection', () => {
    beforeEach(() => {
      component.flatChapters = [
        {label: 'Intro', href: 'intro.html', level: 0},
        {label: 'Chapter 1', href: 'chapter1.html', level: 0}
      ];
    });

    it('should return chapter label for matching href', () => {
      component.currentChapterHref = 'chapter1.html';
      expect(component.currentChapterLabel).toBe('Chapter 1');
    });

    it('should return "Select Chapter" when no match', () => {
      component.currentChapterHref = 'unknown.html';
      expect(component.currentChapterLabel).toBe('Select Chapter');
    });

    it('should return "Select Chapter" when href is null', () => {
      component.currentChapterHref = null;
      expect(component.currentChapterLabel).toBe('Select Chapter');
    });

    it('should match href ignoring fragment and leading slash', () => {
      component.flatChapters = [{label: 'Test', href: 'test.html', level: 0}];
      component.currentChapterHref = '/test.html#section1';
      expect(component.currentChapterLabel).toBe('Test');
    });

    it('should correctly identify active chapter', () => {
      component.currentChapterHref = 'intro.html';
      expect(component.isChapterActive('intro.html')).toBe(true);
      expect(component.isChapterActive('chapter1.html')).toBe(false);
    });
  });

  describe('dropdown toggle', () => {
    it('should toggle chapter dropdown visibility', () => {
      expect(component.showChapterDropdown).toBe(false);
      component.toggleChapterDropdown();
      expect(component.showChapterDropdown).toBe(true);
      component.toggleChapterDropdown();
      expect(component.showChapterDropdown).toBe(false);
    });
  });

  describe('control actions', () => {
    it('should call service methods for playback controls', () => {
      component.onPlayPause();
      expect(rsvpServiceMock.togglePlayPause).toHaveBeenCalled();

      component.onSkipForward();
      expect(rsvpServiceMock.skipForward).toHaveBeenCalledWith(15);

      component.onSkipBackward();
      expect(rsvpServiceMock.skipBackward).toHaveBeenCalledWith(15);
    });

    it('should call service methods for speed controls', () => {
      component.onIncreaseSpeed();
      expect(rsvpServiceMock.increaseSpeed).toHaveBeenCalled();

      component.onDecreaseSpeed();
      expect(rsvpServiceMock.decreaseSpeed).toHaveBeenCalled();
    });

    it('should stop service and emit close on close', () => {
      const closeSpy = vi.spyOn(component.close, 'emit');
      component.onClose();
      expect(rsvpServiceMock.stop).toHaveBeenCalled();
      expect(closeSpy).toHaveBeenCalled();
    });
  });
});
