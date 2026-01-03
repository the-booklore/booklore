import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {forkJoin, Observable, of} from 'rxjs';
import {AppSettingKey, AppSettings} from '../../../shared/model/app-settings.model';
import {catchError, filter, take} from 'rxjs/operators';
import {Library} from '../../book/model/library.model';
import {LibraryService} from '../../book/service/library.service';
import {InputText} from 'primeng/inputtext';
import {Divider} from 'primeng/divider';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {LibraryCustomField} from '../../book/model/library-custom-field.model';

@Component({
  selector: 'app-file-naming-pattern',
  templateUrl: './file-naming-pattern.component.html',
  standalone: true,
  imports: [FormsModule, Button, InputText, Divider, ExternalDocLinkComponent],
  styleUrls: ['./file-naming-pattern.component.scss'],
})
export class FileNamingPatternComponent implements OnInit {
  readonly exampleMetadata: Record<string, string> = {
    title: "The Name of the Wind",
    subtitle: "Special Edition",
    authors: "Patrick Rothfuss",
    year: "2007",
    series: "The Kingkiller Chronicle",
    seriesIndex: "01",
    language: "English",
    publisher: "DAW Books",
    isbn: "9780756404741",
  };

  defaultPattern = '';
  libraries: Library[] = [];
  defaultErrorMessage = '';

  customFieldsByLibraryId: Record<number, LibraryCustomField[]> = {};

  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);
  private libraryService = inject(LibraryService);

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.appSettings$
      .pipe(filter((settings) => settings != null), take(1))
      .subscribe((settings) => {
        this.defaultPattern = settings?.uploadPattern ?? '';
      });

    this.libraryService.libraryState$
      .pipe(filter(state => state.loaded && !!state.libraries))
      .subscribe(state => {
        this.libraries = state.libraries ?? [];

        // Fetch custom fields so we can show placeholders and preview them per library.
        this.libraries.forEach(lib => {
          if (!lib.id || this.customFieldsByLibraryId[lib.id]) {
            return;
          }
          this.libraryService.getCustomFields(lib.id).subscribe({
            next: (fields) => (this.customFieldsByLibraryId[lib.id!] = fields),
            error: () => (this.customFieldsByLibraryId[lib.id!] = []),
          });
        });
      });
  }

  private replacePlaceholders(pattern: string, values: Record<string, string>): string {
    pattern = pattern.replace(/<([^<>]+)>/g, (_, block) => {
      const placeholders = [...block.matchAll(/{(.*?)}/g)].map((m) => m[1]);
      const allHaveValues = placeholders.every((key) => values[key]?.trim());
      return allHaveValues
        ? block.replace(/{(.*?)}/g, (_: string, key: string) => values[key] ?? '')
        : '';
    });
    return pattern.replace(/{(.*?)}/g, (_, key) => values[key] ?? '').trim();
  }

  private appendExtensionIfMissing(path: string, ext = '.pdf'): string {
    const lastSegment = path.split('/').pop() ?? '';
    const hasExtension = /\.[a-z0-9]{2,5}$/i.test(lastSegment);
    return hasExtension ? path : path + ext;
  }

  private buildPreviewValues(customFields: LibraryCustomField[] | undefined): Record<string, string> {
    const values: Record<string, string> = { ...this.exampleMetadata };

    (customFields ?? []).forEach((f) => {
      const key = `custom:${f.name}`;
      values[key] = f.defaultValue ?? 'Example';
    });

    return values;
  }

  private generatePreview(pattern: string, customFields?: LibraryCustomField[]): string {
    let path = this.replacePlaceholders(pattern || '', this.buildPreviewValues(customFields));

    if (!path) return '/original_filename.pdf';
    if (path.endsWith('/')) return path + 'original_filename.pdf';
    if (path.includes('{originalFilename}')) {
      path = path.replace('{originalFilename}', 'original_filename.pdf');
      return path.startsWith('/') ? path : `/${path}`;
    }
    path = this.appendExtensionIfMissing(path);
    return path.startsWith('/') ? path : `/${path}`;
  }

  generateDefaultPreview(): string {
    return this.generatePreview(this.defaultPattern);
  }

  generateLibraryPreview(library: Library): string {
    const customFields = library.id ? this.customFieldsByLibraryId[library.id] : undefined;
    return this.generatePreview(library.fileNamingPattern || this.defaultPattern, customFields);
  }

  validatePattern(pattern: string): boolean {
    const validPatternRegex = /^[[\]\w\s\-{}/().<>.,:'"#]*$/;
    return validPatternRegex.test(pattern);
  }

  onDefaultPatternChange(pattern: string): void {
    this.defaultPattern = pattern;
    this.defaultErrorMessage = this.validatePattern(pattern) ? '' : 'Pattern contains invalid characters.';
  }

  onLibraryPatternChange(_library: Library): void {
    if (_library) {
      // noop
    }
    // Optionally add per-library validation here
  }

  clearLibraryPattern(library: Library): void {
    library.fileNamingPattern = '';
  }

  savePatterns(): void {
    if (this.defaultErrorMessage) {
      this.showMessage('error', 'Invalid Pattern', 'Please fix errors before saving.');
      return;
    }
    this.appSettingsService
      .saveSettings([
        { key: AppSettingKey.UPLOAD_FILE_PATTERN, newValue: this.defaultPattern },
      ])
      .subscribe({
        next: () => this.showMessage('success', 'Settings Saved', 'The default pattern was successfully saved!'),
        error: () => this.showMessage('error', 'Error', 'There was an error saving the settings.'),
      });
  }

  saveLibraryPatterns(): void {
    const patchRequests = this.libraries.map(library =>
      this.libraryService.updateLibraryFileNamingPattern(library.id!, library.fileNamingPattern || '').pipe(
        catchError(() => of(null))
      )
    );
    forkJoin(patchRequests).subscribe(results => {
      const failures = results.filter(result => result === null);
      if (failures.length === 0) {
        this.showMessage('success', 'Library Patterns Saved', 'Library-specific patterns were successfully saved!');
      } else {
        this.showMessage('error', 'Error', `Failed to save ${failures.length} library pattern(s).`);
      }
    });
  }

  private showMessage(severity: 'success' | 'error', summary: string, detail: string): void {
    this.messageService.add({ severity, summary, detail });
  }
}
