import {Component, OnDestroy, OnInit} from '@angular/core';
import {NgIf} from '@angular/common';
import {DomSanitizer, SafeResourceUrl} from '@angular/platform-browser';
import {API_CONFIG} from '../../core/config/api-config';

@Component({
  selector: 'app-ephemera',
  standalone: true,
  imports: [NgIf],
  template: `
    <section class="flex flex-col" style="height: calc(100dvh - 5rem);">
      <header class="border-b border-surface-300 bg-surface-0 p-4 dark:border-surface-700 dark:bg-surface-900">
        <h1 class="text-2xl font-semibold text-surface-900 dark:text-surface-0">Ephemera</h1>
        <p class="text-sm text-surface-600 dark:text-surface-300">Ephemera runs inside Booklore. Close this tab when you are finished.</p>
      </header>

      <div class="relative flex-1">
        <iframe
          *ngIf="iframeUrl"
          class="absolute inset-0 h-full w-full border-0"
          [src]="iframeUrl"
          title="Ephemera"
          (load)="handleLoad()"
        ></iframe>

        <div *ngIf="isLoading" class="absolute inset-0 flex items-center justify-center bg-surface-50 dark:bg-surface-900">
          <div class="text-center text-surface-600 dark:text-surface-200">
            <i class="pi pi-spin pi-spinner text-3xl mb-3 block"></i>
            <p>Connecting to Ephemera...</p>
          </div>
        </div>
      </div>
    </section>
  `,
})
export class EphemeraComponent implements OnInit, OnDestroy {
  iframeUrl?: SafeResourceUrl;
  isLoading = true;

  constructor(private sanitizer: DomSanitizer) {}

  ngOnInit(): void {
    const ephemeraUrl = `${API_CONFIG.BASE_URL}/api/v1/ephemera/`;
    this.iframeUrl = this.sanitizer.bypassSecurityTrustResourceUrl(ephemeraUrl);
  }

  ngOnDestroy(): void {
    this.isLoading = false;
  }

  handleLoad(): void {
    this.isLoading = false;
  }
}

