import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, Subject} from 'rxjs';
import {ReaderViewManagerService} from '../../core/view-manager.service';

export interface RsvpWord {
  text: string;
  orpIndex: number;
  pauseMultiplier: number;
  range?: Range;
  docIndex?: number;
}

export interface RsvpState {
  active: boolean;
  playing: boolean;
  words: RsvpWord[];
  currentIndex: number;
  wpm: number;
  progress: number;
  resumedFromIndex: number | null;
}

export interface RsvpPosition {
  cfi: string;
  wordIndex: number;
  wordText: string;
}

@Injectable()
export class RsvpService {
  private viewManager = inject(ReaderViewManagerService);

  private readonly DEFAULT_WPM = 300;
  private readonly MIN_WPM = 100;
  private readonly MAX_WPM = 1000;
  private readonly WPM_STEP = 50;
  private readonly STORAGE_KEY_PREFIX = 'booklore_rsvp_wpm_';
  private readonly POSITION_KEY_PREFIX = 'booklore_rsvp_pos_';

  private bookId: number | null = null;
  private currentCfi: string | null = null;

  private stateSubject = new BehaviorSubject<RsvpState>({
    active: false,
    playing: false,
    words: [],
    currentIndex: 0,
    wpm: this.DEFAULT_WPM,
    progress: 0,
    resumedFromIndex: null
  });

  public state$ = this.stateSubject.asObservable();

  private requestNextPage = new Subject<void>();
  public requestNextPage$ = this.requestNextPage.asObservable();

  private stopPosition = new Subject<{wordIndex: number; totalWords: number; text: string; range?: Range; docIndex?: number} | null>();
  public stopPosition$ = this.stopPosition.asObservable();

  private showStartChoice = new Subject<{
    hasSavedPosition: boolean;
    hasSelection: boolean;
    selectionText?: string;
    firstVisibleWordIndex: number;
  }>();
  public showStartChoice$ = this.showStartChoice.asObservable();

  private pendingStartWordIndex: number | null = null;

  private playbackTimer: ReturnType<typeof setTimeout> | null = null;

  get currentState(): RsvpState {
    return this.stateSubject.value;
  }

  get currentWord(): RsvpWord | null {
    const state = this.currentState;
    if (state.currentIndex >= 0 && state.currentIndex < state.words.length) {
      return state.words[state.currentIndex];
    }
    return null;
  }

  initialize(bookId: number): void {
    this.bookId = bookId;
    const savedWpm = this.loadWpmFromStorage();
    if (savedWpm) {
      this.updateState({wpm: savedWpm});
    }
  }

  setWpm(wpm: number): void {
    const clampedWpm = Math.max(this.MIN_WPM, Math.min(this.MAX_WPM, wpm));
    this.updateState({wpm: clampedWpm});
    this.saveWpmToStorage(clampedWpm);
  }

  private loadWpmFromStorage(): number | null {
    if (!this.bookId) return null;
    const stored = localStorage.getItem(`${this.STORAGE_KEY_PREFIX}${this.bookId}`);
    if (stored) {
      const parsed = parseInt(stored, 10);
      if (!isNaN(parsed) && parsed >= this.MIN_WPM && parsed <= this.MAX_WPM) {
        return parsed;
      }
    }
    return null;
  }

  private saveWpmToStorage(wpm: number): void {
    if (!this.bookId) return;
    localStorage.setItem(`${this.STORAGE_KEY_PREFIX}${this.bookId}`, wpm.toString());
  }

  setCurrentCfi(cfi: string | null): void {
    this.currentCfi = cfi;
  }

  private loadPositionFromStorage(): RsvpPosition | null {
    if (!this.bookId) return null;
    const stored = localStorage.getItem(`${this.POSITION_KEY_PREFIX}${this.bookId}`);
    if (stored) {
      try {
        return JSON.parse(stored) as RsvpPosition;
      } catch {
        return null;
      }
    }
    return null;
  }

