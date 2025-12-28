import {Component, inject, Input, OnInit, OnChanges, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ReadingSessionApiService, ReadingSessionResponse} from '../../../../../shared/service/reading-session-api.service';
import {TableModule} from 'primeng/table';
import {ProgressSpinnerModule} from 'primeng/progressspinner';
import {TagModule} from 'primeng/tag';

@Component({
  selector: 'app-book-reading-sessions',
  standalone: true,
  imports: [CommonModule, TableModule, ProgressSpinnerModule, TagModule],
  templateUrl: './book-reading-sessions.component.html',
  styleUrls: ['./book-reading-sessions.component.scss']
})
export class BookReadingSessionsComponent implements OnInit, OnChanges {
  @Input() bookId!: number;

  private readonly readingSessionService = inject(ReadingSessionApiService);

  sessions: ReadingSessionResponse[] = [];
  totalRecords = 0;
  first = 0;
  rows = 5;
  loading = false;

  ngOnInit() {
    this.loadSessions();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['bookId'] && !changes['bookId'].firstChange) {
      this.first = 0;
      this.loadSessions();
    }
  }

  loadSessions(page: number = 0) {
    this.loading = true;
    this.readingSessionService.getSessionsByBookId(this.bookId, page, this.rows)
      .subscribe({
        next: (response) => {
          this.sessions = response.content;
          this.totalRecords = response.totalElements;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        }
      });
  }

  onPageChange(event: any) {
    this.first = event.first;
    const page = Math.floor(event.first / event.rows);
    this.loadSessions(page);
  }

  formatDuration(seconds: number): string {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    } else if (minutes > 0) {
      return `${minutes}m ${secs}s`;
    }
    return `${secs}s`;
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleString();
  }

  getProgressColor(delta: number): 'success' | 'secondary' | 'danger' {
    if (delta > 0) return 'success';
    if (delta < 0) return 'danger';
    return 'secondary';
  }

  isPageNumber(location: string | undefined): boolean {
    if (!location) return false;
    return !isNaN(Number(location)) && location.trim() !== '';
  }
}
