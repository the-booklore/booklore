import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Subject} from 'rxjs';
import {ReadingSessionHeatmapComponent} from '../reading-session-heatmap/reading-session-heatmap.component';
import {ReadingSessionTimelineComponent} from '../reading-session-timeline/reading-session-timeline.component';
import {UserService} from '../../../settings/user-management/user.service';
import {takeUntil} from 'rxjs/operators';

interface UserChartConfig {
  id: string;
  component: any;
  enabled: boolean;
  order: number;
}

@Component({
  selector: 'app-user-stats',
  standalone: true,
  imports: [CommonModule, ReadingSessionHeatmapComponent, ReadingSessionTimelineComponent],
  templateUrl: './user-stats.component.html',
  styleUrls: ['./user-stats.component.scss']
})
export class UserStatsComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  private userService = inject(UserService);
  public currentYear = new Date().getFullYear();
  public userName: string = '';

  ngOnInit(): void {
    this.userService.userState$
      .pipe(takeUntil(this.destroy$))
      .subscribe(state => {
        if (state.user) {
          this.userName = state.user.name || state.user.username;
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
