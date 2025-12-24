import { Component, EventEmitter, Input, Output, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { ColorPicker } from 'primeng/colorpicker';
import { Textarea } from 'primeng/textarea';
import { InputNumber } from 'primeng/inputnumber';
import { Button } from 'primeng/button';
import { BookMark, UpdateBookMarkRequest } from '../../../../shared/service/book-mark.service';
import { PrimeTemplate } from 'primeng/api';

export interface BookmarkFormData {
  title: string;
  color: string;
  notes: string;
  priority: number | null;
}

@Component({
  selector: 'app-bookmark-edit-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Dialog,
    InputText,
    ColorPicker,
    Textarea,
    InputNumber,
    Button,
    PrimeTemplate
  ],
  template: `
    <p-dialog
      [(visible)]="visible"
      [modal]="true"
      [closable]="true"
      [style]="{width: '500px'}"
      [draggable]="false"
      [resizable]="false"
      [closeOnEscape]="true"
      [appendTo]="'body'"
      header="Edit Bookmark"
      (onHide)="onDialogHide()">

      @if (formData) {
        <div class="p-4">
          <div class="field mb-4">
            <label for="title" class="block text-sm font-medium mb-2">Title <span class="text-red-500">*</span></label>
            <input
              pInputText
              id="title"
              type="text"
              [(ngModel)]="formData.title"
              class="w-full"
              [class.ng-invalid]="titleError"
              [class.ng-dirty]="titleError"
              placeholder="Enter bookmark title"
              [maxlength]="255"
              (ngModelChange)="titleError = false">
            @if (titleError) {
              <small class="text-red-500">Title is required</small>
            }
          </div>

          <div class="field mb-4">
            <label for="color" class="block text-sm font-medium mb-2">Color</label>
            <div class="flex align-items-center gap-2">
              <p-colorPicker
                [(ngModel)]="formData.color"
                [appendTo]="'body'"
                format="hex">
              </p-colorPicker>
              <input
                pInputText
                [(ngModel)]="formData.color"
                class="w-8rem"
                placeholder="#000000"
                pattern="^#[0-9A-Fa-f]{6}$">
            </div>
          </div>

          <div class="field mb-4">
            <label for="notes" class="block text-sm font-medium mb-2">Notes</label>
            <textarea
              pInputTextarea
              id="notes"
              [(ngModel)]="formData.notes"
              class="w-full"
              rows="3"
              placeholder="Add notes about this bookmark"
              [maxlength]="2000">
            </textarea>
            <small class="text-muted">{{ formData.notes.length || 0 }}/2000</small>
          </div>

          <div class="field mb-4">
            <label for="priority" class="block text-sm font-medium mb-2">Priority (1 = High, 5 = Low)</label>
            <p-inputNumber
              id="priority"
              [(ngModel)]="formData.priority"
              [min]="1"
              [max]="5"
              [showButtons]="true"
              buttonLayout="horizontal"
              spinnerMode="horizontal"
              decrementButtonClass="p-button-secondary"
              incrementButtonClass="p-button-secondary"
              decrementButtonIcon="pi pi-minus"
              incrementButtonIcon="pi pi-plus">
            </p-inputNumber>
          </div>
        </div>
      }

      <ng-template pTemplate="footer">
        <div class="flex justify-content-between">
          <p-button
            label="Cancel"
            icon="pi pi-times"
            (click)="onCancel()"
            [text]="true"
            severity="secondary">
          </p-button>
          <p-button
            label="Save"
            icon="pi pi-check"
            (click)="onSave()"
            [loading]="isSaving"
            [disabled]="!formData || isSaving">
          </p-button>
        </div>
      </ng-template>
    </p-dialog>
  `
})
export class BookmarkEditDialogComponent implements OnChanges {
  @Input() visible = false;
  @Input() bookmark: BookMark | null = null;
  @Input() isSaving = false;
  
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() save = new EventEmitter<UpdateBookMarkRequest>();
  @Output() cancelEdit = new EventEmitter<void>();

  formData: BookmarkFormData | null = null;
  titleError = false;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['bookmark'] && this.bookmark) {
      this.titleError = false;
      this.formData = {
        title: this.bookmark.title || '',
        color: this.bookmark.color || '#3B82F6',
        notes: this.bookmark.notes || '',
        priority: this.bookmark.priority ?? 3
      };
    }
  }

  onSave(): void {
    if (!this.formData) return;

    if (!this.formData.title || !this.formData.title.trim()) {
      this.titleError = true;
      return;
    }

    const request: UpdateBookMarkRequest = {
      title: this.formData.title.trim(),
      color: this.formData.color || undefined,
      notes: this.formData.notes || undefined,
      priority: this.formData.priority ?? undefined
    };
    
    this.save.emit(request);
  }

  onDialogHide(): void {
    // When dialog is closed via X button, treat it as cancel
    this.onCancel();
  }

  onCancel(): void {
    this.formData = null;  // Clear form data
    this.visible = false;
    this.visibleChange.emit(false);
    this.cancelEdit.emit();
  }
}