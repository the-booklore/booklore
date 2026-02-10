import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {Observable, Subject} from 'rxjs';
import {filter, switchMap, takeUntil} from 'rxjs/operators';
import {Book} from '../../../../book/model/book.model';
import {SidecarMetadata, SidecarService, SidecarSyncStatus} from '../../../service/sidecar.service';
import {MessageService} from 'primeng/api';
import {Button} from 'primeng/button';
import {Tag} from 'primeng/tag';
import {Tooltip} from 'primeng/tooltip';
import {DatePipe, JsonPipe} from '@angular/common';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-sidecar-viewer',
  standalone: true,
  templateUrl: './sidecar-viewer.component.html',
  styleUrls: ['./sidecar-viewer.component.scss'],
  imports: [Button, Tag, Tooltip, JsonPipe, DatePipe, TranslocoDirective]
})
export class SidecarViewerComponent implements OnInit, OnDestroy {
  @Input() book$!: Observable<Book>;

  private sidecarService = inject(SidecarService);
  private messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private destroy$ = new Subject<void>();

  sidecarContent: SidecarMetadata | null = null;
  syncStatus: SidecarSyncStatus = 'NOT_APPLICABLE';
  loading = false;
  exporting = false;
  importing = false;
  currentBookId: number | null = null;
  error: string | null = null;

  ngOnInit(): void {
    this.book$.pipe(
      filter((book): book is Book => !!book),
      takeUntil(this.destroy$)
    ).subscribe(book => {
      this.currentBookId = book.id;
      this.loadSidecarData(book.id);
    });
  }

  loadSidecarData(bookId: number): void {
    this.loading = true;
    this.error = null;

    this.sidecarService.getSyncStatus(bookId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (response) => {
        this.syncStatus = response.status;

        if (response.status !== 'MISSING' && response.status !== 'NOT_APPLICABLE') {
          this.loadSidecarContent(bookId);
        } else {
          this.sidecarContent = null;
          this.loading = false;
        }
      },
      error: (err) => {
        this.syncStatus = 'NOT_APPLICABLE';
        this.sidecarContent = null;
        this.loading = false;
        console.error('Failed to get sync status:', err);
      }
    });
  }

  private loadSidecarContent(bookId: number): void {
    this.sidecarService.getSidecarContent(bookId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (content) => {
        this.sidecarContent = content;
        this.loading = false;
      },
      error: (err) => {
        this.sidecarContent = null;
        this.loading = false;
        if (err.status !== 404) {
          console.error('Failed to load sidecar content:', err);
        }
      }
    });
  }

  exportToSidecar(): void {
    if (!this.currentBookId) return;

    this.exporting = true;
    this.sidecarService.exportToSidecar(this.currentBookId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.sidecar.toast.exportSuccessSummary'),
          detail: this.t.translate('metadata.sidecar.toast.exportSuccessDetail')
        });
        this.loadSidecarData(this.currentBookId!);
        this.exporting = false;
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.sidecar.toast.exportFailedSummary'),
          detail: this.t.translate('metadata.sidecar.toast.exportFailedDetail')
        });
        this.exporting = false;
        console.error('Export failed:', err);
      }
    });
  }

  importFromSidecar(): void {
    if (!this.currentBookId) return;

    this.importing = true;
    this.sidecarService.importFromSidecar(this.currentBookId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.sidecar.toast.importSuccessSummary'),
          detail: this.t.translate('metadata.sidecar.toast.importSuccessDetail')
        });
        this.loadSidecarData(this.currentBookId!);
        this.importing = false;
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.sidecar.toast.importFailedSummary'),
          detail: this.t.translate('metadata.sidecar.toast.importFailedDetail')
        });
        this.importing = false;
        console.error('Import failed:', err);
      }
    });
  }

  getSyncStatusSeverity(): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' {
    switch (this.syncStatus) {
      case 'IN_SYNC':
        return 'success';
      case 'OUTDATED':
        return 'warn';
      case 'CONFLICT':
        return 'danger';
      case 'MISSING':
        return 'secondary';
      default:
        return 'info';
    }
  }

  getSyncStatusLabel(): string {
    switch (this.syncStatus) {
      case 'IN_SYNC':
        return this.t.translate('metadata.sidecar.syncStatus.inSync');
      case 'OUTDATED':
        return this.t.translate('metadata.sidecar.syncStatus.outdated');
      case 'CONFLICT':
        return this.t.translate('metadata.sidecar.syncStatus.conflict');
      case 'MISSING':
        return this.t.translate('metadata.sidecar.syncStatus.missing');
      case 'NOT_APPLICABLE':
        return this.t.translate('metadata.sidecar.syncStatus.notApplicable');
      default:
        return this.t.translate('metadata.sidecar.syncStatus.unknown');
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
