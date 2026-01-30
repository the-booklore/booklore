import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {ReaderHeaderService} from './header.service';
import {ReaderIconComponent} from '../../shared/icon.component';
import {Router} from '@angular/router';

@Component({
  selector: 'app-reader-header',
  standalone: true,
  imports: [ReaderIconComponent],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class ReaderHeaderComponent implements OnInit, OnDestroy {
  private headerService = inject(ReaderHeaderService);
  private router = inject(Router);
  private destroy$ = new Subject<void>();

  isVisible = false;
  isCurrentCfiBookmarked = false;

  get bookTitle(): string {
    return this.headerService.title;
  }

  get currentTheme() {
    return this.headerService.currentState.theme;
  }

  ngOnInit(): void {
    this.headerService.forceVisible$
      .pipe(takeUntil(this.destroy$))
      .subscribe(visible => this.isVisible = visible);

    this.headerService.isCurrentCfiBookmarked$
      .pipe(takeUntil(this.destroy$))
      .subscribe(bookmarked => this.isCurrentCfiBookmarked = bookmarked);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onShowChapters(): void {
    this.headerService.openSidebar();
  }

  onOpenNotes(): void {
    this.headerService.openLeftSidebar('notes');
  }

  onOpenSearch(): void {
    this.headerService.openLeftSidebar('search');
  }

  onCreateBookmark(): void {
    this.headerService.createBookmark();
  }

  onShowControls(): void {
    this.headerService.openControls();
  }

  onClose(): void {
    if (window.history.length <= 2) {
      this.router.navigate(['/dashboard']);
    } else {
      this.headerService.close();
    }
  }
}
