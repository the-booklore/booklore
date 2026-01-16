import {Component, EventEmitter, Input, Output} from '@angular/core';

@Component({
  selector: 'app-reader-header',
  standalone: true,
  templateUrl: './reader-header.component.html',
  styleUrls: ['./reader-header.component.scss']
})
export class ReaderHeaderComponent {
  @Input() bookTitle: string | null = null;
  @Input() currentTheme: any = null;
  @Input() isDark = false;
  @Input() fontSize = 16;
  @Input() lineHeight = 1.5;
  @Input() forceVisible = false;
  @Input() flow: 'paginated' | 'scrolled' = 'paginated';
  @Output() showChapters = new EventEmitter<void>();
  @Output() showControls = new EventEmitter<void>();
  @Output() showMetadata = new EventEmitter<void>();
  @Output() createBookmark = new EventEmitter<void>();
  @Output() close = new EventEmitter<void>();
  @Output() toggleDarkMode = new EventEmitter<void>();
  @Output() increaseFontSize = new EventEmitter<void>();
  @Output() decreaseFontSize = new EventEmitter<void>();
  @Output() increaseLineHeight = new EventEmitter<void>();
  @Output() decreaseLineHeight = new EventEmitter<void>();
  @Output() setFlow = new EventEmitter<'paginated' | 'scrolled'>();

  get headerVisible(): boolean {
    return this.forceVisible;
  }
}
