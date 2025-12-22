import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';
import {BookType} from '../../book/model/book.model';

export interface ReadingSessionHeatmapResponse {
  date: string;
  count: number;
}

export interface ReadingSessionTimelineResponse {
  bookId: number;
  bookTitle: string;
  startDate: string;
  bookType: BookType
  endDate: string;
  totalSessions: number;
  totalDurationSeconds: number;
}

@Injectable({
  providedIn: 'root'
})
export class UserStatsService {
  private readonly readingSessionsUrl = `${API_CONFIG.BASE_URL}/api/v1/reading-sessions`;
  private http = inject(HttpClient);

  getHeatmapForYear(year: number): Observable<ReadingSessionHeatmapResponse[]> {
    return this.http.get<ReadingSessionHeatmapResponse[]>(
      `${this.readingSessionsUrl}/heatmap/year/${year}`
    );
  }

  getTimelineForWeek(year: number, week: number): Observable<ReadingSessionTimelineResponse[]> {
    return this.http.get<ReadingSessionTimelineResponse[]>(
      `${this.readingSessionsUrl}/timeline/week/${year}/${week}`
    );
  }
}
