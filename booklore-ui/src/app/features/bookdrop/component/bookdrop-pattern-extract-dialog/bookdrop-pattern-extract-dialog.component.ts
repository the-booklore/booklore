import {Component, inject, OnInit} from '@angular/core';
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

  sampleFiles: string[] = [];
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
    {name: 'SeriesName', description: 'Series or comic name', example: 'One Punch Man'},
    {name: 'Title', description: 'Book title', example: 'The Great Gatsby'},
    {name: 'Authors', description: 'Author name(s)', example: 'John Smith'},
    {name: 'SeriesNumber', description: 'Book number in series', example: '25'},
    {name: 'SeriesBookNumber', description: 'Alias for SeriesNumber', example: '12'},
    {name: 'Year', description: 'Publication year (4 digits)', example: '2023'},
    {name: 'Publisher', description: 'Publisher name', example: 'Marvel'},
    {name: 'Language', description: 'Language code', example: 'en'},
    {name: 'SeriesTotal', description: 'Total books in series', example: '50'},
  ];

  commonPatterns = [
    {label: 'Series - Ch Number', pattern: '{SeriesName} - Ch {SeriesNumber}'},
    {label: 'Series - Volume Number', pattern: '{SeriesName} - Vol {SeriesNumber}'},
    {label: 'Series #Number', pattern: '{SeriesName} #{SeriesNumber}'},
    {label: 'Title (Year)', pattern: '{Title} ({Year})'},
    {label: 'Author - Title', pattern: '{Authors} - {Title}'},
    {label: 'Series Vol.X #Y', pattern: '{SeriesName} Vol.{SeriesTotal} #{SeriesNumber}'},
  ];

  ngOnInit(): void {
    this.sampleFiles = this.config.data?.sampleFiles ?? [];
    this.fileCount = this.config.data?.fileCount ?? 0;
    this.selectAll = this.config.data?.selectAll ?? false;
    this.excludedIds = this.config.data?.excludedIds ?? [];
    this.selectedIds = this.config.data?.selectedIds ?? [];
  }

  insertPlaceholder(placeholderName: string): void {
    const currentPattern = this.patternForm.get('pattern')?.value ?? '';
    this.patternForm.get('pattern')?.setValue(currentPattern + `{${placeholderName}}`);
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

    this.previewResults = this.sampleFiles.map(fileName => {
      const result = this.simulateExtraction(fileName, pattern);
      return {
        fileName,
        success: result.success,
        preview: result.extracted,
      };
    });
  }

  private simulateExtraction(filename: string, pattern: string): { success: boolean; extracted: Record<string, string> } {
    const filenameWithoutExtension = this.getBaseName(filename);
    const placeholderRegex = /\{(\w+)}/g;
    const placeholders: string[] = [];
    let regexPattern = pattern;

    let match;
    while ((match = placeholderRegex.exec(pattern)) !== null) {
      placeholders.push(match[1]);
    }

    regexPattern = this.escapeRegex(pattern);
    
    const lastPlaceholderMatch = pattern.match(/\{(\w+)}(?![\s\S]*\{)/);
    const lastPlaceholderName = lastPlaceholderMatch ? lastPlaceholderMatch[1] : null;
    const patternEndsWithPlaceholder = pattern.trimEnd().endsWith('}');
    
    regexPattern = regexPattern.replace(/\\\{(\w+)\\}/g, (_, placeholderName) => {
      if (['SeriesNumber', 'SeriesBookNumber', 'SeriesTotal'].includes(placeholderName)) {
        return '(\\d+(?:\\.\\d+)?)';
      }
      if (placeholderName === 'Year') {
        return '(\\d{4})';
      }
      
      const isLastPlaceholderWithNoTextAfter = (placeholderName === lastPlaceholderName && patternEndsWithPlaceholder);
      return isLastPlaceholderWithNoTextAfter ? '(.+)' : '(.+?)';
    });

    try {
      const regex = new RegExp(regexPattern);
      const extractMatch = regex.exec(filenameWithoutExtension);

      if (!extractMatch) {
        return {success: false, extracted: {}};
      }

      const extracted: Record<string, string> = {};
      placeholders.forEach((placeholder, index) => {
        extracted[placeholder] = extractMatch[index + 1]?.trim() ?? '';
      });

      return {success: true, extracted};
    } catch {
      return {success: false, extracted: {}};
    }
  }

  private escapeRegex(text: string): string {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  private getBaseName(filename: string): string {
    const lastDot = filename.lastIndexOf('.');
    return lastDot > 0 ? filename.substring(0, lastDot) : filename;
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
    return this.patternForm.valid && (this.patternForm.get('pattern')?.value?.includes('{') ?? false);
  }

  getPlaceholderLabel(name: string): string {
    return '{' + name + '}';
  }

  getPlaceholderTooltip(placeholder: PatternPlaceholder): string {
    return placeholder.description + ' (e.g., ' + placeholder.example + ')';
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
}
