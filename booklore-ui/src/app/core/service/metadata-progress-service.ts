import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, Subject, Subscription} from 'rxjs';
import {MetadataBatchProgressNotification} from '../model/metadata-batch-progress.model';
import {MetadataTaskService} from '../../book/service/metadata-task';
import {UserService} from '../../settings/user-management/user.service';
import {filter, take} from 'rxjs/operators';

@Injectable({providedIn: 'root'})
export class MetadataProgressService implements OnDestroy {
  private progressMap = new Map<string, BehaviorSubject<MetadataBatchProgressNotification>>();

  private progressUpdatesSubject = new Subject<MetadataBatchProgressNotification>();
  progressUpdates$ = this.progressUpdatesSubject.asObservable();

  private activeTasksSubject = new BehaviorSubject<{ [taskId: string]: MetadataBatchProgressNotification }>({});
  activeTasks$ = this.activeTasksSubject.asObservable();

  private metadataTaskService = inject(MetadataTaskService);
  private userService = inject(UserService);

  private subscriptions = new Subscription();

  constructor() {
    const sub = this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user),
        take(1)
      )
      .subscribe(userState => {
        if (!this.hasMetadataPermissions(userState.user)) {
          return;
        }
        const activeTasksSub = this.metadataTaskService.getActiveTasks().subscribe({
          next: (tasks) => this.initializeActiveTasks(tasks),
          error: (err) => console.warn('Failed to fetch active metadata tasks:', err)
        });
        this.subscriptions.add(activeTasksSub);
      });

    this.subscriptions.add(sub);
  }

  handleIncomingProgress(progress: MetadataBatchProgressNotification): void {
    const {taskId} = progress;

    if (!this.progressMap.has(taskId)) {
      this.progressMap.set(taskId, new BehaviorSubject(progress));
    } else {
      this.progressMap.get(taskId)!.next(progress);
    }

    this.progressUpdatesSubject.next(progress);
    this.activeTasksSubject.next(this.getActiveTasks());
  }

  clearTask(taskId: string): void {
    this.progressMap.delete(taskId);
    this.activeTasksSubject.next(this.getActiveTasks());
  }

  getActiveTasks(): { [taskId: string]: MetadataBatchProgressNotification } {
    const result: { [taskId: string]: MetadataBatchProgressNotification } = {};
    this.progressMap.forEach((subject, taskId) => {
      result[taskId] = subject.getValue();
    });
    return result;
  }

  private hasMetadataPermissions(user: any): boolean {
    return !!(user?.permissions?.admin || user?.permissions?.canEditMetadata);
  }

  private initializeActiveTasks(tasks: MetadataBatchProgressNotification[]): void {
    for (const task of tasks) {
      this.progressMap.set(task.taskId, new BehaviorSubject(task));
      this.progressUpdatesSubject.next(task);
    }
    this.activeTasksSubject.next(this.getActiveTasks());
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.progressMap.forEach(subject => subject.complete());
    this.progressUpdatesSubject.complete();
    this.activeTasksSubject.complete();
  }
}
