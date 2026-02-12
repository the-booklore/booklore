import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
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

@Component({
  selector: 'app-reading-streaks-chart',
  standalone: true,
  imports: [CommonModule],
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
  public streaks: Streak[] = [];
  public milestones: Milestone[] = [];
  public hasData = false;
  public maxStreakLength = 1;

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

  getStreakColor(length: number): string {
    const ratio = length / this.maxStreakLength;
    if (ratio >= 0.8) return '#4caf50';
    if (ratio >= 0.5) return '#8bc34a';
    if (ratio >= 0.3) return '#ffc107';
    return '#ff9800';
  }

  getStreakWidth(length: number): number {
    return Math.max(4, (length / this.maxStreakLength) * 100);
  }

  formatDate(date: Date): string {
    return date.toLocaleDateString('en-US', {month: 'short', day: 'numeric'});
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

    // Get last 12 months of streaks
    const twelveMonthsAgo = new Date();
    twelveMonthsAgo.setMonth(twelveMonthsAgo.getMonth() - 12);
    this.streaks = allStreaks.filter(s => s.endDate >= twelveMonthsAgo);
    this.maxStreakLength = Math.max(1, ...allStreaks.map(s => s.length));

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
