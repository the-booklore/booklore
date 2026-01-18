import {inject, Injectable} from '@angular/core';
import {Subject} from 'rxjs';
import {switchMap, takeUntil, tap} from 'rxjs/operators';
import {of} from 'rxjs';
import {Annotation} from '../../../../../shared/service/annotation.service';
import {ReaderViewManagerService} from '../../core/view-manager.service';
import {ReaderAnnotationHttpService} from '../annotations/annotation.service';
import {ReaderLeftSidebarService} from '../../layout/panel/panel.service';
import {TextSelectionAction, AnnotationStyle} from '../../shared/selection-popup.component';

export interface SelectionState {
  visible: boolean;
  position: { x: number; y: number };
  showBelow: boolean;
  overlappingAnnotationId: number | null;
  selectedText: string;
}

export interface SelectionDetail {
  text: string;
  cfi: string;
  range: Range;
  index: number;
}

@Injectable()
export class ReaderSelectionService {
  private viewManager = inject(ReaderViewManagerService);
  private annotationService = inject(ReaderAnnotationHttpService);
  private leftSidebarService = inject(ReaderLeftSidebarService);

  private bookId!: number;
  private annotations: Annotation[] = [];
  private currentSelection: SelectionDetail | null = null;
  private destroy$ = new Subject<void>();

  private _visible = false;
  private _position = { x: 0, y: 0 };
  private _showBelow = false;
  private _overlappingAnnotationId: number | null = null;

  private previewCfi: string | null = null;
  private previewColor: string | null = null;
  private previewStyle: AnnotationStyle | null = null;

  private stateSubject = new Subject<SelectionState>();
  public state$ = this.stateSubject.asObservable();

  private annotationsChangedSubject = new Subject<Annotation[]>();
  public annotationsChanged$ = this.annotationsChangedSubject.asObservable();

  get visible(): boolean {
    return this._visible;
  }

  get position(): { x: number; y: number } {
    return this._position;
  }

  get showBelow(): boolean {
    return this._showBelow;
  }

  get overlappingAnnotationId(): number | null {
    return this._overlappingAnnotationId;
  }

  initialize(bookId: number, destroy$: Subject<void>): void {
    this.bookId = bookId;
    this.destroy$ = destroy$;
  }

  setAnnotations(annotations: Annotation[]): void {
    this.annotations = annotations;
  }

  handleTextSelected(detail: SelectionDetail, popupPosition?: { x: number; y: number; showBelow?: boolean }): void {
    this.currentSelection = detail;
    this._overlappingAnnotationId = this.findOverlappingAnnotation(detail.cfi);
    this._visible = true;
    this._position = popupPosition || { x: 0, y: 0 };
    this._showBelow = popupPosition?.showBelow || false;

    this.emitState();
  }

  handleAction(action: TextSelectionAction): void {
    if (action.type === 'preview' && this.currentSelection) {
      this.updatePreview(
        this.currentSelection.cfi,
        action.color || '#FFFF00',
        action.style || 'highlight'
      );
      return;
    }

    this._visible = false;
    this._overlappingAnnotationId = null;

    if (action.type === 'dismiss') {
      this.clearPreview();
      this.viewManager.clearSelection();
      this.currentSelection = null;
      this.emitState();
      return;
    }

    if (action.type === 'select') {
      this.clearPreview();
      this.emitState();
    } else if (action.type === 'search' && action.searchText) {
      this.clearPreview();
      this.viewManager.clearSelection();
      this.leftSidebarService.openWithSearch(action.searchText);
      this.emitState();
    } else if (action.type === 'delete' && action.annotationId) {
      this.clearPreview();
      this.deleteAnnotation(action.annotationId);
      this.viewManager.clearSelection();
    } else if (action.type === 'annotate' && this.currentSelection) {
      this.clearPreview();
      this.createAnnotation(
        this.currentSelection.text,
        this.currentSelection.cfi,
        action.color || '#FFFF00',
        action.style || 'highlight'
      );
    }

    this.currentSelection = null;
    this.emitState();
  }

