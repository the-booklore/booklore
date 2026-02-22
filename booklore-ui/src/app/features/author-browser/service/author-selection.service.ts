import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable} from 'rxjs';
import {AuthorSummary} from '../model/author.model';

export interface AuthorCheckboxClickEvent {
  index: number;
  author: AuthorSummary;
  selected: boolean;
  shiftKey: boolean;
}

@Injectable({providedIn: 'root'})
export class AuthorSelectionService {
  private selectedAuthorsSubject = new BehaviorSubject<Set<number>>(new Set());
  private currentAuthors: AuthorSummary[] = [];
  private lastSelectedIndex: number | null = null;

  selectedAuthors$: Observable<Set<number>> = this.selectedAuthorsSubject.asObservable();

  get selectedAuthors(): Set<number> {
    return this.selectedAuthorsSubject.value;
  }

  get selectedCount(): number {
    return this.selectedAuthorsSubject.value.size;
  }

  setCurrentAuthors(authors: AuthorSummary[]): void {
    this.currentAuthors = authors;
  }

  handleCheckboxClick(event: AuthorCheckboxClickEvent): void {
    const {index, author, selected, shiftKey} = event;

    if (!shiftKey || this.lastSelectedIndex === null) {
      if (selected) {
        this.select(author.id);
      } else {
        this.deselect(author.id);
      }
      this.lastSelectedIndex = index;
    } else {
      const start = Math.min(this.lastSelectedIndex, index);
      const end = Math.max(this.lastSelectedIndex, index);
      for (let i = start; i <= end; i++) {
        const a = this.currentAuthors[i];
        if (!a) continue;
        if (selected) {
          this.select(a.id);
        } else {
          this.deselect(a.id);
        }
      }
    }
  }

  selectAll(): void {
    const current = new Set(this.selectedAuthorsSubject.value);
    for (const author of this.currentAuthors) {
      current.add(author.id);
    }
    this.selectedAuthorsSubject.next(current);
  }

  deselectAll(): void {
    this.selectedAuthorsSubject.next(new Set());
    this.lastSelectedIndex = null;
  }

  getSelectedIds(): number[] {
    return Array.from(this.selectedAuthorsSubject.value);
  }

  private select(id: number): void {
    const current = new Set(this.selectedAuthorsSubject.value);
    current.add(id);
    this.selectedAuthorsSubject.next(current);
  }

  private deselect(id: number): void {
    const current = new Set(this.selectedAuthorsSubject.value);
    current.delete(id);
    this.selectedAuthorsSubject.next(current);
  }
}
