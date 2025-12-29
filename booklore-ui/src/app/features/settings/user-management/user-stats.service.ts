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

export interface GenreStatsResponse {
  genre: string;
  bookCount: number;
  totalSessions: number;
  totalDurationSeconds: number;
  averageSessionsPerBook: number;
}

export interface CompletionTimelineResponse {
  year: number;
  month: number;
  totalBooks: number;
  statusBreakdown: { [key: string]: number };
  finishedBooks: number;
  completionRate: number;
}

export interface FavoriteDaysResponse {
  dayOfWeek: number;
  dayName: string;
  sessionCount: number;
  totalDurationSeconds: number;
}

export interface PeakHoursResponse {
  hourOfDay: number;
  sessionCount: number;
  totalDurationSeconds: number;
}

@Injectable({
  providedIn: 'root'
})
export class UserStatsService {
  private readonly readingSessionsUrl = `${API_CONFIG.BASE_URL}/api/v1/user-stats`;
  private http = inject(HttpClient);

  getHeatmapForYear(year: number): Observable<ReadingSessionHeatmapResponse[]> {
    return this.http.get<ReadingSessionHeatmapResponse[]>(
      `${this.readingSessionsUrl}/heatmap`,
      { params: { year: year.toString() } }
    );
  }

  getTimelineForWeek(year: number, month: number, week: number): Observable<ReadingSessionTimelineResponse[]> {
    return this.http.get<ReadingSessionTimelineResponse[]>(
      `${this.readingSessionsUrl}/timeline`,
      { params: { year: year.toString(), month: month.toString(), week: week.toString() } }
    );
  }

  getGenreStats(): Observable<GenreStatsResponse[]> {
    return this.http.get<GenreStatsResponse[]>(
      `${this.readingSessionsUrl}/genres`
    );
  }

  getCompletionTimelineForYear(year: number): Observable<CompletionTimelineResponse[]> {
    return this.http.get<CompletionTimelineResponse[]>(
      `${this.readingSessionsUrl}/completion-timeline`,
      { params: { year: year.toString() } }
    );
  }

  getFavoriteDays(year?: number, month?: number): Observable<FavoriteDaysResponse[]> {
    let params: any = {};
    if (year !== undefined && year !== null) {
      params.year = year.toString();
    }
    if (month !== undefined && month !== null) {
      params.month = month.toString();
    }

    return this.http.get<FavoriteDaysResponse[]>(
      `${this.readingSessionsUrl}/favorite-days`,
      {params}
    );
  }

  getPeakHours(year?: number, month?: number): Observable<PeakHoursResponse[]> {
    let params: any = {};
    if (year !== undefined && year !== null) {
      params.year = year.toString();
    }
    if (month !== undefined && month !== null) {
      params.month = month.toString();
    }

    return this.http.get<PeakHoursResponse[]>(
      `${this.readingSessionsUrl}/peak-hours`,
      {params}
    );
  }
}
