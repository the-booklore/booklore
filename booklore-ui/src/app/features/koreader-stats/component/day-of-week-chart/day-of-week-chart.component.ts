import {Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {DayOfWeekStats, KoreaderStatsService} from '../../service/koreader-stats.service';
import {catchError, of} from 'rxjs';

@Component({
  selector: 'app-day-of-week-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './day-of-week-chart.component.html',
  styleUrls: ['./day-of-week-chart.component.scss']
})
export class DayOfWeekChartComponent implements OnInit {
  private readonly koreaderStatsService = inject(KoreaderStatsService);

  public isLoading = true;
  public stats: DayOfWeekStats[] = [];
  public maxDuration = 0;
  public selectedDay: DayOfWeekStats | null = null;

  ngOnInit(): void {
    this.loadData();
  }

  private loadData(): void {
    this.koreaderStatsService.getDayOfWeekStats()
      .pipe(
        catchError(error => {
          console.error('Error loading day of week stats:', error);
          return of([]);
        })
      )
      .subscribe(data => {
        this.stats = data;
        this.maxDuration = Math.max(...data.map(d => d.totalDurationSeconds), 1);
        this.isLoading = false;
      });
  }

  getBarHeight(durationSeconds: number): number {
    return (durationSeconds / this.maxDuration) * 100;
  }

  formatDuration(seconds: number): string {
    if (!seconds) return '0m';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (hours === 0) return `${minutes}m`;
    return `${hours}h ${minutes}m`;
  }

  getShortDayName(dayName: string): string {
    return dayName.substring(0, 3);
  }

  selectDay(day: DayOfWeekStats): void {
    this.selectedDay = this.selectedDay?.dayIndex === day.dayIndex ? null : day;
  }

  isSelected(day: DayOfWeekStats): boolean {
    return this.selectedDay?.dayIndex === day.dayIndex;
  }
}
