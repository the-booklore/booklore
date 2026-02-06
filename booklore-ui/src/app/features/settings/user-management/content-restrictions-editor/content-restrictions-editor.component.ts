import {Component, EventEmitter, inject, Input, OnChanges, OnInit, Output, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {Select} from 'primeng/select';
import {MessageService} from 'primeng/api';
import {Tooltip} from 'primeng/tooltip';
import {
  AGE_RATING_OPTIONS,
  CONTENT_RATINGS,
  ContentRestriction,
  ContentRestrictionMode,
  ContentRestrictionType
} from '../content-restriction.model';
import {ContentRestrictionService} from '../content-restriction.service';
import {BookService} from '../../../book/service/book.service';
import {filter, take} from 'rxjs/operators';
import {Book} from '../../../book/model/book.model';

@Component({
  selector: 'app-content-restrictions-editor',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Button,
    Select,
    Tooltip
  ],
  templateUrl: './content-restrictions-editor.component.html',
  styleUrls: ['./content-restrictions-editor.component.scss']
})
export class ContentRestrictionsEditorComponent implements OnInit, OnChanges {
  @Input() userId!: number;
  @Input() isEditing = false;
  @Output() restrictionsChanged = new EventEmitter<ContentRestriction[]>();

  private contentRestrictionService = inject(ContentRestrictionService);
  private bookService = inject(BookService);
  private messageService = inject(MessageService);

  restrictions: ContentRestriction[] = [];
  availableCategories: string[] = [];
  availableTags: string[] = [];
  availableMoods: string[] = [];

  newRestriction: Partial<ContentRestriction> = {
    restrictionType: ContentRestrictionType.CATEGORY,
    mode: ContentRestrictionMode.EXCLUDE,
    value: ''
  };

  restrictionTypes = [
    {label: 'Category/Genre', value: ContentRestrictionType.CATEGORY},
    {label: 'Tag', value: ContentRestrictionType.TAG},
    {label: 'Mood', value: ContentRestrictionType.MOOD},
    {label: 'Age Rating', value: ContentRestrictionType.AGE_RATING},
    {label: 'Content Rating', value: ContentRestrictionType.CONTENT_RATING}
  ];

  restrictionModes = [
    {label: 'Exclude (Hide matching)', value: ContentRestrictionMode.EXCLUDE},
    {label: 'Allow Only (Show only matching)', value: ContentRestrictionMode.ALLOW_ONLY}
  ];

  ageRatingOptions = AGE_RATING_OPTIONS;
  contentRatingOptions = CONTENT_RATINGS.map(r => ({label: r, value: r}));

  ngOnInit() {
    this.loadRestrictions();
    this.loadAvailableValues();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['userId'] && !changes['userId'].firstChange) {
      this.loadRestrictions();
    }
  }

  loadRestrictions() {
    if (!this.userId) return;

    this.contentRestrictionService.getUserRestrictions(this.userId).subscribe({
      next: (restrictions) => {
        this.restrictions = restrictions;
        this.restrictionsChanged.emit(this.restrictions);
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to load content restrictions'
        });
      }
    });
  }

  loadAvailableValues() {
    this.bookService.bookState$.pipe(
      filter(state => !!state.books && state.books.length > 0),
      take(1)
    ).subscribe(state => {
      const books: Book[] = state.books || [];
      const categories = new Set<string>();
      const tags = new Set<string>();
      const moods = new Set<string>();

      books.forEach((book: Book) => {
        book.metadata?.categories?.forEach((c: string) => categories.add(c));
        book.metadata?.tags?.forEach((t: string) => tags.add(t));
        book.metadata?.moods?.forEach((m: string) => moods.add(m));
      });

      this.availableCategories = Array.from(categories).sort();
      this.availableTags = Array.from(tags).sort();
      this.availableMoods = Array.from(moods).sort();
    });
  }

  getValueOptions(): {label: string, value: string}[] {
    switch (this.newRestriction.restrictionType) {
      case ContentRestrictionType.CATEGORY:
        return this.availableCategories.map(c => ({label: c, value: c}));
      case ContentRestrictionType.TAG:
        return this.availableTags.map(t => ({label: t, value: t}));
      case ContentRestrictionType.MOOD:
        return this.availableMoods.map(m => ({label: m, value: m}));
      case ContentRestrictionType.AGE_RATING:
        return this.ageRatingOptions.map(o => ({label: o.label, value: o.value}));
      case ContentRestrictionType.CONTENT_RATING:
        return this.contentRatingOptions;
      default:
        return [];
    }
  }

  addRestriction() {
    if (!this.newRestriction.value || !this.newRestriction.restrictionType || !this.newRestriction.mode) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Warning',
        detail: 'Please select all fields'
      });
      return;
    }

    const exists = this.restrictions.some(r =>
      r.restrictionType === this.newRestriction.restrictionType &&
      r.value === this.newRestriction.value
    );

    if (exists) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Warning',
        detail: 'This restriction already exists'
      });
      return;
    }

    const restriction: ContentRestriction = {
      userId: this.userId,
      restrictionType: this.newRestriction.restrictionType!,
      mode: this.newRestriction.mode!,
      value: this.newRestriction.value!
    };

    this.contentRestrictionService.addRestriction(this.userId, restriction).subscribe({
      next: (added) => {
        this.restrictions.push(added);
        this.restrictionsChanged.emit(this.restrictions);
        this.newRestriction.value = '';
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Content restriction added'
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to add content restriction'
        });
      }
    });
  }

  removeRestriction(restriction: ContentRestriction) {
    if (!restriction.id) return;

    this.contentRestrictionService.deleteRestriction(this.userId, restriction.id).subscribe({
      next: () => {
        this.restrictions = this.restrictions.filter(r => r.id !== restriction.id);
        this.restrictionsChanged.emit(this.restrictions);
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Content restriction removed'
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to remove content restriction'
        });
      }
    });
  }

  getRestrictionTypeLabel(type: ContentRestrictionType): string {
    const found = this.restrictionTypes.find(t => t.value === type);
    return found?.label || type;
  }

  getModeLabel(mode: ContentRestrictionMode): string {
    return mode === ContentRestrictionMode.EXCLUDE ? 'Exclude' : 'Allow Only';
  }

  getModeClass(mode: ContentRestrictionMode): string {
    return mode === ContentRestrictionMode.EXCLUDE ? 'mode-exclude' : 'mode-allow';
  }

  getExcludeRestrictions(): ContentRestriction[] {
    return this.restrictions.filter(r => r.mode === ContentRestrictionMode.EXCLUDE);
  }

  getAllowOnlyRestrictions(): ContentRestriction[] {
    return this.restrictions.filter(r => r.mode === ContentRestrictionMode.ALLOW_ONLY);
  }
}
