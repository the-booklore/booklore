import {Component, inject, OnInit, ChangeDetectorRef} from '@angular/core';
import {FormControl, FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Button} from 'primeng/button';
import {Checkbox} from 'primeng/checkbox';
import {InputText} from 'primeng/inputtext';
import {AutoComplete} from 'primeng/autocomplete';
import {Divider} from 'primeng/divider';
import {SelectButton} from 'primeng/selectbutton';
import {BookMetadata} from '../../../book/model/book.model';

export interface BulkEditResult {
  fields: Partial<BookMetadata>;
  enabledFields: Set<string>;
  mergeArrays: boolean;
}

interface BulkEditField {
  name: string;
  label: string;
  type: 'text' | 'chips' | 'number';
  controlName: string;
}

@Component({
  selector: 'app-bookdrop-bulk-edit-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    Button,
    Checkbox,
    InputText,
    AutoComplete,
    Divider,
    SelectButton,
  ],
  templateUrl: './bookdrop-bulk-edit-dialog.component.html',
  styleUrl: './bookdrop-bulk-edit-dialog.component.scss'
})
export class BookdropBulkEditDialogComponent implements OnInit {

  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);
  private readonly cdr = inject(ChangeDetectorRef);

  fileCount: number = 0;
  mergeArrays = true;

  enabledFields = new Set<string>();

  bulkEditForm = new FormGroup({
    seriesName: new FormControl(''),
    seriesTotal: new FormControl<number | null>(null),
    authors: new FormControl<string[]>([]),
    publisher: new FormControl(''),
    language: new FormControl(''),
    categories: new FormControl<string[]>([]),
    moods: new FormControl<string[]>([]),
    tags: new FormControl<string[]>([]),
  });

  textFields: BulkEditField[] = [
    {name: 'seriesName', label: 'Series Name', type: 'text', controlName: 'seriesName'},
    {name: 'publisher', label: 'Publisher', type: 'text', controlName: 'publisher'},
    {name: 'language', label: 'Language', type: 'text', controlName: 'language'},
  ];

  numberFields: BulkEditField[] = [
    {name: 'seriesTotal', label: 'Series Total', type: 'number', controlName: 'seriesTotal'},
  ];

  chipFields: BulkEditField[] = [
    {name: 'authors', label: 'Authors', type: 'chips', controlName: 'authors'},
    {name: 'categories', label: 'Genres', type: 'chips', controlName: 'categories'},
    {name: 'moods', label: 'Moods', type: 'chips', controlName: 'moods'},
    {name: 'tags', label: 'Tags', type: 'chips', controlName: 'tags'},
  ];

  mergeOptions = [
    {label: 'Merge', value: true},
    {label: 'Replace', value: false},
  ];

  ngOnInit(): void {
    this.fileCount = this.config.data?.fileCount ?? 0;
    this.setupFormValueChangeListeners();
  }

  private setupFormValueChangeListeners(): void {
    Object.keys(this.bulkEditForm.controls).forEach(fieldName => {
      const control = this.bulkEditForm.get(fieldName);
      control?.valueChanges.subscribe(value => {
        const hasValue = Array.isArray(value) ? value.length > 0 : (value !== null && value !== '' && value !== undefined);
        if (hasValue && !this.enabledFields.has(fieldName)) {
          this.enabledFields.add(fieldName);
          this.cdr.detectChanges();
        }
      });
    });
  }

  onAutoCompleteBlur(fieldName: string, event: Event): void {
    const target = event.target as HTMLInputElement;
    const inputValue = target?.value?.trim();
    if (inputValue) {
      const control = this.bulkEditForm.get(fieldName);
      const currentValue = (control?.value as string[]) || [];
      if (!currentValue.includes(inputValue)) {
        control?.setValue([...currentValue, inputValue]);
      }
      if (target) {
        target.value = '';
      }
    }
    
    if (!this.enabledFields.has(fieldName)) {
      const control = this.bulkEditForm.get(fieldName);
      const value = control?.value;
      if (Array.isArray(value) && value.length > 0) {
        this.enabledFields.add(fieldName);
        this.cdr.detectChanges();
      }
    }
  }

  toggleField(fieldName: string): void {
    if (this.enabledFields.has(fieldName)) {
      this.enabledFields.delete(fieldName);
    } else {
      this.enabledFields.add(fieldName);
    }
  }

  isFieldEnabled(fieldName: string): boolean {
    return this.enabledFields.has(fieldName);
  }

  get hasEnabledFields(): boolean {
    return this.enabledFields.size > 0;
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  apply(): void {
    const formValue = this.bulkEditForm.value;
    const fields: Partial<BookMetadata> = {};

    this.enabledFields.forEach(fieldName => {
      const value = formValue[fieldName as keyof typeof formValue];

      if (value !== undefined && value !== null) {
        (fields as Record<string, unknown>)[fieldName] = value;
      }
    });

    const result: BulkEditResult = {
      fields,
      enabledFields: new Set(this.enabledFields),
      mergeArrays: this.mergeArrays,
    };

    this.dialogRef.close(result);
  }
}
