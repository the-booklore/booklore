import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Dialog } from 'primeng/dialog';
import { Button } from 'primeng/button';
import { BookMark } from '../../../../shared/service/book-mark.service';
import { PrimeTemplate } from 'primeng/api';

@Component({
  selector: 'app-bookmark-view-dialog',
  standalone: true,
  imports: [
    CommonModule,
    Dialog,
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
      header="View Bookmark"
      (onHide)="onClose()">

      @if (bookmark) {
        <div class="p-4">
          <div class="mb-4">
            <h3 class="text-xl font-bold m-0 mb-2 flex align-items-center gap-2">
              <span class="w-1rem h-1rem border-circle inline-block" [style.background-color]="bookmark.color"></span>
              {{ bookmark.title }}
            </h3>
            <div class="text-sm text-500 mb-2">
              Created on {{ bookmark.createdAt | date:'medium' }}
            </div>
            <div class="text-sm font-medium mb-3">
              Priority: {{ bookmark.priority }} ({{ getPriorityLabel(bookmark.priority) }})
            </div>
          </div>

          @if (bookmark.notes) {
            <div class="surface-ground p-3 border-round">
              <div class="text-sm font-bold mb-2">Notes:</div>
              <p class="m-0 white-space-pre-wrap">{{ bookmark.notes }}</p>
            </div>
          } @else {
            <div class="text-500 italic">No notes added.</div>
          }
        </div>
      }

      <ng-template pTemplate="footer">
        <div class="flex justify-content-end">
          <p-button
            label="Close"
            icon="pi pi-times"
            (click)="onClose()"
            [text]="true"
            severity="secondary">
          </p-button>
        </div>
      </ng-template>
    </p-dialog>
  `
})
export class BookmarkViewDialogComponent {
  @Input() visible = false;
  @Input() bookmark: BookMark | null = null;
  @Output() visibleChange = new EventEmitter<boolean>();

  onClose(): void {
    this.visible = false;
    this.visibleChange.emit(false);
  }

  getPriorityLabel(priority: number | undefined): string {
    if (priority === undefined) return 'Normal';
    if (priority <= 1) return 'Highest';
    if (priority === 2) return 'High';
    if (priority === 3) return 'Normal';
    if (priority === 4) return 'Low';
    return 'Lowest';
  }
}
