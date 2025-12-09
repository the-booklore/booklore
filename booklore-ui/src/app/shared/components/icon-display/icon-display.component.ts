import {Component, inject, Input, OnInit, OnChanges, SimpleChanges} from '@angular/core';
import {IconSelection} from '../../service/icon-picker.service';
import {NgClass, NgStyle} from '@angular/common';
import {IconCacheService} from '../../services/icon-cache.service';
import {IconService} from '../../services/icon.service';
import {DomSanitizer, SafeHtml} from '@angular/platform-browser';

@Component({
  selector: 'app-icon-display',
  standalone: true,
  imports: [NgClass, NgStyle],
  template: `
    @if (icon) {
      @if (icon.type === 'PRIME_NG') {
        <i [class]="getPrimeNgIconClass(icon.value)" [ngClass]="iconClass" [ngStyle]="iconStyle"></i>
      } @else {
        <div
          class="svg-icon-inline"
          [innerHTML]="getSvgContent(icon.value)"
          [ngClass]="iconClass"
          [ngStyle]="getSvgStyle()"
        ></div>
      }
    }
  `,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    .svg-icon-inline {
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    .svg-icon-inline :deep(svg) {
      width: 100%;
      height: 100%;
      display: block;
    }
  `]
})
export class IconDisplayComponent implements OnInit, OnChanges {
  @Input() icon: IconSelection | null = null;
  @Input() iconClass: string = 'icon';
  @Input() iconStyle: Record<string, string> = {};
  @Input() size: string = '16px';
  @Input() alt: string = 'Icon';

  private iconCache = inject(IconCacheService);
  private iconService = inject(IconService);
  private sanitizer = inject(DomSanitizer);

  ngOnInit(): void {
    this.loadIconIfNeeded();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['icon'] && this.icon?.type === 'CUSTOM_SVG') {
      this.loadIconIfNeeded();
    }
  }

  private loadIconIfNeeded(): void {
    if (this.icon?.type === 'CUSTOM_SVG') {
      this.iconService.getSanitizedSvgContent(this.icon.value).subscribe({
        error: () => {
          if (this.icon?.type === 'CUSTOM_SVG') {
            const errorSvg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="red"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>';
            const sanitized = this.sanitizer.bypassSecurityTrustHtml(errorSvg);
            this.iconCache.cacheIcon(this.icon.value, errorSvg, sanitized);
          }
        }
      });
    }
  }

  getSvgContent(iconName: string): SafeHtml | null {
    return this.iconCache.getCachedSanitized(iconName) || null;
  }

  getPrimeNgIconClass(iconValue: string): string {
    if (iconValue.startsWith('pi pi-')) {
      return iconValue;
    }
    if (iconValue.startsWith('pi-')) {
      return `pi ${iconValue}`;
    }
    return `pi pi-${iconValue}`;
  }

  getSvgStyle(): Record<string, string> {
    return {
      width: this.size,
      height: this.size,
      ...this.iconStyle
    };
  }
}
