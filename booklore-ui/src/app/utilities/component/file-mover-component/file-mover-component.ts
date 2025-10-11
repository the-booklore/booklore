import {Component, inject, OnDestroy} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {TableModule} from 'primeng/table';
import {Divider} from 'primeng/divider';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {filter, take, takeUntil} from 'rxjs/operators';
import {Subject} from 'rxjs';

import {BookService} from '../../../book/service/book.service';
import {Book} from '../../../book/model/book.model';
import {FileMoveRequest, FileOperationsService} from '../../service/file-operations-service';
import {LibraryService} from "../../../book/service/library.service";
import {AppSettingsService} from '../../../core/service/app-settings.service';
import {Select} from 'primeng/select';

interface FilePreview {
  bookId: number;
  originalPath: string;
  relativeOriginalPath: string;
  currentLibraryId: number | null;
  currentLibraryName: string;
  targetLibraryId: number | null;
  targetLibraryName: string;
  newPath: string;
  relativeNewPath: string;
  isMoved?: boolean;
}

@Component({
  selector: 'app-file-mover-component',
  standalone: true,
  imports: [Button, FormsModule, TableModule, Divider, Select],
  templateUrl: './file-mover-component.html',
  styleUrl: './file-mover-component.scss'
})
export class FileMoverComponent implements OnDestroy {
  private config = inject(DynamicDialogConfig);
  private ref = inject(DynamicDialogRef);
  private bookService = inject(BookService);
  private libraryService = inject(LibraryService);
  private fileOperationsService = inject(FileOperationsService);
  private messageService = inject(MessageService);
  private appSettingsService = inject(AppSettingsService);
  private destroy$ = new Subject<void>();

  libraryPatterns: {
    libraryId: number | null;
    libraryName: string;
    pattern: string;
    source: string;
    bookCount: number;
  }[] = [];
  defaultMovePattern = '';
  loading = false;
  patternsCollapsed = false;

  bookIds: Set<number> = new Set();
  books: Book[] = [];
  availableLibraries: { id: number | null; name: string }[] = [];
  filePreviews: FilePreview[] = [];
  defaultTargetLibraryId: number | null = null;

