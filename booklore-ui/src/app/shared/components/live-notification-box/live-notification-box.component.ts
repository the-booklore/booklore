import {Component, inject} from '@angular/core';
import {NotificationEventService} from '../../websocket/notification-event.service';
import {LogNotification} from '../../websocket/model/log-notification.model';
import {Tag} from 'primeng/tag';
import {NgIf} from '@angular/common';
import {TagComponent} from '../tag/tag.component';

@Component({
  selector: 'app-live-notification-box',
  standalone: true,
  templateUrl: './live-notification-box.component.html',
  styleUrls: ['./live-notification-box.component.scss'],
  host: {
    class: 'config-panel'
  },
  imports: [
    TagComponent
  ]
})
export class LiveNotificationBoxComponent {
  latestNotification: LogNotification = {message: 'No recent notifications...'};

  private notificationService = inject(NotificationEventService);

  constructor() {
    this.notificationService.latestNotification$.subscribe(notification => {
      this.latestNotification = notification;
    });
  }

  getSeverityColor(severity?: string): 'red' | 'amber' | 'green' | 'gray' {
    switch (severity) {
      case 'ERROR':
        return 'red';
      case 'WARN':
        return 'amber';
      case 'INFO':
        return 'green';
      default:
        return 'gray';
    }
  }
}
