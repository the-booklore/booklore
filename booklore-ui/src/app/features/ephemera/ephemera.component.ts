import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-ephemera',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="h-full w-full flex flex-col">
      <div class="p-4 border-b border-surface-300 dark:border-surface-700">
        <h1 class="text-2xl font-bold">Ephemera Book Processing</h1>
        <p class="text-surface-600 dark:text-surface-400">
          Access the internal Ephemera book processing application
        </p>
      </div>
      <div class="flex-1 relative">
        <iframe 
          src="/api/ephemera/"
          class="w-full h-full border-0"
          title="Ephemera Book Processing Interface"
          (load)="onIframeLoad()"
          (error)="onIframeError()">
        </iframe>
        <div *ngIf="loading" class="absolute inset-0 flex items-center justify-center bg-surface-100 dark:bg-surface-900">
          <div class="text-center">
            <i class="pi pi-spin pi-spinner text-4xl text-primary-500 mb-4"></i>
            <p class="text-surface-600 dark:text-surface-400">Loading Ephemera...</p>
          </div>
        </div>
        <div *ngIf="error" class="absolute inset-0 flex items-center justify-center bg-surface-100 dark:bg-surface-900">
          <div class="text-center">
            <i class="pi pi-exclamation-triangle text-4xl text-red-500 mb-4"></i>
            <p class="text-surface-600 dark:text-surface-400">Unable to load Ephemera. Please check if the service is running.</p>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      height: 100%;
    }
  `]
})
export class EphemeraComponent implements OnInit {
  loading = true;
  error = false;

  ngOnInit() {
    // Set loading state initially
    this.loading = true;
    this.error = false;
  }

  onIframeLoad() {
    this.loading = false;
    this.error = false;
  }

  onIframeError() {
    this.loading = false;
    this.error = true;
  }
}