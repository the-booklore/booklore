import {Component, EventEmitter, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ReaderIconComponent} from '../../ebook-reader/shared/icon.component';

interface ShortcutItem {
  keys: string[];
  description: string;
  mobileGesture?: string;
}

interface ShortcutGroup {
  title: string;
  shortcuts: ShortcutItem[];
}

@Component({
  selector: 'app-cbx-shortcuts-help',
  standalone: true,
  imports: [CommonModule, ReaderIconComponent],
  templateUrl: './cbx-shortcuts-help.component.html',
  styleUrls: ['./cbx-shortcuts-help.component.scss']
})
export class CbxShortcutsHelpComponent {
  @Output() close = new EventEmitter<void>();

  shortcutGroups: ShortcutGroup[] = [
    {
      title: 'Navigation',
      shortcuts: [
        {keys: ['←', '→'], description: 'Previous / Next page', mobileGesture: 'Swipe left/right'},
        {keys: ['Space'], description: 'Next page'},
        {keys: ['Shift', 'Space'], description: 'Previous page'},
        {keys: ['Home'], description: 'First page'},
        {keys: ['End'], description: 'Last page'},
        {keys: ['Page Up'], description: 'Previous page'},
        {keys: ['Page Down'], description: 'Next page'}
      ]
    },
    {
      title: 'Display',
      shortcuts: [
        {keys: ['F'], description: 'Toggle fullscreen'},
        {keys: ['D'], description: 'Toggle reading direction (LTR/RTL)'},
        {keys: ['Escape'], description: 'Exit fullscreen / Close dialogs'},
        {keys: ['Double-click'], description: 'Toggle zoom (fit page / actual size)', mobileGesture: 'Double-tap'}
      ]
    },
    {
      title: 'Playback',
      shortcuts: [
        {keys: ['P'], description: 'Toggle slideshow / auto-play'}
      ]
    },
    {
      title: 'Other',
      shortcuts: [
        {keys: ['?'], description: 'Show this help dialog'}
      ]
    }
  ];

  isMobile = window.innerWidth < 768;

  onClose(): void {
    this.close.emit();
  }

  onOverlayClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('dialog-overlay')) {
      this.onClose();
    }
  }
}
