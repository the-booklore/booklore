import {TestBed} from '@angular/core/testing';
import {vi, describe, it, expect, beforeEach} from 'vitest';
import {RsvpService, RsvpWord} from './rsvp.service';
import {ReaderViewManagerService} from '../../core/view-manager.service';

describe('RsvpService', () => {
  let service: RsvpService;

  const viewManagerMock = {
    getRenderer: vi.fn(() => null)
  };

  beforeEach(() => {
    viewManagerMock.getRenderer.mockClear();

    TestBed.configureTestingModule({
      providers: [
        RsvpService,
        {provide: ReaderViewManagerService, useValue: viewManagerMock}
      ]
    });

    service = TestBed.inject(RsvpService);
  });

  describe('initial state', () => {
    it('should have correct default values', () => {
      const state = service.currentState;
      expect(state.active).toBe(false);
      expect(state.playing).toBe(false);
      expect(state.wpm).toBe(300);
      expect(state.currentIndex).toBe(0);
      expect(state.words).toEqual([]);
      expect(state.progress).toBe(0);
    });

    it('should return null for currentWord when no words loaded', () => {
      expect(service.currentWord).toBeNull();
    });
  });

  describe('ORP (Optimal Recognition Point) calculation', () => {
    // Access private method via any cast for testing
    const calculateORP = (word: string): number => {
      return (service as any).calculateORP(word);
    };

    it('should return 0 for single character words', () => {
      expect(calculateORP('a')).toBe(0);
      expect(calculateORP('I')).toBe(0);
    });

    it('should return 0 for 2-3 letter words', () => {
      expect(calculateORP('to')).toBe(0);
      expect(calculateORP('the')).toBe(0);
      expect(calculateORP('an')).toBe(0);
    });

    it('should return 1 for 4-5 letter words', () => {
      expect(calculateORP('word')).toBe(1);
      expect(calculateORP('books')).toBe(1);
    });

    it('should return 2 for 6-8 letter words', () => {
      expect(calculateORP('reading')).toBe(2);
      expect(calculateORP('chapters')).toBe(2);
    });

    it('should return 3 for words longer than 8 characters', () => {
      expect(calculateORP('comfortable')).toBe(3);
      expect(calculateORP('understanding')).toBe(3);
    });

    it('should ignore punctuation when calculating ORP', () => {
      expect(calculateORP('word,')).toBe(1);
      expect(calculateORP('"hello"')).toBe(1);
      expect(calculateORP('end.')).toBe(0);
    });
  });

  describe('pause multiplier calculation', () => {
    const getPauseMultiplier = (word: string): number => {
      return (service as any).getPauseMultiplier(word);
    };

    it('should return 2.0 for sentence-ending punctuation', () => {
      expect(getPauseMultiplier('done.')).toBe(2.0);
      expect(getPauseMultiplier('what?')).toBe(2.0);
      expect(getPauseMultiplier('wow!')).toBe(2.0);
    });

    it('should return 1.5 for comma and semicolon', () => {
      expect(getPauseMultiplier('however,')).toBe(1.5);
      expect(getPauseMultiplier('first;')).toBe(1.5);
      expect(getPauseMultiplier('namely:')).toBe(1.5);
    });

    it('should return 1.3 for very long words (>12 chars)', () => {
      expect(getPauseMultiplier('internationally')).toBe(1.3);
      expect(getPauseMultiplier('authentication')).toBe(1.3);
    });

    it('should return 1.1 for long words (9-12 chars)', () => {
      expect(getPauseMultiplier('important')).toBe(1.1);
      expect(getPauseMultiplier('complexity')).toBe(1.1);
    });

    it('should return 1.0 for regular words', () => {
      expect(getPauseMultiplier('the')).toBe(1.0);
      expect(getPauseMultiplier('reading')).toBe(1.0);
    });

    it('should prioritize punctuation over word length', () => {
      // Even though "internationally." is long, punctuation takes precedence
      expect(getPauseMultiplier('internationally.')).toBe(2.0);
    });
  });

  describe('word display duration', () => {
    const getWordDisplayDuration = (word: RsvpWord, wpm: number): number => {
      return (service as any).getWordDisplayDuration(word, wpm);
    };

    it('should calculate base duration from WPM', () => {
      const word: RsvpWord = {text: 'test', orpIndex: 1, pauseMultiplier: 1.0};
      // 300 WPM = 200ms per word
      expect(getWordDisplayDuration(word, 300)).toBe(200);
      // 600 WPM = 100ms per word
      expect(getWordDisplayDuration(word, 600)).toBe(100);
    });

    it('should apply pause multiplier to base duration', () => {
      const wordWithPause: RsvpWord = {text: 'end.', orpIndex: 0, pauseMultiplier: 2.0};
      // 300 WPM = 200ms base, with 2.0 multiplier = 400ms
      expect(getWordDisplayDuration(wordWithPause, 300)).toBe(400);
    });
  });

  describe('WPM controls', () => {
    it('should increase WPM by step amount', () => {
      const initialWpm = service.currentState.wpm;
      service.increaseSpeed();
      expect(service.currentState.wpm).toBe(initialWpm + 50);
    });

    it('should decrease WPM by step amount', () => {
      const initialWpm = service.currentState.wpm;
      service.decreaseSpeed();
      expect(service.currentState.wpm).toBe(initialWpm - 50);
    });

    it('should not exceed maximum WPM (1000)', () => {
      // Set to near max
      service.setWpm(980);
      service.increaseSpeed();
      expect(service.currentState.wpm).toBe(1000);

      // Try to increase beyond max
      service.increaseSpeed();
      expect(service.currentState.wpm).toBe(1000);
    });

    it('should not go below minimum WPM (100)', () => {
      // Set to near min
      service.setWpm(120);
      service.decreaseSpeed();
      expect(service.currentState.wpm).toBe(100);

      // Try to decrease below min
      service.decreaseSpeed();
      expect(service.currentState.wpm).toBe(100);
    });

    it('should clamp setWpm to valid range', () => {
      service.setWpm(50);
      expect(service.currentState.wpm).toBe(100);

      service.setWpm(2000);
      expect(service.currentState.wpm).toBe(1000);

      service.setWpm(500);
      expect(service.currentState.wpm).toBe(500);
    });
  });

  describe('skip controls', () => {
    beforeEach(() => {
      // Manually set up state with words for skip testing
      const words: RsvpWord[] = Array.from({length: 100}, (_, i) => ({
        text: `word${i}`,
        orpIndex: 1,
        pauseMultiplier: 1.0
      }));

      (service as any).stateSubject.next({
        active: true,
        playing: false,
        words,
        currentIndex: 50,
        wpm: 300,
        progress: 50,
        resumedFromIndex: null
      });
    });

    it('should skip forward by specified count', () => {
      service.skipForward(15);
      expect(service.currentState.currentIndex).toBe(65);
    });

    it('should skip backward by specified count', () => {
      service.skipBackward(15);
      expect(service.currentState.currentIndex).toBe(35);
    });

    it('should not skip forward past end of words', () => {
      service.skipForward(100);
      expect(service.currentState.currentIndex).toBe(99); // Last word index
    });

    it('should not skip backward past beginning', () => {
      service.skipBackward(100);
      expect(service.currentState.currentIndex).toBe(0);
    });

    it('should update progress when skipping', () => {
      service.skipForward(10);
      expect(service.currentState.progress).toBe(60);

      service.skipBackward(20);
      expect(service.currentState.progress).toBe(40);
    });
  });

  describe('CFI comparison', () => {
    const extractBaseCfi = (cfi: string): string => {
      return (service as any).extractBaseCfi(cfi);
    };

    const isSameSection = (cfi1: string | null, cfi2: string | null): boolean => {
      return (service as any).isSameSection(cfi1, cfi2);
    };

    it('should extract base CFI without position specifics', () => {
      const cfi = 'epubcfi(/6/30!/4[chapter1]/2[section],/1:0,/1:50)';
      const base = extractBaseCfi(cfi);
      expect(base).toContain('/6/30');
      expect(base).not.toContain(':0');
      expect(base).not.toContain(':50');
    });

    it('should identify same section CFIs', () => {
      const cfi1 = 'epubcfi(/6/30!/4[chapter1],/1:0,/1:50)';
      const cfi2 = 'epubcfi(/6/30!/4[chapter1],/1:100,/1:150)';
      expect(isSameSection(cfi1, cfi2)).toBe(true);
    });

    it('should identify different section CFIs', () => {
      const cfi1 = 'epubcfi(/6/30!/4[chapter1],/1:0,/1:50)';
      const cfi2 = 'epubcfi(/6/32!/4[chapter2],/1:0,/1:50)';
      expect(isSameSection(cfi1, cfi2)).toBe(false);
    });

    it('should return false when either CFI is null', () => {
      expect(isSameSection(null, 'epubcfi(/6/30!/4[chapter1])')).toBe(false);
      expect(isSameSection('epubcfi(/6/30!/4[chapter1])', null)).toBe(false);
      expect(isSameSection(null, null)).toBe(false);
    });
  });

  describe('selection matching', () => {
    const findWordIndexBySelection = (words: RsvpWord[], selectionText: string): number => {
      return (service as any).findWordIndexBySelection(words, selectionText);
    };

    const createWords = (texts: string[]): RsvpWord[] => {
      return texts.map(text => ({
        text,
        orpIndex: 1,
        pauseMultiplier: 1.0
      }));
    };

    it('should find exact single word match', () => {
      const words = createWords(['The', 'quick', 'brown', 'fox', 'jumps']);
      expect(findWordIndexBySelection(words, 'brown')).toBe(2);
    });

    it('should find multi-word selection', () => {
      const words = createWords(['The', 'quick', 'brown', 'fox', 'jumps']);
      expect(findWordIndexBySelection(words, 'quick brown')).toBe(1);
    });

    it('should be case insensitive', () => {
      const words = createWords(['The', 'Quick', 'Brown', 'Fox']);
      expect(findWordIndexBySelection(words, 'quick')).toBe(1);
      expect(findWordIndexBySelection(words, 'BROWN')).toBe(2);
    });

    it('should return -1 when no match found', () => {
      const words = createWords(['The', 'quick', 'brown', 'fox']);
      expect(findWordIndexBySelection(words, 'elephant')).toBe(-1);
    });

    it('should return -1 for empty selection', () => {
      const words = createWords(['The', 'quick', 'brown']);
      expect(findWordIndexBySelection(words, '')).toBe(-1);
      expect(findWordIndexBySelection(words, '   ')).toBe(-1);
    });

    it('should return -1 for empty words array', () => {
      expect(findWordIndexBySelection([], 'test')).toBe(-1);
    });
  });

  describe('play/pause state', () => {
    it('should toggle play/pause', () => {
      // Start with words loaded but paused
      (service as any).stateSubject.next({
        ...service.currentState,
        active: true,
        playing: false,
        words: [{text: 'test', orpIndex: 1, pauseMultiplier: 1.0}]
      });

      expect(service.currentState.playing).toBe(false);
    });

    it('should pause playback', () => {
      (service as any).stateSubject.next({
        ...service.currentState,
        active: true,
        playing: true
      });

      service.pause();
      expect(service.currentState.playing).toBe(false);
    });
  });

  describe('stop', () => {
    it('should reset state when stopped', () => {
      // Set up active state
      (service as any).stateSubject.next({
        active: true,
        playing: true,
        words: [{text: 'test', orpIndex: 1, pauseMultiplier: 1.0}],
        currentIndex: 0,
        wpm: 300,
        progress: 50,
        resumedFromIndex: 10
      });

      service.stop();

      const state = service.currentState;
      expect(state.active).toBe(false);
      expect(state.playing).toBe(false);
      expect(state.words).toEqual([]);
      expect(state.currentIndex).toBe(0);
      expect(state.progress).toBe(0);
    });
  });

  describe('currentWord getter', () => {
    it('should return the word at current index', () => {
      const words: RsvpWord[] = [
        {text: 'first', orpIndex: 1, pauseMultiplier: 1.0},
        {text: 'second', orpIndex: 1, pauseMultiplier: 1.0},
        {text: 'third', orpIndex: 1, pauseMultiplier: 1.0}
      ];

      (service as any).stateSubject.next({
        ...service.currentState,
        words,
        currentIndex: 1
      });

      expect(service.currentWord?.text).toBe('second');
    });

    it('should return null when index is out of bounds', () => {
      const words: RsvpWord[] = [
        {text: 'only', orpIndex: 1, pauseMultiplier: 1.0}
      ];

      (service as any).stateSubject.next({
        ...service.currentState,
        words,
        currentIndex: 5
      });

      expect(service.currentWord).toBeNull();
    });
  });
});
