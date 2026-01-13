import {Component, EventEmitter, HostListener, inject, Input, Output} from '@angular/core';
import {ReaderViewManagerService} from '../../services/reader-view-manager.service';

interface TocItem {
  label: string;
  href: string;
  subitems: any;
  id: number;
}

interface PageItem {
  label: string;
  href: string;
  subitems: any;
  id: number;
}

interface RelocateEventDetail {
  fraction: number;
  section: { current: number; total: number };
  location: { current: number; next: number; total: number };
  time: { section: number; total: number };
  tocItem: TocItem;
  pageItem: PageItem;
  cfi: string;
  range: any;
}

@Component({
  selector: 'app-reader-navbar',
  standalone: true,
  templateUrl: './reader-navbar.component.html',
  styleUrls: ['./reader-navbar.component.scss']
})
export class ReaderNavbarComponent {
  navbarVisible = false;
  showLocationPopover = false;
  private isNavbarHovered = false;

  @Input() progressData: RelocateEventDetail | null = null;
  @Output() progressChange = new EventEmitter<number>();

  private managerService = inject(ReaderViewManagerService);

  @HostListener('document:mousemove', ['$event'])
  onDocumentMouseMove(event: MouseEvent) {
    const windowHeight = window.innerHeight;
    if (this.showLocationPopover) {
      this.navbarVisible = true;
      return;
    }
    if (event.clientY >= windowHeight - 60) {
      this.navbarVisible = true;
    } else if (!this.isNavbarHovered) {
      this.navbarVisible = false;
      this.showLocationPopover = false;
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    const clickedInside = target.closest('.location-popover') || target.closest('.location-btn');
    if (!clickedInside && this.showLocationPopover) {
      this.showLocationPopover = false;
      const windowHeight = window.innerHeight;
      if (event.clientY < windowHeight - 60 && !this.isNavbarHovered) {
        this.navbarVisible = false;
      }
    }
  }

  @HostListener('window:blur')
  onWindowBlur() {
    setTimeout(() => {
      if (this.showLocationPopover) {
        this.showLocationPopover = false;
        if (!this.isNavbarHovered) {
          this.navbarVisible = false;
        }
      }
    }, 200);
  }

  onNavbarMouseEnter() {
    this.isNavbarHovered = true;
    this.navbarVisible = true;
  }

  onNavbarMouseLeave() {
    this.isNavbarHovered = false;
    if (!this.showLocationPopover) {
      this.navbarVisible = false;
      this.showLocationPopover = false;
    }
  }

  toggleLocationPopover() {
    this.showLocationPopover = !this.showLocationPopover;
  }

  get currentFraction(): number {
    return this.progressData?.fraction ?? 0;
  }

  get currentPercentage(): number {
    return Math.round(this.currentFraction * 100);
  }

  get timeTotal(): string {
    return this.formatDuration((this.progressData?.time.total ?? 0) * 60);
  }

  get timeSection(): string {
    return this.formatDuration((this.progressData?.time.section ?? 0) * 60);
  }

  get locationCurrent(): number {
    return this.progressData?.location.current ?? 0;
  }

  get locationTotal(): number {
    return this.progressData?.location.total ?? 0;
  }

  get sectionCurrent(): number {
    return this.progressData?.section.current ?? 0;
  }

  get sectionTotal(): number {
    return this.progressData?.section.total ?? 0;
  }

  get currentPage(): string {
    return this.progressData?.pageItem?.label ?? 'N/A';
  }

  onProgressChange(event: Event) {
    const target = event.target as HTMLInputElement;
    const fraction = parseFloat(target.value) / 100;
    this.progressChange.emit(fraction);
  }

  onFirstSection() {
    this.managerService.goToSection(0).subscribe();
  }

  onPreviousSection(): void {
    const s = this.progressData?.section;
    if (!s || s.current <= 0) return;
    this.managerService.goToSection(s.current - 1).subscribe();
  }

  onNextSection(): void {
    const s = this.progressData?.section;
    if (!s || s.current >= s.total - 1) return;
    this.managerService.goToSection(s.current + 1).subscribe();
  }

  onLastSection(): void {
    const s = this.progressData?.section;
    if (!s || s.total <= 0) return;
    this.managerService.goToSection(s.total - 1).subscribe();
  }

  private formatDuration(seconds: number): string {
    if (seconds < 60) return `${Math.round(seconds)} sec`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes} min`;
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    return remainingMinutes > 0
      ? `${hours} hr ${remainingMinutes} min`
      : `${hours} hr`;
  }
}
