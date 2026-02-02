import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { DynamicDialogRef, DynamicDialogConfig } from 'primeng/dynamicdialog';
import { MessageService } from 'primeng/api';

// PrimeNG imports
import { ButtonModule } from 'primeng/button';
import { DropdownModule } from 'primeng/dropdown';
import { InputNumberModule } from 'primeng/inputnumber';
import { SliderModule } from 'primeng/slider';
import { ProgressSpinnerModule } from 'primeng/progressspinner';

import { BookConversionService } from '../../services/book-conversion.service';

interface DeviceProfile {
  value: string;
  displayName: string;
  width: number;
  height: number;
  supportsCustom: boolean;
}

interface ConversionRequest {
  bookId: number;
  deviceProfile: string;
  customWidth?: number;
  customHeight?: number;
  compressionPercentage?: number;
}

@Component({
  selector: 'app-cbz-conversion-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    ButtonModule,
    DropdownModule,
    InputNumberModule,
    SliderModule,
    ProgressSpinnerModule
  ],
  templateUrl: './cbz-conversion-dialog.component.html',
  styleUrls: ['./cbz-conversion-dialog.component.scss']
})
export class CbzConversionDialogComponent implements OnInit {
  conversionForm: FormGroup;
  deviceProfiles: DeviceProfile[] = [];
  selectedProfile?: DeviceProfile;
  loading = false;
  converting = false;
  bookId: number;
  bookTitle: string;

  constructor(
    private fb: FormBuilder,
    private conversionService: BookConversionService,
    private messageService: MessageService,
    public ref: DynamicDialogRef,
    public config: DynamicDialogConfig
  ) {
    this.bookId = this.config.data?.bookId;
    this.bookTitle = this.config.data?.bookTitle || 'Unknown';

    this.conversionForm = this.fb.group({
      deviceProfile: [null, Validators.required],
      customWidth: [{ value: null, disabled: true }, [Validators.min(100)]],
      customHeight: [{ value: null, disabled: true }, [Validators.min(100)]],
      compressionPercentage: [85, [Validators.required, Validators.min(1), Validators.max(100)]]
    });
  }

  ngOnInit(): void {
    this.loadDeviceProfiles();
    this.setupFormListeners();
  }

  private loadDeviceProfiles(): void {
    this.loading = true;
    this.conversionService.getDeviceProfiles().subscribe({
      next: (profiles) => {
        this.deviceProfiles = profiles;
        this.loading = false;
        
        // Set default profile (Kindle Paperwhite is a good default)
        const defaultProfile = profiles.find(p => p.value === 'KINDLE_PAPERWHITE');
        if (defaultProfile) {
          this.conversionForm.patchValue({ deviceProfile: defaultProfile.value });
          this.selectedProfile = defaultProfile;
        }
      },
      error: (error) => {
        console.error('Failed to load device profiles', error);
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to load device profiles'
        });
        this.loading = false;
      }
    });
  }

  private setupFormListeners(): void {
    // Watch for device profile changes
    this.conversionForm.get('deviceProfile')?.valueChanges.subscribe((profileValue) => {
      const profile = this.deviceProfiles.find(p => p.value === profileValue);
      this.selectedProfile = profile;

      const customWidthControl = this.conversionForm.get('customWidth');
      const customHeightControl = this.conversionForm.get('customHeight');

      if (profile?.supportsCustom) {
        // Enable custom dimension inputs for "Other" profile
        customWidthControl?.enable();
        customHeightControl?.enable();
        customWidthControl?.setValidators([Validators.required, Validators.min(100)]);
        customHeightControl?.setValidators([Validators.required, Validators.min(100)]);
      } else {
        // Disable and clear custom dimensions for preset profiles
        customWidthControl?.disable();
        customHeightControl?.disable();
        customWidthControl?.clearValidators();
        customHeightControl?.clearValidators();
        customWidthControl?.setValue(null);
        customHeightControl?.setValue(null);
      }

      customWidthControl?.updateValueAndValidity();
      customHeightControl?.updateValueAndValidity();
    });
  }

  get isCustomProfile(): boolean {
    return this.selectedProfile?.supportsCustom || false;
  }

  get targetResolution(): string {
    if (!this.selectedProfile) {
      return 'No profile selected';
    }

    if (this.isCustomProfile) {
      const width = this.conversionForm.get('customWidth')?.value;
      const height = this.conversionForm.get('customHeight')?.value;
      if (width && height) {
        return `${width} × ${height} pixels`;
      }
      return 'Enter custom dimensions';
    }

    return `${this.selectedProfile.width} × ${this.selectedProfile.height} pixels`;
  }

  onConvert(): void {
    if (this.conversionForm.invalid) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Validation Error',
        detail: 'Please fill in all required fields correctly'
      });
      return;
    }

    this.converting = true;

    const formValue = this.conversionForm.getRawValue();
    const request: ConversionRequest = {
      bookId: this.bookId,
      deviceProfile: formValue.deviceProfile,
      compressionPercentage: formValue.compressionPercentage
    };

    // Add custom dimensions if using OTHER profile
    if (this.isCustomProfile) {
      request.customWidth = formValue.customWidth;
      request.customHeight = formValue.customHeight;
    }

    this.conversionService.convertCbzToEpub(request).subscribe({
      next: (response) => {
        this.messageService.add({
          severity: 'success',
          summary: 'Conversion Successful',
          detail: `Created: ${response.fileName}`,
          life: 5000
        });
        this.converting = false;
        this.ref.close({ success: true, newBookId: response.newBookId });
      },
      error: (error) => {
        console.error('Conversion failed', error);
        this.messageService.add({
          severity: 'error',
          summary: 'Conversion Failed',
          detail: error.error?.message || 'An error occurred during conversion'
        });
        this.converting = false;
      }
    });
  }

  onCancel(): void {
    this.ref.close();
  }
}
