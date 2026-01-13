import {Component, EventEmitter, HostListener, Input, Output} from '@angular/core';

@Component({
  selector: 'app-reader-header',
  standalone: true,
  templateUrl: './reader-header.component.html',
  styleUrls: ['./reader-header.component.scss']
})
export class ReaderHeaderComponent {
  @Input() currentChapterName: string | null = null;
  @Input() currentTheme: any;
  @Input() isDark: boolean = false;
  @Input() fontSize: number = 100;
  @Input() lineHeight: number = 1.4;
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

  headerVisible = false;
  dropdownVisible = false;
  private isHeaderHovered = false;

  @HostListener('document:mousemove', ['$event'])
  onDocumentMouseMove(event: MouseEvent) {
    if (this.dropdownVisible) {
      this.headerVisible = true;
      return;
    }
    if (event.clientY <= 40) {
      this.headerVisible = true;
    } else if (!this.isHeaderHovered) {
      this.headerVisible = false;
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    const clickedInside = target.closest('.dropdown-container');
    if (!clickedInside && this.dropdownVisible) {
      this.dropdownVisible = false;
      if (event.clientY > 40 && !this.isHeaderHovered) {
        this.headerVisible = false;
      }
    }
  }

  @HostListener('window:blur')
  onWindowBlur() {
    setTimeout(() => {
      if (this.dropdownVisible) {
        this.dropdownVisible = false;
        if (!this.isHeaderHovered) {
          this.headerVisible = false;
        }
      }
    }, 200);
  }

  onHeaderMouseEnter() {
    this.isHeaderHovered = true;
    this.headerVisible = true;
  }

  onHeaderMouseLeave() {
    this.isHeaderHovered = false;
    if (!this.dropdownVisible) {
      this.headerVisible = false;
    }
  }

  toggleDropdown() {
    this.dropdownVisible = !this.dropdownVisible;
  }
}
