import {Component, inject, OnInit} from '@angular/core';
import {FileSelectEvent, FileUpload, FileUploadHandlerEvent} from 'primeng/fileupload';
import {Button} from 'primeng/button';
import {AsyncPipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {MessageService} from 'primeng/api';
import {Select} from 'primeng/select';
import {Badge} from 'primeng/badge';
import {LibraryService} from '../../../features/book/service/library.service';
import {Library, LibraryPath} from '../../../features/book/model/library.model';
import {LibraryState} from '../../../features/book/model/state/library-state.model';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';
import {Book} from '../../../features/book/model/book.model';
import {HttpClient} from '@angular/common/http';
import {Tooltip} from 'primeng/tooltip';
import {AppSettingsService} from '../../service/app-settings.service';
import {filter, take} from 'rxjs/operators';
import {AppSettings} from '../../model/app-settings.model';
import {SelectButton} from 'primeng/selectbutton';

interface UploadingFile {
  file: File;
  status: 'Pending' | 'Uploading' | 'Uploaded' | 'Failed';
  errorMessage?: string;
}

@Component({
  selector: 'app-book-uploader',
  standalone: true,
  imports: [
    FileUpload,
    Button,
    AsyncPipe,
    FormsModule,
    Select,
    Badge,
    Tooltip,
    SelectButton
  ],
  templateUrl: './book-uploader.component.html',
  styleUrl: './book-uploader.component.scss'
})
export class BookUploaderComponent implements OnInit {
  files: UploadingFile[] = [];
  isUploading: boolean = false;
  _selectedLibrary: Library | null = null;
  selectedPath: LibraryPath | null = null;

  private readonly libraryService = inject(LibraryService);
  private readonly messageService = inject(MessageService);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly http = inject(HttpClient);

  readonly libraryState$: Observable<LibraryState> = this.libraryService.libraryState$;
  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;
  maxFileSizeBytes?: number;
  stateOptions = [
    {label: 'Library', value: 'library'},
    {label: 'Bookdrop', value: 'bookdrop'}
  ];
  value = 'library';

  ngOnInit(): void {
    this.appSettings$
      .pipe(
        filter(settings => settings != null),
        take(1)
      )
      .subscribe(settings => {
        this.maxFileSizeBytes = (settings?.maxFileUploadSizeInMb ?? 100) * 1024 * 1024;
      });

    this.libraryState$.subscribe(state => {
      if (state?.libraries?.length !== 1 || this.selectedLibrary) {
        return;
      }

      this.selectedLibrary = state.libraries[0];
    });
  }

  get selectedLibrary(): Library | null {
    return this._selectedLibrary;
  }

  set selectedLibrary(library: Library | null) {
    this._selectedLibrary = library;

    if(library?.paths?.length === 1) {
      this.selectedPath = library.paths[0];
    }
  }

  hasPendingFiles(): boolean {
    return this.files.some(f => f.status === 'Pending');
  }

  filesPresent(): boolean {
    return this.files.length > 0;
  }

  choose(_event: any, chooseCallback: () => void): void {
    chooseCallback();
  }

  onClear(clearCallback: () => void): void {
    clearCallback();
    this.files = [];
  }

  onFilesSelect(event: FileSelectEvent): void {
    const newFiles = event.currentFiles;
    for (const file of newFiles) {
      const exists = this.files.some(f => f.file.name === file.name && f.file.size === file.size);
      if (!exists) {
        this.files.unshift({file, status: 'Pending'});
      }
    }
  }

  onRemoveTemplatingFile(_event: any, _file: File, removeFileCallback: (event: any, index: number) => void, index: number): void {
    removeFileCallback(_event, index);
  }

  uploadEvent(uploadCallback: () => void): void {
    uploadCallback();
  }

  uploadFiles(event: FileUploadHandlerEvent): void {
    if (this.value === 'library' && (!this.selectedLibrary || !this.selectedPath)) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Missing Data',
        detail: 'Please select a library and path before uploading.',
        life: 4000
      });
      return;
    }

    const filesToUpload = this.files.filter(f => f.status === 'Pending');
    if (filesToUpload.length === 0) return;

    this.isUploading = true;
    this.uploadBatch(filesToUpload, 0, 5);
  }

  private uploadBatch(files: UploadingFile[], startIndex: number, batchSize: number): void {
    const batch = files.slice(startIndex, startIndex + batchSize);
    if (batch.length === 0) {
      this.isUploading = false;
      return;
    }

    let pending = batch.length;

    for (const uploadFile of batch) {
      uploadFile.status = 'Uploading';

      const formData = new FormData();
      formData.append('file', uploadFile.file);

      let uploadUrl: string;
      if (this.value === 'library') {
        const libraryId = this.selectedLibrary!.id!.toString();
        const pathId = this.selectedPath!.id!.toString();
        formData.append('libraryId', libraryId);
        formData.append('pathId', pathId);
        uploadUrl = `${API_CONFIG.BASE_URL}/api/v1/files/upload`;
      } else {
        uploadUrl = `${API_CONFIG.BASE_URL}/api/v1/files/upload/bookdrop`;
      }

      this.http.post<Book>(uploadUrl, formData).subscribe({
        next: () => {
          uploadFile.status = 'Uploaded';
          if (--pending === 0) {
            this.uploadBatch(files, startIndex + batchSize, batchSize);
          }
        },
        error: (err) => {
          uploadFile.status = 'Failed';
          uploadFile.errorMessage = err?.error?.message || 'Upload failed due to unknown error.';
          console.error('Upload failed for', uploadFile.file.name, err);
          if (--pending === 0) {
            this.uploadBatch(files, startIndex + batchSize, batchSize);
          }
        }
      });
    }
  }

  isChooseDisabled(): boolean {
    if (this.value === 'bookdrop') {
      return this.isUploading;
    }
    return !this.selectedLibrary || !this.selectedPath || this.isUploading;
  }

  isUploadDisabled(): boolean {
    return this.isChooseDisabled() || !this.filesPresent() || !this.hasPendingFiles();
  }

  formatSize(bytes: number): string {
    const k = 1024;
    const dm = 2;
    if (bytes < k) return `${bytes} B`;
    if (bytes < k * k) return `${(bytes / k).toFixed(dm)} KB`;
    return `${(bytes / (k * k)).toFixed(dm)} MB`;
  }

  getBadgeSeverity(status: UploadingFile['status']): 'info' | 'warn' | 'success' | 'danger' {
    switch (status) {
      case 'Pending':
        return 'warn';
      case 'Uploading':
        return 'info';
      case 'Uploaded':
        return 'success';
      case 'Failed':
        return 'danger';
      default:
        return 'info';
    }
  }
}