  constructor() {
    this.bookIds = new Set(this.config.data?.bookIds ?? []);
    this.books = this.bookService.getBooksByIdsFromState([...this.bookIds]);
    this.loadAvailableLibraries();
    this.loadDefaultPattern();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadDefaultPattern(): void {
    this.appSettingsService.appSettings$.pipe(
      filter(settings => settings != null),
      take(1),
      takeUntil(this.destroy$)
    ).subscribe(settings => {
      this.defaultMovePattern = settings?.uploadPattern || '';
      this.loadLibraryPatterns();
    });
  }

  private loadLibraryPatterns(): void {
    this.libraryService.libraryState$.pipe(
      filter(state => state.loaded && state.libraries != null),
      take(1),
      takeUntil(this.destroy$)
    ).subscribe(state => {
      const booksByLibrary = new Map<number | null, Book[]>();
      this.books.forEach(book => {
        const libraryId =
          book.libraryId ??
          book.libraryPath?.id ??
          (book as any).library?.id ??
          null;
        if (!booksByLibrary.has(libraryId)) {
          booksByLibrary.set(libraryId, []);
        }
        booksByLibrary.get(libraryId)!.push(book);
      });

      this.libraryPatterns = Array.from(booksByLibrary.entries()).map(([libraryId, books]) => {
        let libraryName = 'Unknown Library';
        let pattern = this.defaultMovePattern;
        let source = 'App Default';

        if (libraryId) {
          const library = state.libraries?.find(lib => lib.id === libraryId);
          if (library) {
            libraryName = library.name;
            if (library.fileNamingPattern) {
              pattern = library.fileNamingPattern;
              source = 'Library Setting';
            }
          }
        }

        return {
          libraryId,
          libraryName,
          pattern,
          source,
          bookCount: books.length
        };
      });

      this.applyPattern();
    });
  }

  private loadAvailableLibraries(): void {
    this.libraryService.libraryState$.pipe(
      filter(state => state.loaded && state.libraries != null),
      take(1),
      takeUntil(this.destroy$)
    ).subscribe(state => {
      this.availableLibraries = state.libraries?.map(lib => ({id: lib.id ?? null, name: lib.name})) || [];
    });
  }

  applyPattern(): void {
    this.filePreviews = this.books.map(book => {
      const fileName = book.fileName ?? '';
      const fileSubPath = book.fileSubPath ? `${book.fileSubPath.replace(/\/+$/g, '')}/` : '';

      const relativeOriginalPath = `${fileSubPath}${fileName}`;

      const currentLibraryId = book.libraryId ?? book.libraryPath?.id ?? (book as any).library?.id ?? null;
      const currentLibraryName = this.getLibraryNameById(currentLibraryId);

      const targetLibraryId = currentLibraryId;
      const targetLibraryName = currentLibraryName;

      const preview: FilePreview = {
        bookId: book.id,
        originalPath: this.getFullPath(currentLibraryId, relativeOriginalPath),
        relativeOriginalPath,
        currentLibraryId,
        currentLibraryName,
        targetLibraryId,
        targetLibraryName,
        newPath: '',
        relativeNewPath: ''
      };

      this.updatePreviewPaths(preview, book);
      return preview;
    });
  }

  onDefaultLibraryChange(): void {
    this.filePreviews.forEach(preview => {
      if (!preview.isMoved) {
        preview.targetLibraryId = this.defaultTargetLibraryId;
        preview.targetLibraryName = this.getLibraryNameById(this.defaultTargetLibraryId);

        const book = this.books.find(b => b.id === preview.bookId);
        if (book) {
          this.updatePreviewPaths(preview, book);
        }
      }
    });
  }

  onLibraryChange(preview: FilePreview): void {
    preview.targetLibraryName = this.getLibraryNameById(preview.targetLibraryId);
    const book = this.books.find(b => b.id === preview.bookId);
    if (book) {
      this.updatePreviewPaths(preview, book);
    }
  }

  private updatePreviewPaths(preview: FilePreview, book: Book): void {
    const meta = book.metadata!;
    const fileName = book.fileName ?? '';
    const extension = fileName.match(/\.[^.]+$/)?.[0] ?? '';

    const libraryPattern = this.libraryPatterns.find(p => p.libraryId === preview.targetLibraryId);
    const pattern = libraryPattern?.pattern || this.defaultMovePattern;

    const values: Record<string, string> = {
      authors: this.sanitize(meta.authors?.join(', ') || 'Unknown Author'),
      title: this.sanitize(meta.title || 'Untitled'),
      year: this.formatYear(meta.publishedDate),
      series: this.sanitize(meta.seriesName || ''),
      seriesIndex: this.formatSeriesIndex(meta.seriesNumber ?? undefined),
      language: this.sanitize(meta.language || ''),
      publisher: this.sanitize(meta.publisher || ''),
      isbn: this.sanitize(meta.isbn13 || meta.isbn10 || ''),
      currentFilename: this.sanitize(fileName)
    };

    let newPath: string;

    if (!pattern?.trim()) {
      newPath = fileName;
    } else {
      newPath = pattern.replace(/<([^<>]+)>/g, (_, block) => {
        const placeholders = [...block.matchAll(/{(.*?)}/g)].map(m => m[1]);
        const allHaveValues = placeholders.every(key => values[key]?.trim());
        return allHaveValues
          ? block.replace(/{(.*?)}/g, (_: string, key: string) => values[key] ?? '')
          : '';
      });

      newPath = newPath.replace(/{(.*?)}/g, (_, key) => values[key] ?? '');

      if (!newPath.endsWith(extension)) {
        newPath += extension;
      }
    }

    preview.relativeNewPath = newPath;
    preview.newPath = this.getFullPath(preview.targetLibraryId, newPath);
  }

  private getLibraryNameById(libraryId: number | null): string {
    if (libraryId === null) return 'Unknown Library';
    return this.availableLibraries.find(lib => lib.id === libraryId)?.name || 'Unknown Library';
  }

  private getFullPath(libraryId: number | null, relativePath: string): string {
    const libraryPath = libraryId ? this.libraryService.getLibraryPathById(libraryId)?.replace(/\/+$/g, '') : '';
    return libraryPath ? `${libraryPath}/${relativePath}`.replace(/\/\/+/g, '/') : relativePath;
  }

  get movedFileCount(): number {
    return this.filePreviews.filter(p => p.isMoved).length;
  }

  sanitize(input: string | undefined): string {
    return input?.replace(/[\\/:*?"<>|]/g, '')
      .replace(/[\x00-\x1F\x7F]/g, '')
      .replace(/\s+/g, ' ')
      .trim() ?? '';
  }

  formatYear(dateStr?: string): string {
    if (!dateStr) return '';
    const yearMatch = dateStr.match(/^(\d{4})/);
    if (yearMatch) {
      return yearMatch[1];
    }
    const date = new Date(dateStr);
    return isNaN(date.getTime()) ? '' : date.getUTCFullYear().toString();
  }

  formatSeriesIndex(seriesNumber?: number): string {
    if (seriesNumber == null) return '';
    return this.sanitize(seriesNumber.toString());
  }

  saveChanges(): void {
    this.loading = true;

    const request: FileMoveRequest = {
      bookIds: [...this.bookIds],
      moves: this.filePreviews.map(preview => ({
        bookId: preview.bookId,
        targetLibraryId: preview.targetLibraryId
      }))
    };

    this.fileOperationsService.moveFiles(request).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.loading = false;
        this.filePreviews.forEach(p => (p.isMoved = true));
        this.messageService.add({
          severity: 'success',
          summary: 'Files Organized!',
          detail: `Successfully organized ${this.filePreviews.length} file${this.filePreviews.length === 1 ? '' : 's'}.`,
          life: 3000
        });
      },
      error: () => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Oops! Something went wrong',
          detail: 'We had trouble organizing your files. Please try again.',
          life: 3000
        });
      }
    });
  }

  cancel(): void {
    this.ref.close();
  }

  togglePatternsCollapsed(): void {
    this.patternsCollapsed = !this.patternsCollapsed;
  }
}
