import {Component, ViewChild, ElementRef, OnDestroy} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Button} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {CustomFontService} from '../../../../shared/service/custom-font.service';
import {CustomFont, formatFileSize} from '../../../../shared/model/custom-font.model';
import {InputText} from 'primeng/inputtext';
import {FormsModule} from '@angular/forms';
import {DynamicDialogRef} from 'primeng/dynamicdialog';

@Component({
  selector: 'app-font-upload-dialog',
  standalone: true,
  imports: [CommonModule, Button, InputText, FormsModule],
  templateUrl: './font-upload-dialog.component.html',
  styleUrls: ['./font-upload-dialog.component.scss']
})
export class FontUploadDialogComponent implements OnDestroy {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  isUploading = false;
  uploadedFontName = '';
  selectedFile: File | null = null;
  isDragOver = false;
  previewFontFamily: string | null = null;

  readonly maxFileSize = 5242880; // 5MB
  readonly maxFonts = 10;
  readonly acceptedFormats = ['.ttf', '.otf', '.woff', '.woff2'];

  constructor(
    private customFontService: CustomFontService,
    private messageService: MessageService,
    private dialogRef: DynamicDialogRef
  ) {}

  onUploadZoneClick(): void {
    if (this.isUploading) {
      return;
    }
    this.fileInput.nativeElement.click();
  }

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];

    if (!file) return;

    // Validate file type
    const fileName = file.name.toLowerCase();
    const isValidFormat = this.acceptedFormats.some(format => fileName.endsWith(format));

    if (!isValidFormat) {
      this.messageService.add({
        severity: 'error',
        summary: 'Invalid File Type',
        detail: 'Please upload a TTF, OTF, WOFF, or WOFF2 font file'
      });
      input.value = ''; // Reset input
      return;
    }

    // Validate file size
    if (file.size > this.maxFileSize) {
      this.messageService.add({
        severity: 'error',
        summary: 'File Too Large',
        detail: `File size must not exceed ${this.formatFileSize(this.maxFileSize)}`
      });
      input.value = ''; // Reset input
      return;
    }

    this.selectedFile = file;
    this.uploadedFontName = file.name.replace(/\.[^/.]+$/, ''); // Remove extension
    this.loadFontPreview(file);
    input.value = ''; // Reset input for future uploads
  }

  async loadFontPreview(file: File): Promise<void> {
    // Clean up any previous preview font before loading new one
    this.cleanupPreviewFont();

    try {
      // Generate a unique font family name for preview
      const previewFontName = `preview-font-${Date.now()}`;

      // Read file as ArrayBuffer
      const arrayBuffer = await file.arrayBuffer();

      // Create a FontFace object
      const fontFace = new FontFace(previewFontName, arrayBuffer);

      // Load the font
      await fontFace.load();

      // Add to document fonts
      document.fonts.add(fontFace);

      // Set the preview font family
      this.previewFontFamily = `"${previewFontName}"`;
    } catch (error) {
      console.error('Failed to load font preview:', error);
      this.previewFontFamily = null;
      this.messageService.add({
        severity: 'warn',
        summary: 'Preview Failed',
        detail: 'Unable to preview font, but you can still upload it'
      });
    }
  }

  uploadFont(): void {
    if (!this.selectedFile) {
      this.messageService.add({
        severity: 'warn',
        summary: 'No File Selected',
        detail: 'Please select a font file to upload'
      });
      return;
    }

    this.isUploading = true;
    this.customFontService.uploadFont(this.selectedFile, this.uploadedFontName || undefined).subscribe({
      next: (font) => {
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: `Font "${font.fontName}" uploaded successfully`
        });
        this.isUploading = false;
        this.dialogRef.close(font); // Return the uploaded font
      },
      error: (error) => {
        console.error('Failed to upload font:', error);
        let errorMessage = 'Failed to upload font';
        if (error.status === 400) {
          errorMessage = 'Invalid file format or quota exceeded';
        }
        this.messageService.add({
          severity: 'error',
          summary: 'Upload Failed',
          detail: errorMessage
        });
        this.isUploading = false;
      }
    });
  }

  formatFileSize(bytes: number): string {
    return formatFileSize(bytes);
  }

  cancel(): void {
    this.cleanupPreviewFont();
    this.dialogRef.close(null);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;

    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      const file = files[0];

      // Validate file type
      const fileName = file.name.toLowerCase();
      const isValidFormat = this.acceptedFormats.some(format => fileName.endsWith(format));

      if (!isValidFormat) {
        this.messageService.add({
          severity: 'error',
          summary: 'Invalid File Type',
          detail: 'Please upload a TTF, OTF, WOFF, or WOFF2 font file'
        });
        return;
      }

      // Validate file size
      if (file.size > this.maxFileSize) {
        this.messageService.add({
          severity: 'error',
          summary: 'File Too Large',
          detail: `File size must not exceed ${this.formatFileSize(this.maxFileSize)}`
        });
        return;
      }

      this.selectedFile = file;
      this.uploadedFontName = file.name.replace(/\.[^/.]+$/, ''); // Remove extension
      this.loadFontPreview(file);
    }
  }

  cleanupPreviewFont(): void {
    if (this.previewFontFamily) {
      // Extract font name from quoted string
      const fontName = this.previewFontFamily.replace(/"/g, '');

      // Remove from document fonts
      document.fonts.forEach(font => {
        if (font.family === fontName) {
          document.fonts.delete(font);
        }
      });

      this.previewFontFamily = null;
    }
  }

  ngOnDestroy(): void {
    this.cleanupPreviewFont();
  }
}
