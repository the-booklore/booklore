import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { FileSelectEvent, FileUploadModule } from 'primeng/fileupload';
import { ToastModule } from 'primeng/toast';
import { ConfigurationExportService, ImportOptions } from '../../../shared/services/configuration-export.service';

@Component({
  selector: 'app-export-import',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    CheckboxModule,
    FileUploadModule,
    ToastModule
  ],
  template: `
    <div class="export-import-section">
      <p-toast />

      <h3>Export/Import Configuration</h3>
      <p class="text-sm text-color-secondary mb-4">
        Export your shelves, magic shelves, and settings as JSON for backup or sharing.
      </p>

      <!-- Export Section -->
      <div class="export-section mb-4 p-3 surface-ground border-round">
        <h4>Export Configuration</h4>
        <p-button
          label="Download Configuration"
          icon="pi pi-download"
          (onClick)="exportConfiguration()"
          [loading]="exporting"
        />
      </div>

      <!-- Import Section -->
      <div class="import-section p-3 surface-ground border-round">
        <h4>Import Configuration</h4>
        <p-fileUpload
          mode="basic"
          accept=".json"
          [auto]="true"
          chooseLabel="Select JSON File"
          (onSelect)="importConfiguration($event)"
          [disabled]="importing"
        />

        <div class="import-options-section">
          <h5>Import Options</h5>
          <p class="text-sm text-color-secondary mb-3">Choose how to handle existing items and what to import</p>

          <div class="import-row">
            <p-checkbox
              binary="true"
              [(ngModel)]="importOptions.skipExisting"
              inputId="skip"
            ></p-checkbox>
            <label for="skip" class="ml-2">Skip existing items (don't update)</label>
          </div>

          <div class="import-row">
            <p-checkbox
              binary="true"
              [(ngModel)]="importOptions.overwrite"
              inputId="overwrite"
            ></p-checkbox>
            <label for="overwrite" class="ml-2">Overwrite existing items</label>
          </div>

          <hr class="my-3" />

          <div class="import-row">
            <p-checkbox
              binary="true"
              [(ngModel)]="importOptions.importShelves"
              inputId="shelves"
            ></p-checkbox>
            <label for="shelves" class="ml-2">Include shelves</label>
          </div>

          <div class="import-row">
            <p-checkbox
              binary="true"
              [(ngModel)]="importOptions.importMagicShelves"
              inputId="magic"
            ></p-checkbox>
            <label for="magic" class="ml-2">Include smart shelves (auto-generated)</label>
          </div>

          <div class="import-row">
            <p-checkbox
              binary="true"
              [(ngModel)]="importOptions.importSettings"
              inputId="settings"
            ></p-checkbox>
            <label for="settings" class="ml-2">Include user settings</label>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .export-import-section {
      padding: 1rem;
    }

    .import-options-section {
      margin-top: 1rem;
    }

    .import-row {
      display: flex;
      align-items: center;
      margin-bottom: 1rem;
    }

    .import-row label {
      font-size: 0.95rem;
      cursor: pointer;
      user-select: none;
    }
  `]
})
export class ExportImportComponent {
  exporting = false;
  importing = false;

  importOptions: ImportOptions = {
    skipExisting: true,
    overwrite: false,
    importShelves: true,
    importMagicShelves: true,
    importSettings: true
  };

  constructor(
    private exportService: ConfigurationExportService,
    private messageService: MessageService
  ) {}

  exportConfiguration() {
    this.exporting = true;
    this.exportService.exportConfiguration().subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `booklore-config-${new Date().toISOString().slice(0, 10)}.json`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.exporting = false;
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Configuration exported successfully'
        });
      },
      error: () => {
        this.exporting = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to export configuration'
        });
      }
    });
  }

  importConfiguration(event: FileSelectEvent) {
    const file = event.files[0];
    if (!file) return;

    this.importing = true;
    const reader = new FileReader();

    reader.onload = (e) => {
      const content = e.target?.result as string;
      this.exportService.importConfiguration(content, this.importOptions).subscribe({
        next: () => {
          this.importing = false;
          this.messageService.add({
            severity: 'success',
            summary: 'Success',
            detail: 'Configuration imported successfully'
          });
        },
        error: (err) => {
          this.importing = false;
          this.messageService.add({
            severity: 'error',
            summary: 'Import Failed',
            detail: err.error?.message || 'Invalid configuration file'
          });
        }
      });
    };

    reader.onerror = () => {
      this.importing = false;
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to read file'
      });
    };

    reader.readAsText(file);
  }
}
