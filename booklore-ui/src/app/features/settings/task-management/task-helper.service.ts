import {inject, Injectable} from '@angular/core';
import {MessageService} from 'primeng/api';
import {MetadataRefreshRequest} from '../../metadata/model/request/metadata-refresh-request.model';
import {catchError, map} from 'rxjs/operators';
import {of} from 'rxjs';
import {TaskCreateRequest, TaskService, TaskType} from './task.service';

@Injectable({
  providedIn: 'root'
})
export class TaskHelperService {
  private taskService = inject(TaskService);
  private messageService = inject(MessageService);

  refreshMetadataTask(options: MetadataRefreshRequest) {
    const request: TaskCreateRequest = {
      taskType: TaskType.REFRESH_METADATA_MANUAL,
      options
    };
    return this.taskService.startTask(request).pipe(
      map(() => {
        this.messageService.add({
          severity: 'success',
          summary: 'Metadata Update Scheduled',
          detail: 'The metadata update for the selected books has been successfully scheduled.'
        });
        return {success: true};
      }),
      catchError((e) => {
        if (e.status === 409) {
          this.messageService.add({
            severity: 'error',
            summary: 'Task Already Running',
            life: 5000,
            detail: 'A metadata refresh task is already in progress. Please wait for it to complete before starting another one.'
          });
        } else {
          this.messageService.add({
            severity: 'error',
            summary: 'Metadata Update Failed',
            life: 5000,
            detail: 'An unexpected error occurred while scheduling the metadata update. Please try again later or contact support if the issue persists.'
          });
        }
        return of({success: false});
      })
    );
  }
}
