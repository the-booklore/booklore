import {Component, EventEmitter, HostListener, Input, Output} from '@angular/core';

@Component({
  selector: 'app-reader-header',
  standalone: true,
  templateUrl: './reader-header.component.html',
  styleUrls: ['./reader-header.component.scss']
})
export class ReaderHeaderComponent {
  @Input() currentChapterName: string | null = null;
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

  private _headerVisible = false;
  dropdownVisible = false;
  private isHeaderHovered = false;

  @HostListener('document:mousemove', ['$event'])
  onDocumentMouseMove(event: MouseEvent) {
    if (this.dropdownVisible) {
      this._headerVisible = true;
      return;
    }
    if (event.clientY <= 40) {
      this._headerVisible = true;
    } else if (!this.isHeaderHovered) {
      this._headerVisible = false;
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    const clickedInside = target.closest('.dropdown-container');
    if (!clickedInside && this.dropdownVisible) {
      this.dropdownVisible = false;
      if (event.clientY > 40 && !this.isHeaderHovered) {
        this._headerVisible = false;
      }
    }
  }

  @HostListener('window:blur')
  onWindowBlur() {
    setTimeout(() => {
      if (this.dropdownVisible) {
        this.dropdownVisible = false;
        if (!this.isHeaderHovered) {
          this._headerVisible = false;
        }
      }
    }, 200);
  }

  onHeaderMouseEnter() {
    this.isHeaderHovered = true;
    this._headerVisible = true;
  }

  onHeaderMouseLeave() {
    this.isHeaderHovered = false;
    if (!this.dropdownVisible) {
      this._headerVisible = false;
    }
  }

  toggleDropdown() {
    this.dropdownVisible = !this.dropdownVisible;
  }

  onSetFlow(flow: 'paginated' | 'scrolled') {
    if (this.flow !== flow) {
      this.setFlow.emit(flow);
    }
  }

  get headerVisible(): boolean {
    return this.forceVisible || this._headerVisible;
  }
}
