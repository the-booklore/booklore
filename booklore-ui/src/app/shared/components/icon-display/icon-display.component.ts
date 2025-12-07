import {Component, inject, Input} from '@angular/core';
import {IconSelection} from '../../service/icon-picker.service';
import {UrlHelperService} from '../../service/url-helper.service';
import {NgClass, NgStyle} from '@angular/common';

@Component({
  selector: 'app-icon-display',
  standalone: true,
  imports: [NgClass, NgStyle],
  template: `
    @if (icon) {
      @if (icon.type === 'PRIME_NG') {
        <i [class]="getPrimeNgIconClass(icon.value)" [ngClass]="iconClass" [ngStyle]="iconStyle"></i>
      } @else {
        <img
          [src]="getIconUrl(icon.value)"
          [alt]="alt"
          [ngClass]="iconClass"
          [ngStyle]="getImageStyle()"
        />
      }
    }
  `,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
  `]
})
export class IconDisplayComponent {
  @Input() icon: IconSelection | null = null;
  @Input() iconClass: string = 'icon';
  @Input() iconStyle: Record<string, string> = {};
  @Input() size: string = '24px';
  @Input() alt: string = 'Icon';

  private urlHelper = inject(UrlHelperService);

  getPrimeNgIconClass(iconValue: string): string {
    if (iconValue.startsWith('pi pi-')) {
      return iconValue;
    }
    if (iconValue.startsWith('pi-')) {
      return `pi ${iconValue}`;
    }
    return `pi pi-${iconValue}`;
  }

  getIconUrl(iconName: string): string {
    return this.urlHelper.getIconUrl(iconName);
  }

  getImageStyle(): Record<string, string> {
    return {
      width: this.size,
      height: this.size,
      objectFit: 'contain',
      ...this.iconStyle
    };
  }
}