  private savePositionToStorage(): void {
    if (!this.bookId || !this.currentCfi) return;
    const state = this.currentState;
    if (state.words.length === 0) return;

    const position: RsvpPosition = {
      cfi: this.currentCfi,
      wordIndex: state.currentIndex,
      wordText: state.words[state.currentIndex]?.text || ''
    };
    localStorage.setItem(`${this.POSITION_KEY_PREFIX}${this.bookId}`, JSON.stringify(position));
  }

  private clearPositionFromStorage(): void {
    if (!this.bookId) return;
    localStorage.removeItem(`${this.POSITION_KEY_PREFIX}${this.bookId}`);
  }

  /**
   * Extract the base section identifier from a CFI for comparison.
   * CFIs contain exact character positions that change on scroll, so we only
   * compare the spine/document/section parts to determine if we're on the same "page".
   * Example: "epubcfi(/6/30!/4[id]/2[section],...)" -> "/6/30!/4[id]/2[section]"
   */
  private extractBaseCfi(cfi: string): string {
    // Remove the "epubcfi(" prefix and ")" suffix
    let inner = cfi.replace(/^epubcfi\(/, '').replace(/\)$/, '');

    // Split on comma - the first part contains the base path
    const parts = inner.split(',');
    let basePath = parts[0];

    // Further clean up: remove trailing position specifics after the last bracketed identifier
    // We want to keep paths like /6/30!/4[id]/2[section] but not character offsets
    // Match pattern: keep everything up to and including the last [bracketed-id]
    const match = basePath.match(/^(.*\][^\/]*)/);
    if (match) {
      basePath = match[1];
    }

