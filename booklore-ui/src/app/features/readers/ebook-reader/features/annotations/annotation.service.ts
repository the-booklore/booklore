import {inject, Injectable} from '@angular/core';
import {Observable, of} from 'rxjs';
import {catchError, map, tap} from 'rxjs/operators';
import {MessageService} from 'primeng/api';
import {
  Annotation,
  AnnotationService,
  AnnotationStyle,
  CreateAnnotationRequest
} from '../../../../../shared/service/annotation.service';
import {Annotation as ViewAnnotation, ReaderAnnotationService} from './annotation-renderer.service';

@Injectable()
export class ReaderAnnotationHttpService {
  private annotationService = inject(AnnotationService);
  private messageService = inject(MessageService);
  private readerAnnotationService = inject(ReaderAnnotationService);

  private currentChapterTitle: string | null = null;

  updateCurrentChapter(chapterTitle: string): void {
    this.currentChapterTitle = chapterTitle;
  }

  createAnnotation(
    bookId: number,
    cfi: string,
    text: string,
    color: string = '#FACC15',
    style: AnnotationStyle = 'highlight',
    note?: string
  ): Observable<Annotation | null> {
    const request: CreateAnnotationRequest = {
      bookId,
      cfi,
      text,
      color,
      style,
      note,
      chapterTitle: this.currentChapterTitle || undefined
    };

    return this.annotationService.createAnnotation(request).pipe(
      tap(() => {
        this.messageService.add({
          severity: 'success',
          summary: 'Highlight Added',
          detail: 'Your highlight was saved successfully.'
        });
      }),
      catchError(error => {
        const isDuplicate = error?.status === 409;
        this.messageService.add(
          isDuplicate
            ? {
              severity: 'warn',
              summary: 'Highlight Already Exists',
              detail: 'You already have a highlight at this location.'
            }
            : {
              severity: 'error',
              summary: 'Unable to Add Highlight',
              detail: 'Something went wrong while adding the highlight. Please try again.'
            }
        );
        return of(null);
      })
    );
  }

  getAnnotations(bookId: number): Observable<Annotation[]> {
    return this.annotationService.getAnnotationsForBook(bookId).pipe(
      catchError(() => {
        return of([]);
      })
    );
  }

  deleteAnnotation(annotationId: number): Observable<boolean> {
    return this.annotationService.deleteAnnotation(annotationId).pipe(
      map(() => {
        this.messageService.add({
          severity: 'success',
          summary: 'Highlight Removed',
          detail: 'Your highlight was removed successfully.'
        });
        return true;
      }),
      catchError(() => {
        this.messageService.add({
          severity: 'error',
          summary: 'Unable to Remove Highlight',
          detail: 'Something went wrong while removing the highlight. Please try again.'
        });
        return of(false);
      })
    );
  }

  updateAnnotationNote(annotationId: number, note: string): Observable<Annotation | null> {
    return this.annotationService.updateAnnotation(annotationId, {note}).pipe(
      tap(() => {
        this.messageService.add({
          severity: 'success',
          summary: 'Note Updated',
          detail: 'Your note was saved successfully.'
        });
      }),
      catchError(() => {
        this.messageService.add({
          severity: 'error',
          summary: 'Unable to Update Note',
          detail: 'Something went wrong while updating the note. Please try again.'
        });
        return of(null);
      })
    );
  }

  toViewAnnotations(annotations: Annotation[]): ViewAnnotation[] {
    return annotations.map(a => ({
      value: a.cfi,
      color: a.color,
      style: a.style
    }));
  }

  reset(): void {
    this.currentChapterTitle = null;
    this.readerAnnotationService.resetAnnotations();
  }
}
