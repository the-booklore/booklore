import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {ReadingStatsSummary} from '../component/koreader-stats.component';

export interface DailyReadingStats {
  date: string;
  durationSeconds: number;
  pagesRead: number;
}

export interface BookDayReading {
  bookId: number;
  title: string;
  durationSeconds: number;
  pagesRead: number;
}

export interface CalendarDayData {
  date: string;
  totalDurationSeconds: number;
  totalPagesRead: number;
  books: BookDayReading[];
}

export interface BookReadingSpan {
  bookId: number;
  title: string;
  startDate: string;
  endDate: string;
  row: number;
  totalDurationSeconds: number;
  totalPagesRead: number;
}

export interface CalendarMonthData {
  year: number;
  month: number;
  days: CalendarDayData[];
  bookSpans: BookReadingSpan[];
}

export interface DayOfWeekStats {
  dayName: string;
  dayIndex: number;
  totalDurationSeconds: number;
  totalPagesRead: number;
  sessionCount: number;
}

@Injectable({
  providedIn: 'root'
})
export class KoreaderStatsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.API_CONFIG.BASE_URL;

  getReadingStatsSummary(): Observable<ReadingStatsSummary> {
    return this.http.get<ReadingStatsSummary>(`${this.apiUrl}/api/v1/koreader/statistics/summary`);
  }

  getDailyReadingStats(): Observable<DailyReadingStats[]> {
    return this.http.get<DailyReadingStats[]>(`${this.apiUrl}/api/v1/koreader/statistics/daily`);
  }

  getCalendarMonth(year: number, month: number): Observable<CalendarMonthData> {
    return this.http.get<CalendarMonthData>(`${this.apiUrl}/api/v1/koreader/statistics/calendar/${year}/${month}`);
  }

  getDayOfWeekStats(): Observable<DayOfWeekStats[]> {
    return this.http.get<DayOfWeekStats[]>(`${this.apiUrl}/api/v1/koreader/statistics/day-of-week`);
  }
}
