import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Tooltip} from 'primeng/tooltip';
import {EMPTY, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {ReadingSessionHeatmapResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';

interface Streak {
  startDate: Date;
  endDate: Date;
  length: number;
}

interface Milestone {
  label: string;
  icon: string;
  requirement: number;
  type: 'streak' | 'total';
  unlocked: boolean;
}

interface CalendarDay {
  date: string;
  count: number;
}

@Component({
  selector: 'app-reading-streaks-chart',
  standalone: true,
  imports: [CommonModule, Tooltip],
  templateUrl: './reading-streaks-chart.component.html',
  styleUrls: ['./reading-streaks-chart.component.scss']
})
export class ReadingStreaksChartComponent implements OnInit, OnDestroy {
  private readonly userStatsService = inject(UserStatsService);
  private readonly destroy$ = new Subject<void>();

  public currentStreak = 0;
  public longestStreak = 0;
  public totalReadingDays = 0;
  public consistencyPercent = 0;
  public milestones: Milestone[] = [];
  public hasData = false;
  public calendarDays: (CalendarDay | null)[] = [];
  public monthLabels: { label: string; col: number }[] = [];
  public numWeeks = 0;

  ngOnInit(): void {
    this.userStatsService.getReadingDates()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading reading dates:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => this.processData(data));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  getCellClass(day: CalendarDay): string {
    if (day.count === 0) return 'level-0';
    if (day.count <= 1) return 'level-1';
    if (day.count <= 3) return 'level-2';
    return 'level-3';
  }

  private processData(data: ReadingSessionHeatmapResponse[]): void {
    if (!data || data.length === 0) {
      this.hasData = false;
      return;
    }

    this.hasData = true;
    this.totalReadingDays = data.length;

    const dates = new Set(data.map(d => d.date));
    const sortedDates = Array.from(dates).sort();
    const dateSet = new Set(sortedDates);

    // Calculate all streaks
    const allStreaks: Streak[] = [];
    let streakStart: string | null = null;
    let prevDate: string | null = null;

    for (const dateStr of sortedDates) {
      if (!prevDate || !this.isConsecutiveDay(prevDate, dateStr)) {
        if (prevDate && streakStart) {
          allStreaks.push({
            startDate: new Date(streakStart),
            endDate: new Date(prevDate),
            length: this.daysBetween(streakStart, prevDate) + 1
          });
        }
        streakStart = dateStr;
      }
      prevDate = dateStr;
    }
    if (prevDate && streakStart) {
      allStreaks.push({
        startDate: new Date(streakStart),
        endDate: new Date(prevDate),
        length: this.daysBetween(streakStart, prevDate) + 1
      });
    }

    // Build calendar heatmap
    const dateCountMap = new Map<string, number>();
    data.forEach(d => dateCountMap.set(d.date, d.count));
    this.buildCalendar(dateCountMap);

    // Calculate longest streak
    this.longestStreak = allStreaks.length > 0 ? Math.max(...allStreaks.map(s => s.length)) : 0;

    // Calculate current streak
    const today = new Date().toISOString().split('T')[0];
    const yesterday = new Date(Date.now() - 86400000).toISOString().split('T')[0];

    if (dateSet.has(today)) {
      const lastStreak = allStreaks[allStreaks.length - 1];
      if (lastStreak && lastStreak.endDate.toISOString().split('T')[0] === today) {
        this.currentStreak = lastStreak.length;
      }
    } else if (dateSet.has(yesterday)) {
      const lastStreak = allStreaks[allStreaks.length - 1];
      if (lastStreak && lastStreak.endDate.toISOString().split('T')[0] === yesterday) {
        this.currentStreak = lastStreak.length;
      }
    } else {
      this.currentStreak = 0;
    }

    // Calculate consistency
    if (sortedDates.length >= 2) {
      const firstDate = sortedDates[0];
      const totalPossibleDays = this.daysBetween(firstDate, today) + 1;
      this.consistencyPercent = totalPossibleDays > 0
        ? Math.round((this.totalReadingDays / totalPossibleDays) * 100)
        : 0;
    }

    // Build milestones
    this.milestones = [
      {label: '7-Day Streak', icon: '🔥', requirement: 7, type: 'streak', unlocked: this.longestStreak >= 7},
      {label: '30-Day Streak', icon: '⚡', requirement: 30, type: 'streak', unlocked: this.longestStreak >= 30},
      {label: '100 Reading Days', icon: '📚', requirement: 100, type: 'total', unlocked: this.totalReadingDays >= 100},
      {label: '365 Reading Days', icon: '🏆', requirement: 365, type: 'total', unlocked: this.totalReadingDays >= 365},
      {label: 'Year of Reading', icon: '👑', requirement: 365, type: 'streak', unlocked: this.longestStreak >= 365},
    ];
  }

  private buildCalendar(dateCountMap: Map<string, number>): void {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const start = new Date(today);
    start.setDate(start.getDate() - 52 * 7);
    const startDow = start.getDay();
    start.setDate(start.getDate() + (startDow === 0 ? -6 : 1 - startDow));

    const cells: (CalendarDay | null)[] = [];
    const months: { label: string; col: number }[] = [];
    let prevMonth = -1;

    const current = new Date(start);
    let weekIndex = 0;

    while (current <= today) {
      const dow = (current.getDay() + 6) % 7; // Mon=0, Sun=6
      if (dow === 0 && cells.length > 0) weekIndex++;

      if (current.getMonth() !== prevMonth) {
        months.push({
          label: current.toLocaleDateString('en-US', {month: 'short'}),
          col: weekIndex + 1
        });
        prevMonth = current.getMonth();
      }

      const dateStr = this.toDateStr(current);
      cells.push({date: dateStr, count: dateCountMap.get(dateStr) || 0});
      current.setDate(current.getDate() + 1);
    }

    // Pad last week with nulls
    const lastDow = (today.getDay() + 6) % 7;
    for (let i = lastDow + 1; i <= 6; i++) {
      cells.push(null);
    }

    this.numWeeks = weekIndex + 1;
    this.calendarDays = cells;
    this.monthLabels = months;
  }

  private toDateStr(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private isConsecutiveDay(dateStr1: string, dateStr2: string): boolean {
    const d1 = new Date(dateStr1);
    const d2 = new Date(dateStr2);
    const diffMs = d2.getTime() - d1.getTime();
    return Math.abs(diffMs - 86400000) < 3600000; // within 1 hour of 24h
  }

  private daysBetween(dateStr1: string, dateStr2: string): number {
    const d1 = new Date(dateStr1);
    const d2 = new Date(dateStr2);
    return Math.round((d2.getTime() - d1.getTime()) / 86400000);
  }
}
