import {Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';

export type ReaderIconName =
  | 'menu'
  | 'close'
  | 'chevron-left'
  | 'chevron-right'
  | 'chevron-down'
  | 'chevron-first'
  | 'chevron-last'
  | 'dots-vertical'
  | 'bookmark'
  | 'settings'
  | 'book'
  | 'edit'
  | 'copy'
  | 'trash'
  | 'search'
  | 'note'
  | 'plus'
  | 'minus';

interface IconPath {
  d: string;
  type?: 'path' | 'polyline' | 'line' | 'rect' | 'circle';
}

const ICONS: Record<ReaderIconName, IconPath[]> = {
  // Navigation icons
  'menu': [
    {d: 'M3,6 L21,6', type: 'line'},
    {d: 'M3,12 L21,12', type: 'line'},
    {d: 'M3,18 L21,18', type: 'line'}
  ],
  'close': [
    {d: 'M18,6 L6,18', type: 'line'},
    {d: 'M6,6 L18,18', type: 'line'}
  ],
  'chevron-left': [
    {d: '15,18 9,12 15,6', type: 'polyline'}
  ],
  'chevron-right': [
    {d: '9,18 15,12 9,6', type: 'polyline'}
  ],
  'chevron-down': [
    {d: '6,9 12,15 18,9', type: 'polyline'}
  ],
  'chevron-first': [
    {d: '11,17 6,12 11,7', type: 'polyline'},
    {d: '18,17 13,12 18,7', type: 'polyline'}
  ],
  'chevron-last': [
    {d: '13,17 18,12 13,7', type: 'polyline'},
    {d: '6,17 11,12 6,7', type: 'polyline'}
  ],
  'dots-vertical': [
    {d: 'M12,12 m-1,0 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0', type: 'path'},
    {d: 'M12,5 m-1,0 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0', type: 'path'},
    {d: 'M12,19 m-1,0 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0', type: 'path'}
  ],

  // Action icons
  'bookmark': [
    {d: 'M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z'}
  ],
  'settings': [
    {d: 'M12 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0-6 0', type: 'path'},
    {d: 'M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z'}
  ],
  'book': [
    {d: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20'},
    {d: 'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'}
  ],
  'edit': [
    {d: 'M12 20h9'},
    {d: 'M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z'}
  ],
  'copy': [
    {d: 'M9,9 L22,9 L22,22 L9,22 Z M9,9 Q9,9 9,9', type: 'path'},
    {d: 'M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1'}
  ],
  'trash': [
    {d: 'M3 6h18'},
    {d: 'M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6'},
    {d: 'M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2'}
  ],
  'search': [
    {d: 'M11 11m-8 0a8 8 0 1 0 16 0a8 8 0 1 0-16 0', type: 'path'},
    {d: 'M21 21l-4.35-4.35'}
  ],
  'note': [
    {d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'},
    {d: '14,2 14,8 20,8', type: 'polyline'},
    {d: 'M16 13H8'},
    {d: 'M16 17H8'},
    {d: 'M10 9H8'}
  ],
  'plus': [
    {d: 'M12,5 L12,19', type: 'line'},
    {d: 'M5,12 L19,12', type: 'line'}
  ],
  'minus': [
    {d: 'M5,12 L19,12', type: 'line'}
  ]
};

@Component({
  selector: 'app-reader-icon',
  standalone: true,
  imports: [CommonModule],
  template: `
    <svg
      [attr.width]="size"
      [attr.height]="size"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="strokeWidth"
      stroke-linecap="round"
      stroke-linejoin="round"
      [class]="className">
      @for (path of paths; track $index) {
        @switch (path.type) {
          @case ('line') {
            <line
              [attr.x1]="getLineCoords(path.d, 0)"
              [attr.y1]="getLineCoords(path.d, 1)"
              [attr.x2]="getLineCoords(path.d, 2)"
              [attr.y2]="getLineCoords(path.d, 3)"/>
          }
          @case ('polyline') {
            <polyline [attr.points]="path.d"/>
          }
          @default {
            <path [attr.d]="path.d"/>
          }
        }
      }
    </svg>
  `,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    svg {
      display: block;
    }
  `]
})
export class ReaderIconComponent {
  @Input() name!: ReaderIconName;
  @Input() size: number | string = 18;
  @Input() strokeWidth: number | string = 2;
  @Input() className = '';

  get paths(): IconPath[] {
    return ICONS[this.name] || [];
  }

  getLineCoords(d: string, index: number): string {
    const coords = d.replace(/[ML]/g, ' ').trim().split(/[\s,]+/);
    return coords[index] || '0';
  }
}