    return basePath;
  }

  /**
   * Check if two CFIs refer to the same section/page of the book.
   */
  private isSameSection(cfi1: string | null, cfi2: string | null): boolean {
    if (!cfi1 || !cfi2) return false;
    const base1 = this.extractBaseCfi(cfi1);
    const base2 = this.extractBaseCfi(cfi2);
    return base1 === base2;
  }

  start(retryCount = 0): void {
    const words = this.extractWordsWithRanges();
    if (words.length === 0) {
      // Retry up to 3 times with increasing delay if no words found
      if (retryCount < 3) {
        setTimeout(() => this.start(retryCount + 1), 150 * (retryCount + 1));
        return;
      }
      return;
    }

    let startIndex = 0;
    let resumedFromIndex: number | null = null;

    // First, check if we have a pending start position (from selection or current position)
    if (this.pendingStartWordIndex !== null && this.pendingStartWordIndex < words.length) {
      startIndex = this.pendingStartWordIndex;
      this.pendingStartWordIndex = null;
    }
    // Otherwise, check if we have a saved position for this page
    else {
      const savedPosition = this.loadPositionFromStorage();

      if (savedPosition && this.isSameSection(savedPosition.cfi, this.currentCfi)) {
        // We're on the same section/page, try to resume from saved position
        if (savedPosition.wordIndex < words.length) {
          // Verify the word matches (in case content changed)
          if (words[savedPosition.wordIndex]?.text === savedPosition.wordText) {
            startIndex = savedPosition.wordIndex;
            resumedFromIndex = savedPosition.wordIndex;
          }
        }
      }
    }

    this.updateState({
      active: true,
      playing: true,
      words,
      currentIndex: startIndex,
      progress: (startIndex / words.length) * 100,
      resumedFromIndex
    });

    this.scheduleNextWord();
  }

  pause(): void {
    this.clearTimer();
    this.updateState({playing: false});
  }

  resume(): void {
    if (!this.currentState.active) return;
    this.updateState({playing: true});
    this.scheduleNextWord();
  }

  togglePlayPause(): void {
    if (this.currentState.playing) {
      this.pause();
    } else {
      this.resume();
    }
  }

  stop(): void {
    // Save position before stopping
    this.savePositionToStorage();

    // Emit position info so reader can update progress and highlight
    const state = this.currentState;
    if (state.words.length > 0) {
      const currentWord = state.words[state.currentIndex];
      this.stopPosition.next({
        wordIndex: state.currentIndex,
        totalWords: state.words.length,
        text: currentWord?.text || '',
        range: currentWord?.range,
        docIndex: currentWord?.docIndex
      });
    } else {
      this.stopPosition.next(null);
    }

    this.clearTimer();
    this.updateState({
      active: false,
      playing: false,
      words: [],
      currentIndex: 0,
      progress: 0,
      resumedFromIndex: null
    });
  }

  requestStart(selectionText?: string): void {
    // Pre-extract words to find first visible word
    const words = this.extractWordsWithRanges();
    const firstVisibleWordIndex = this.findFirstVisibleWordIndex(words);

    // Check if there's a saved position for the current section/page
    const savedPosition = this.loadPositionFromStorage();
    const hasSavedPosition = !!(savedPosition && this.isSameSection(savedPosition.cfi, this.currentCfi));

    // Check if there's a text selection
    const hasSelection = !!selectionText && selectionText.trim().length > 0;

    this.showStartChoice.next({
      hasSavedPosition,
      hasSelection,
      selectionText: selectionText?.trim(),
      firstVisibleWordIndex
    });
  }

  startFromBeginning(): void {
    this.clearPositionFromStorage();
    this.pendingStartWordIndex = null;
    this.start();
  }

  startFromSavedPosition(): void {
    this.pendingStartWordIndex = null;
    this.start(); // start() already handles resuming from saved position
  }

  startFromCurrentPosition(): void {
    this.clearPositionFromStorage();
    const words = this.extractWordsWithRanges();
    const firstVisibleIndex = this.findFirstVisibleWordIndex(words);
    this.pendingStartWordIndex = firstVisibleIndex > 0 ? firstVisibleIndex : null;
    this.start();
  }

  startFromSelection(selectionText: string): void {
    this.clearPositionFromStorage();
    const words = this.extractWordsWithRanges();
    const selectionIndex = this.findWordIndexBySelection(words, selectionText);
    this.pendingStartWordIndex = selectionIndex >= 0 ? selectionIndex : null;
    this.start();
  }

  private findFirstVisibleWordIndex(words: RsvpWord[]): number {
    if (words.length === 0) return 0;

    // Get the visible bounds from Foliate's paginator
    // In Foliate's CSS column layout, getBoundingClientRect() returns layout coordinates
    // The visible content is determined by the scroll position
    // Visible bounds = [scrollStart - pageSize, scrollStart] in layout coordinates
    try {
      const foliateView = document.querySelector('foliate-view') as any;
      if (foliateView?.renderer) {
        const renderer = foliateView.renderer;
        const scrollStart = renderer.start ?? 0;
        const pageSize = renderer.size ?? 0;

        if (pageSize > 0) {
          // Foliate's visible bounds formula (from paginator.js getVisibleRange)
          const visibleStart = scrollStart - pageSize;
          const visibleEnd = scrollStart;

          // Find the first word whose layout position falls within visible bounds
          for (let i = 0; i < words.length; i++) {
            const word = words[i];
            if (word.range) {
              try {
                const rect = word.range.getBoundingClientRect();
                // Word is visible if its left edge is within the visible layout bounds
                if (rect.width > 0 && rect.left >= visibleStart && rect.left < visibleEnd) {
                  return i;
                }
              } catch {
                continue;
              }
            }
          }
        }
      }
    } catch {
      // Fall through to return 0
    }

    return 0;
  }

  private findWordIndexBySelection(words: RsvpWord[], selectionText: string): number {
    if (!selectionText || words.length === 0) return -1;

    // Clean the selection text
    const cleanSelection = selectionText.trim().toLowerCase();
    const selectionWords = cleanSelection.split(/\s+/);

    if (selectionWords.length === 0) return -1;

    // Find the first word of the selection
    const firstSelectionWord = selectionWords[0];

    for (let i = 0; i < words.length; i++) {
      const word = words[i];
      const cleanWord = word.text.toLowerCase().replace(/[^\w]/g, '');
      const cleanFirstWord = firstSelectionWord.replace(/[^\w]/g, '');

      if (cleanWord === cleanFirstWord || cleanWord.includes(cleanFirstWord) || cleanFirstWord.includes(cleanWord)) {
        // Verify this is likely the right match by checking subsequent words
        if (selectionWords.length === 1) {
          return i;
        }

        // Check if the next few words also match
        let matchCount = 1;
        for (let j = 1; j < selectionWords.length && i + j < words.length; j++) {
          const nextWord = words[i + j].text.toLowerCase().replace(/[^\w]/g, '');
          const nextSelectionWord = selectionWords[j].replace(/[^\w]/g, '');
          if (nextWord === nextSelectionWord || nextWord.includes(nextSelectionWord)) {
            matchCount++;
          } else {
            break;
          }
        }

        // If we matched at least half the selection words, consider it a match
        if (matchCount >= Math.ceil(selectionWords.length / 2)) {
          return i;
        }
      }
    }

    return -1;
  }

  increaseSpeed(): void {
    const newWpm = Math.min(this.MAX_WPM, this.currentState.wpm + this.WPM_STEP);
    this.updateState({wpm: newWpm});
    this.saveWpmToStorage(newWpm);
  }

  decreaseSpeed(): void {
    const newWpm = Math.max(this.MIN_WPM, this.currentState.wpm - this.WPM_STEP);
    this.updateState({wpm: newWpm});
    this.saveWpmToStorage(newWpm);
  }

  skipForward(count: number = 10): void {
    const state = this.currentState;
    const newIndex = Math.min(state.words.length - 1, state.currentIndex + count);
    this.updateState({
      currentIndex: newIndex,
      progress: (newIndex / state.words.length) * 100
    });
  }

  skipBackward(count: number = 10): void {
    const state = this.currentState;
    const newIndex = Math.max(0, state.currentIndex - count);
    this.updateState({
      currentIndex: newIndex,
      progress: (newIndex / state.words.length) * 100
    });
  }

  loadNextPageContent(retryCount = 0): void {
    // Clear saved position since we're on a new page
    this.clearPositionFromStorage();

    const words = this.extractWordsWithRanges();
    if (words.length === 0) {
      // Retry up to 3 times with increasing delay if no words found
      if (retryCount < 3) {
        setTimeout(() => this.loadNextPageContent(retryCount + 1), 200 * (retryCount + 1));
        return;
      }
      this.pause();
      return;
    }

    this.updateState({
      words,
      currentIndex: 0,
      progress: 0,
      resumedFromIndex: null
    });

    if (this.currentState.playing) {
      this.scheduleNextWord();
    }
  }

  private scheduleNextWord(): void {
    this.clearTimer();

    const state = this.currentState;
    if (!state.playing || !state.active) return;

    if (state.currentIndex >= state.words.length) {
      this.requestNextPage.next();
      return;
    }

    const word = state.words[state.currentIndex];
    const duration = this.getWordDisplayDuration(word, state.wpm);

    this.playbackTimer = setTimeout(() => {
      this.advanceToNextWord();
    }, duration);
  }

  private advanceToNextWord(): void {
    const state = this.currentState;
    const newIndex = state.currentIndex + 1;

    if (newIndex >= state.words.length) {
      this.requestNextPage.next();
      return;
    }

    this.updateState({
      currentIndex: newIndex,
      progress: (newIndex / state.words.length) * 100
    });

    this.scheduleNextWord();
  }

  private clearTimer(): void {
    if (this.playbackTimer) {
      clearTimeout(this.playbackTimer);
      this.playbackTimer = null;
    }
  }

  private extractTextFromCurrentView(): string {
    const words = this.extractWordsWithRanges();
    return words.map(w => w.text).join(' ');
  }

  private extractWordsWithRanges(): RsvpWord[] {
    const renderer = this.viewManager.getRenderer();
    if (!renderer) return [];

    const contents = renderer.getContents?.();
    if (!contents || contents.length === 0) return [];

    const allWords: RsvpWord[] = [];

    for (const content of contents) {
      const {doc, index: docIndex} = content;
      if (!doc?.body) continue;

      const words = this.extractWordsFromElement(doc.body, doc, docIndex);
      allWords.push(...words);
    }

    return allWords;
  }

  private extractWordsFromElement(element: HTMLElement, doc: Document, docIndex: number): RsvpWord[] {
    const excludeTags = new Set(['SCRIPT', 'STYLE', 'NAV', 'HEADER', 'FOOTER', 'ASIDE']);
    const words: RsvpWord[] = [];

    const walk = (node: Node): void => {
      if (node.nodeType === Node.TEXT_NODE) {
        const text = node.textContent || '';
        const nodeWords = text.split(/(\s+)/).filter(w => w.trim().length > 0);

        let offset = 0;
        for (const word of nodeWords) {
          const wordStart = text.indexOf(word, offset);
          if (wordStart === -1) continue;

          try {
            const range = doc.createRange();
            range.setStart(node, wordStart);
            range.setEnd(node, wordStart + word.length);

            words.push({
              text: word,
              orpIndex: this.calculateORP(word),
              pauseMultiplier: this.getPauseMultiplier(word),
              range,
              docIndex
            });
          } catch {
            // If range creation fails, add word without range
            words.push({
              text: word,
              orpIndex: this.calculateORP(word),
              pauseMultiplier: this.getPauseMultiplier(word)
            });
          }

          offset = wordStart + word.length;
        }
        return;
      }

      if (node.nodeType !== Node.ELEMENT_NODE) {
        return;
      }

      const el = node as HTMLElement;
      const tagName = el.tagName.toUpperCase();

      if (excludeTags.has(tagName)) {
        return;
      }

      const style = el.ownerDocument.defaultView?.getComputedStyle(el);
      if (style?.display === 'none' || style?.visibility === 'hidden') {
        return;
      }

      for (const child of Array.from(el.childNodes)) {
        walk(child);
      }
    };

    walk(element);
    return words;
  }

  private tokenizeText(text: string): RsvpWord[] {
    const words = text.split(/\s+/).filter(w => w.length > 0);

    return words.map(word => ({
      text: word,
      orpIndex: this.calculateORP(word),
      pauseMultiplier: this.getPauseMultiplier(word)
    }));
  }

  private calculateORP(word: string): number {
    const cleanWord = word.replace(/[^\w]/g, '');
    const len = cleanWord.length;

    if (len <= 1) return 0;
    if (len <= 3) return 0;
    if (len <= 5) return 1;
    if (len <= 8) return 2;
    return 3;
  }

  private getPauseMultiplier(word: string): number {
    if (/[.!?]$/.test(word)) return 2.0;
    if (/[,;:]$/.test(word)) return 1.5;
    if (word.length > 12) return 1.3;
    if (word.length > 8) return 1.1;
    return 1.0;
  }

  private getWordDisplayDuration(word: RsvpWord, wpm: number): number {
    const baseMs = 60000 / wpm;
    return baseMs * word.pauseMultiplier;
  }

  private updateState(partial: Partial<RsvpState>): void {
    this.stateSubject.next({...this.currentState, ...partial});
  }

  reset(): void {
    this.stop();
    this.clearPositionFromStorage();
    this.currentCfi = null;
    this.updateState({wpm: this.DEFAULT_WPM, resumedFromIndex: null});
  }
}