  private updatePreview(cfi: string, color: string, style: AnnotationStyle): void {
    if (this.previewCfi) {
      this.viewManager.deleteAnnotation(this.previewCfi)
        .pipe(takeUntil(this.destroy$))
        .subscribe();
    }

    this.previewCfi = cfi;
    this.previewColor = color;
    this.previewStyle = style;

    this.viewManager.addAnnotation({
      value: cfi,
      color: color,
      style: style
    }).pipe(takeUntil(this.destroy$)).subscribe();
  }

  private clearPreview(): void {
    if (this.previewCfi) {
      this.viewManager.deleteAnnotation(this.previewCfi)
        .pipe(takeUntil(this.destroy$))
        .subscribe();
      this.previewCfi = null;
      this.previewColor = null;
      this.previewStyle = null;
    }
  }

  private createAnnotation(text: string, cfi: string, color: string, style: AnnotationStyle): void {
    this.annotationService.createAnnotation(
      this.bookId,
      cfi,
      text,
      color,
      style
    ).pipe(
      switchMap(savedAnnotation => {
        if (!savedAnnotation) return of(null);

        this.annotations = [...this.annotations, savedAnnotation];
        this.annotationsChangedSubject.next(this.annotations);

        return this.viewManager.addAnnotation({
          value: savedAnnotation.cfi,
          color: savedAnnotation.color,
          style: savedAnnotation.style
        }).pipe(
          tap(() => this.viewManager.clearSelection())
        );
      }),
      takeUntil(this.destroy$)
    ).subscribe();
  }

  deleteAnnotation(annotationId: number): void {
    const annotation = this.annotations.find(a => a.id === annotationId);
    if (!annotation) return;

    this.annotationService.deleteAnnotation(annotationId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(success => {
        if (success) {
          this.viewManager.deleteAnnotation(annotation.cfi)
            .pipe(takeUntil(this.destroy$))
            .subscribe();
          this.annotations = this.annotations.filter(a => a.id !== annotationId);
          this.annotationsChangedSubject.next(this.annotations);
        }
      });
  }

  private findOverlappingAnnotation(selectionCfi: string): number | null {
    if (!selectionCfi || !this.annotations.length) {
      return null;
    }

    const getBasePath = (cfi: string): string => {
      const inner = cfi.replace(/^epubcfi\(/, '').replace(/\)$/, '');
      const commaIndex = inner.indexOf(',');
      if (commaIndex > 0) {
        return inner.substring(0, commaIndex).replace(/:\d+$/, '');
      }
      return inner.replace(/:\d+$/, '');
    };

    const selectionBasePath = getBasePath(selectionCfi);

    for (const annotation of this.annotations) {
      const annotationBasePath = getBasePath(annotation.cfi);
      if (selectionBasePath.startsWith(annotationBasePath) ||
          annotationBasePath.startsWith(selectionBasePath) ||
          selectionBasePath === annotationBasePath) {
        return annotation.id;
      }
    }

    return null;
  }

  private emitState(): void {
    this.stateSubject.next({
      visible: this._visible,
      position: this._position,
      showBelow: this._showBelow,
      overlappingAnnotationId: this._overlappingAnnotationId,
      selectedText: this.currentSelection?.text || ''
    });
  }

  reset(): void {
    this._visible = false;
    this._position = { x: 0, y: 0 };
    this._showBelow = false;
    this._overlappingAnnotationId = null;
    this.currentSelection = null;
    this.annotations = [];
    this.previewCfi = null;
    this.previewColor = null;
    this.previewStyle = null;
  }

  getCurrentSelection(): SelectionDetail | null {
    return this.currentSelection;
  }

  hidePopup(): void {
    this._visible = false;
    this._overlappingAnnotationId = null;
    this.clearPreview();
    this.emitState();
  }
}
