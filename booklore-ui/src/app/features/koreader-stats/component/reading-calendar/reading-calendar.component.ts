import {Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {BookReadingSpan, CalendarDayData, CalendarMonthData, KoreaderStatsService} from '../../service/koreader-stats.service';
import {catchError, of} from 'rxjs';
import {Button} from 'primeng/button';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {FormsModule} from '@angular/forms';

interface CalendarCell {
  date: Date | null;
  dayNumber: number | null;
  dayData: CalendarDayData | null;
  isCurrentMonth: boolean;
}

interface CalendarWeek {
  cells: CalendarCell[];
}

interface ProcessedSpan extends BookReadingSpan {
  startColumn: number;
  endColumn: number;
  colorIndex: number;
}

@Component({
  selector: 'app-reading-calendar',
  standalone: true,
  imports: [
    CommonModule,
    Button,
    ToggleSwitch,
    FormsModule
  ],
  templateUrl: './reading-calendar.component.html',
  styleUrls: ['./reading-calendar.component.scss']
})
export class ReadingCalendarComponent implements OnInit {
  private readonly koreaderStatsService = inject(KoreaderStatsService);
  private readonly router = inject(Router);

  public currentYear: number = new Date().getFullYear();
  public currentMonth: number = new Date().getMonth() + 1;
  public calendarData: CalendarMonthData | null = null;
  public isLoading = false;
  public weeks: CalendarWeek[] = [];
  public processedSpans: ProcessedSpan[] = [];
  public showDurationBars = false;
  public maxRowsInMonth = 0;
  public today: Date = new Date();

  // Month totals
  public monthTotalDuration = 0;
  public monthTotalPages = 0;

  private readonly WEEKDAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  private readonly MONTH_NAMES = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
  ];

  // Book color indices - we'll use CSS custom properties with these
  private bookColorIndex = 0;

  ngOnInit(): void {
    this.loadCalendarData();
  }

  get weekdayLabels(): string[] {
    return this.WEEKDAY_LABELS;
  }

  get monthName(): string {
    return this.MONTH_NAMES[this.currentMonth - 1];
  }

  previousMonth(): void {
    if (this.currentMonth === 1) {
      this.currentMonth = 12;
      this.currentYear--;
    } else {
      this.currentMonth--;
    }
    this.loadCalendarData();
  }

  nextMonth(): void {
    if (this.currentMonth === 12) {
      this.currentMonth = 1;
      this.currentYear++;
    } else {
      this.currentMonth++;
    }
    this.loadCalendarData();
  }

  goToToday(): void {
    const today = new Date();
    this.currentYear = today.getFullYear();
    this.currentMonth = today.getMonth() + 1;
    this.loadCalendarData();
  }

  isToday(cell: CalendarCell): boolean {
    if (!cell.date || !cell.isCurrentMonth) return false;
    return cell.date.getDate() === this.today.getDate() &&
           cell.date.getMonth() === this.today.getMonth() &&
           cell.date.getFullYear() === this.today.getFullYear();
  }

  navigateToBook(bookId: number): void {
    this.router.navigate(['/book', bookId]);
  }

  getBookColorClass(index: number): string {
    return `book-color-${index % 10}`;
  }

  private loadCalendarData(): void {
    this.isLoading = true;
    this.koreaderStatsService.getCalendarMonth(this.currentYear, this.currentMonth)
      .pipe(
        catchError(error => {
          console.error('Error loading calendar data:', error);
          this.isLoading = false;
          return of(null);
        })
      )
      .subscribe(data => {
        this.calendarData = data;
        this.buildCalendarGrid();
        this.processSpans();
        this.calculateMonthTotals();
        this.isLoading = false;
      });
  }

  private calculateMonthTotals(): void {
    if (!this.calendarData?.days) {
      this.monthTotalDuration = 0;
      this.monthTotalPages = 0;
      return;
    }
    this.monthTotalDuration = this.calendarData.days.reduce(
      (sum, day) => sum + day.totalDurationSeconds, 0
    );
    this.monthTotalPages = this.calendarData.days.reduce(
      (sum, day) => sum + day.totalPagesRead, 0
    );
  }

  private buildCalendarGrid(): void {
    this.weeks = [];

    const firstDay = new Date(this.currentYear, this.currentMonth - 1, 1);
    const lastDay = new Date(this.currentYear, this.currentMonth, 0);
    const daysInMonth = lastDay.getDate();

    // Get day of week (0 = Sunday, adjust for Monday start)
    let startDayOfWeek = firstDay.getDay();
    startDayOfWeek = startDayOfWeek === 0 ? 6 : startDayOfWeek - 1; // Convert to Monday = 0

    // Create a map for quick day data lookup
    const dayDataMap = new Map<number, CalendarDayData>();
    if (this.calendarData?.days) {
      this.calendarData.days.forEach(day => {
        const date = new Date(day.date);
        dayDataMap.set(date.getDate(), day);
      });
    }

    let currentWeek: CalendarCell[] = [];

    // Add empty cells for days before the first of the month
    for (let i = 0; i < startDayOfWeek; i++) {
      currentWeek.push({
        date: null,
        dayNumber: null,
        dayData: null,
        isCurrentMonth: false
      });
    }

    // Add each day of the month
    for (let day = 1; day <= daysInMonth; day++) {
      const date = new Date(this.currentYear, this.currentMonth - 1, day);
      currentWeek.push({
        date,
        dayNumber: day,
        dayData: dayDataMap.get(day) || null,
        isCurrentMonth: true
      });

      if (currentWeek.length === 7) {
        this.weeks.push({cells: currentWeek});
        currentWeek = [];
      }
    }

    // Add empty cells for remaining days
    if (currentWeek.length > 0) {
      while (currentWeek.length < 7) {
        currentWeek.push({
          date: null,
          dayNumber: null,
          dayData: null,
          isCurrentMonth: false
        });
      }
      this.weeks.push({cells: currentWeek});
    }
  }

  private processSpans(): void {
    this.processedSpans = [];
    this.maxRowsInMonth = 0;

    if (!this.calendarData?.bookSpans) return;

    const colorIndexMap = new Map<number, number>();
    let colorIndex = 0;

    this.calendarData.bookSpans.forEach(span => {
      // Assign a consistent color index to each book
      if (!colorIndexMap.has(span.bookId)) {
        colorIndexMap.set(span.bookId, colorIndex);
        colorIndex++;
      }

      const startDate = new Date(span.startDate);
      const endDate = new Date(span.endDate);

      // Calculate column positions (0-6 for each week day, starting Monday)
      const startDayOfMonth = startDate.getDate();
      const endDayOfMonth = endDate.getDate();

      // Calculate which column (day of week) the span starts and ends
      const startColumn = this.getDayColumn(startDayOfMonth);
      const endColumn = this.getDayColumn(endDayOfMonth);

      this.processedSpans.push({
        ...span,
        startColumn,
        endColumn,
        colorIndex: colorIndexMap.get(span.bookId) ?? 0
      });

      if (span.row + 1 > this.maxRowsInMonth) {
        this.maxRowsInMonth = span.row + 1;
      }
    });
  }

  private getDayColumn(dayOfMonth: number): number {
    const date = new Date(this.currentYear, this.currentMonth - 1, dayOfMonth);
    let dayOfWeek = date.getDay();
    return dayOfWeek === 0 ? 6 : dayOfWeek - 1; // Convert to Monday = 0
  }

  getSpansForWeek(weekIndex: number): ProcessedSpan[] {
    const weekStart = this.getWeekStartDay(weekIndex);
    const weekEnd = this.getWeekEndDay(weekIndex);

    return this.processedSpans.filter(span => {
      const startDay = new Date(span.startDate).getDate();
      const endDay = new Date(span.endDate).getDate();
      return startDay <= weekEnd && endDay >= weekStart;
    });
  }

  private getWeekStartDay(weekIndex: number): number {
    const firstDay = new Date(this.currentYear, this.currentMonth - 1, 1);
    let startDayOfWeek = firstDay.getDay();
    startDayOfWeek = startDayOfWeek === 0 ? 6 : startDayOfWeek - 1;

    const firstDayOfWeek = 1 - startDayOfWeek + (weekIndex * 7);
    return Math.max(1, firstDayOfWeek);
  }

  private getWeekEndDay(weekIndex: number): number {
    const lastDay = new Date(this.currentYear, this.currentMonth, 0).getDate();
    const firstDay = new Date(this.currentYear, this.currentMonth - 1, 1);
    let startDayOfWeek = firstDay.getDay();
    startDayOfWeek = startDayOfWeek === 0 ? 6 : startDayOfWeek - 1;

    const lastDayOfWeek = 7 - startDayOfWeek + (weekIndex * 7);
    return Math.min(lastDay, lastDayOfWeek);
  }

  getSpanStyle(span: ProcessedSpan, weekIndex: number): { [key: string]: string } {
    const weekStart = this.getWeekStartDay(weekIndex);
    const weekEnd = this.getWeekEndDay(weekIndex);

    const spanStartDay = new Date(span.startDate).getDate();
    const spanEndDay = new Date(span.endDate).getDate();

    // Calculate where span starts and ends within this week
    const effectiveStart = Math.max(spanStartDay, weekStart);
    const effectiveEnd = Math.min(spanEndDay, weekEnd);

    // Get column positions
    const startCol = this.getDayColumn(effectiveStart);
    const endCol = this.getDayColumn(effectiveEnd);

    // Calculate percentage positions
    const leftPercent = (startCol / 7) * 100;
    const widthPercent = ((endCol - startCol + 1) / 7) * 100;

    return {
      'left': `${leftPercent}%`,
      'width': `${widthPercent}%`,
      'top': `${span.row * 26}px`
    };
  }

  formatDuration(seconds: number): string {
    if (!seconds) return '0m';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (hours === 0) return `${minutes}m`;
    return `${hours}h ${minutes}m`;
  }
}
