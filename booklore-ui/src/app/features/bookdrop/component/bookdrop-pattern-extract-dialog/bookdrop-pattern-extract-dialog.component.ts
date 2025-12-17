import {Component, ElementRef, inject, OnInit, ViewChild} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {Divider} from 'primeng/divider';
import {Chip} from 'primeng/chip';
import {ProgressSpinner} from 'primeng/progressspinner';
import {BookdropService, PatternExtractResult} from '../../service/bookdrop.service';
import {MessageService} from 'primeng/api';
import {NgClass} from '@angular/common';
import {Tooltip} from 'primeng/tooltip';

interface PatternPlaceholder {
  name: string;
  description: string;
  example: string;
}

interface PreviewResult {
  fileName: string;
  success: boolean;
  preview: Record<string, string>;
  errorMessage?: string;
}

@Component({
  selector: 'app-bookdrop-pattern-extract-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    Button,
    InputText,
    Divider,
    Chip,
    ProgressSpinner,
    NgClass,
    Tooltip,
  ],
  templateUrl: './bookdrop-pattern-extract-dialog.component.html',
  styleUrl: './bookdrop-pattern-extract-dialog.component.scss'
})
export class BookdropPatternExtractDialogComponent implements OnInit {

  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);
  private readonly bookdropService = inject(BookdropService);
  private readonly messageService = inject(MessageService);

  @ViewChild('patternInput', {static: false}) patternInput?: ElementRef<HTMLInputElement>;

  fileCount = 0;
  selectAll = false;
  excludedIds: number[] = [];
  selectedIds: number[] = [];

  isExtracting = false;
  previewResults: PreviewResult[] = [];

  patternPlaceholderText = 'e.g., {SeriesName} - Ch {SeriesNumber}';
  spinnerStyle = {width: '24px', height: '24px'};

  patternForm = new FormGroup({
    pattern: new FormControl('', Validators.required),
  });

  availablePlaceholders: PatternPlaceholder[] = [
    {name: '*', description: 'Wildcard - skips any text (not a metadata field)', example: 'anything'},
    {name: 'SeriesName', description: 'Series or comic name', example: 'Chronicles of Earth'},
    {name: 'Title', description: 'Book title', example: 'The Lost City'},
    {name: 'Subtitle', description: 'Book subtitle', example: 'A Tale of Adventure'},
    {name: 'Authors', description: 'Author name(s)', example: 'John Smith'},
    {name: 'SeriesNumber', description: 'Book number in series', example: '25'},
    {name: 'Published', description: 'Full date with format', example: '{Published:yyyy-MM-dd}'},
    {name: 'Publisher', description: 'Publisher name', example: 'Epic Press'},
    {name: 'Language', description: 'Language code', example: 'en'},
    {name: 'SeriesTotal', description: 'Total books in series', example: '50'},
    {name: 'ISBN10', description: 'ISBN-10 identifier', example: '1234567890'},
    {name: 'ISBN13', description: 'ISBN-13 identifier', example: '1234567890123'},
    {name: 'ASIN', description: 'Amazon ASIN', example: 'B012345678'},
  ];

  commonPatterns = [
    {label: 'Author - Title', pattern: '{Authors} - {Title}'},
    {label: 'Title - Author', pattern: '{Title} - {Authors}'},
    {label: 'Title (Year)', pattern: '{Title} ({Published:yyyy})'},
    {label: 'Author - Title (Year)', pattern: '{Authors} - {Title} ({Published:yyyy})'},
    {label: 'Series #Number', pattern: '{SeriesName} #{SeriesNumber}'},
    {label: 'Series - Chapter Number', pattern: '{SeriesName} - Chapter {SeriesNumber}'},
    {label: 'Series - Vol Number', pattern: '{SeriesName} - Vol {SeriesNumber}'},
    {label: '[Tag] Series - Chapter Number', pattern: '[*] {SeriesName} - Chapter {SeriesNumber}'},
    {label: 'Title by Author', pattern: '{Title} by {Authors}'},
    {label: 'Series vX (of Total)', pattern: '{SeriesName} v{SeriesNumber} (of {SeriesTotal})'},
  ];

  ngOnInit(): void {
    this.fileCount = this.config.data?.fileCount ?? 0;
    this.selectAll = this.config.data?.selectAll ?? false;
    this.excludedIds = this.config.data?.excludedIds ?? [];
    this.selectedIds = this.config.data?.selectedIds ?? [];
  }

  insertPlaceholder(placeholderName: string): void {
    const patternControl = this.patternForm.get('pattern');
    const currentPattern = patternControl?.value ?? '';
    const inputElement = this.patternInput?.nativeElement;
    
    const textToInsert = placeholderName === '*' ? '*' : `{${placeholderName}}`;
    
    const patternToModify = placeholderName === '*' 
      ? currentPattern 
      : this.removeExistingPlaceholder(currentPattern, placeholderName);
    
    if (inputElement) {
      const cursorPosition = this.calculateCursorPosition(inputElement, currentPattern, patternToModify);
      const newPattern = this.insertTextAtCursor(patternToModify, textToInsert, cursorPosition);
      
      patternControl?.setValue(newPattern);
      this.focusInputAfterInsertion(inputElement, cursorPosition, textToInsert.length);
    } else {
      patternControl?.setValue(patternToModify + textToInsert);
    }
    
    this.previewPattern();
  }

  private removeExistingPlaceholder(pattern: string, placeholderName: string): string {
    const existingPlaceholderRegex = new RegExp(`\\{${placeholderName}(?::[^}]*)?\\}`, 'g');
    return pattern.replace(existingPlaceholderRegex, '');
  }

  private calculateCursorPosition(inputElement: HTMLInputElement, originalPattern: string, modifiedPattern: string): number {
    let cursorPosition = inputElement.selectionStart ?? modifiedPattern.length;
    
    if (originalPattern !== modifiedPattern) {
      const existingPlaceholderRegex = new RegExp(`\\{\\w+(?::[^}]*)?\\}`, 'g');
      const matchBefore = originalPattern.substring(0, cursorPosition).match(existingPlaceholderRegex);
      if (matchBefore) {
        cursorPosition -= matchBefore.reduce((sum, match) => sum + match.length, 0);
      }
      cursorPosition = Math.max(0, cursorPosition);
    }
    
    return cursorPosition;
  }

  private insertTextAtCursor(pattern: string, text: string, cursorPosition: number): string {
    const textBefore = pattern.substring(0, cursorPosition);
    const textAfter = pattern.substring(cursorPosition);
    return textBefore + text + textAfter;
  }

  private focusInputAfterInsertion(inputElement: HTMLInputElement, cursorPosition: number, insertedTextLength: number): void {
    setTimeout(() => {
      const newCursorPosition = cursorPosition + insertedTextLength;
      inputElement.setSelectionRange(newCursorPosition, newCursorPosition);
      inputElement.focus();
    }, 0);
  }

  applyCommonPattern(pattern: string): void {
    this.patternForm.get('pattern')?.setValue(pattern);
    this.previewPattern();
  }

  previewPattern(): void {
    const pattern = this.patternForm.get('pattern')?.value;
    if (!pattern) {
      this.previewResults = [];
      return;
    }

    const request = {
      pattern,
      selectAll: this.selectAll,
      excludedIds: this.excludedIds,
      selectedIds: this.selectedIds,
      preview: true
    };

    this.bookdropService.extractFromPattern(request).subscribe({
      next: (result) => {
        this.previewResults = result.results.map(r => ({
          fileName: r.fileName,
          success: r.success,
          preview: r.extractedMetadata || {},
          errorMessage: r.errorMessage
        }));
      },
      error: () => {
        this.previewResults = [];
      }
    });
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  extract(): void {
    const pattern = this.patternForm.get('pattern')?.value;
    if (!pattern) {
      return;
    }

    this.isExtracting = true;

    const payload = {
      pattern,
      selectAll: this.selectAll,
      excludedIds: this.excludedIds,
      selectedIds: this.selectedIds,
      preview: false,
    };

    this.bookdropService.extractFromPattern(payload).subscribe({
      next: (result: PatternExtractResult) => {
        this.isExtracting = false;
        this.messageService.add({
          severity: 'success',
          summary: 'Extraction Complete',
          detail: `Successfully extracted metadata from ${result.successfullyExtracted} of ${result.totalFiles} files.`,
        });
        this.dialogRef.close(result);
      },
      error: (err) => {
        this.isExtracting = false;
        console.error('Pattern extraction failed:', err);
        this.messageService.add({
          severity: 'error',
          summary: 'Extraction Failed',
          detail: 'An error occurred during pattern extraction.',
        });
      },
    });
  }

  get hasValidPattern(): boolean {
    const pattern: string = this.patternForm.get('pattern')?.value ?? '';
    if (!this.patternForm.valid || !pattern) {
      return false;
    }
    const placeholderRegex = /\{[a-zA-Z0-9_]+(?::[^{}]+)?\}|\*/;
    return placeholderRegex.test(pattern);
  }

  getPlaceholderLabel(name: string): string {
    return name === '*' ? '*' : `{${name}}`;
  }

  getPlaceholderTooltip(placeholder: PatternPlaceholder): string {
    return `${placeholder.description} (e.g., ${placeholder.example})`;
  }

  getPreviewClass(preview: PreviewResult): Record<string, boolean> {
    return {
      'preview-success': preview.success,
      'preview-failure': !preview.success
    };
  }

  getPreviewIconClass(preview: PreviewResult): string {
    return preview.success ? 'pi-check-circle' : 'pi-times-circle';
  }

  getPreviewEntries(preview: PreviewResult): Array<{key: string; value: string}> {
    return Object.entries(preview.preview).map(([key, value]) => ({key, value}));
  }

  getErrorMessage(preview: PreviewResult): string {
    return preview.errorMessage || 'Pattern did not match';
  }

  getErrorTooltip(preview: PreviewResult): string {
    return preview.success ? '' : (preview.errorMessage || 'Pattern did not match filename structure');
  }
}
