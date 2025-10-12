import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Button} from 'primeng/button';
import {Badge} from 'primeng/badge';
import {ProgressBar} from 'primeng/progressbar';
import {MessageService} from 'primeng/api';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {MetadataReplaceMode, Task, TASK_TYPE_CONFIG, TaskCreateRequest, TaskProgressPayload, TaskService, TaskStatus, TaskType} from './task.service';
import {finalize, Subscription} from 'rxjs';

@Component({
  selector: 'app-task-management',
  standalone: true,
  imports: [
    CommonModule,
    Button,
    Badge,
    ProgressBar,
    Select,
    FormsModule
  ],
  templateUrl: './task-management.component.html',
  styleUrl: './task-management.component.scss'
})
export class TaskManagementComponent implements OnInit, OnDestroy {
  private messageService = inject(MessageService);
  private taskService = inject(TaskService);

  tasks: Task[] = [];
  loading = false;
  private subscription?: Subscription;

  metadataReplaceOptions = [
    {label: 'Replace Missing Only (Recommended)', value: MetadataReplaceMode.REPLACE_MISSING},
    {label: 'Replace All Metadata', value: MetadataReplaceMode.REPLACE_ALL}
  ];
  selectedMetadataReplaceMode: MetadataReplaceMode = MetadataReplaceMode.REPLACE_MISSING;

  ngOnInit(): void {
    this.loadTasks();
    this.subscribeToTaskProgress();
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private subscribeToTaskProgress(): void {
    this.subscription = this.taskService.taskProgress$.subscribe(progress => {
      if (progress) {
        this.updateTaskWithProgress(progress);
      }
    });
  }

  private updateTaskWithProgress(progress: TaskProgressPayload): void {
    const taskIndex = this.tasks.findIndex(task => task.id === progress.taskId);
    if (taskIndex !== -1) {
      this.tasks[taskIndex] = {
        ...this.tasks[taskIndex],
        status: progress.taskStatus,
        progressPercentage: progress.progress,
        message: progress.message,
        updatedAt: new Date().toISOString()
      };

      if (progress.taskStatus === TaskStatus.COMPLETED || progress.taskStatus === TaskStatus.FAILED) {
        setTimeout(() => this.loadTasks(), 1000);
      }
    }
  }

  loadTasks(): void {
    this.loading = true;
    this.taskService.getLatestTasksForEachType()
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: (response) => {
          this.tasks = response.tasks;
        },
        error: (error) => {
          console.error('Error loading tasks:', error);
          this.showMessage('error', 'Error', 'Failed to load tasks');
        }
      });
  }

  getTaskDisplayName(type: string): string {
    const taskNames: Record<string, string> = {
      'CLEAR_CBX_CACHE': 'Clear CBX Cache',
      'CLEAR_PDF_CACHE': 'Clear PDF Cache',
      'RE_SCAN_LIBRARY': 'Refresh Metadata from Library Files'
    };
    return taskNames[type] || type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
  }

  getTaskDescription(type: string): string {
    const descriptions: Record<string, string> = {
      'CLEAR_CBX_CACHE': 'Clears the cached images for CBX files to free up disk space.',
      'CLEAR_PDF_CACHE': 'Clears the cached images for PDF files to free up disk space.',
      'RE_SCAN_LIBRARY': 'Scans the library folders to read metadata from existing book files and update the database accordingly. Existing metadata in the database may be overwritten during this process.'
    };
    return descriptions[type] || 'System maintenance task.';
  }

  canExecuteTask(task: Task): boolean {
    return this.canRunTask(task) || this.isTaskStale(task);
  }

  executeTask(task: Task): void {
    this.runTask(task.type);
  }

  canRunTask(task: Task): boolean {
    return !task.status || task.status === TaskStatus.COMPLETED || task.status === TaskStatus.FAILED || task.status === TaskStatus.CANCELLED;
  }

  canCancelTask(task: Task): boolean {
    return task.status === TaskStatus.IN_PROGRESS || task.status === TaskStatus.PENDING;
  }

  runTask(type: string): void {
    if (!this.canRunTask(this.getTaskByType(type)) && !this.isTaskStale(this.getTaskByType(type))) {
      this.showMessage('warn', 'Task Already Running', 'This task is already in progress or pending.');
      return;
    }

    let options = null;

    if (type === TaskType.RE_SCAN_LIBRARY) {
      options = {
        metadataReplaceMode: this.selectedMetadataReplaceMode
      };
    }

    this.runTaskWithOptions(type, options);
  }

  private runTaskWithOptions(type: string, options: any): void {
    const request: TaskCreateRequest = {
      taskType: type as TaskType,
      options: options
    };

    const isAsync = TASK_TYPE_CONFIG[type as TaskType]?.async || false;

    this.taskService.startTask(request).subscribe({
      next: (response) => {
        if (isAsync) {
          this.showMessage('info', 'Task Queued', `${this.getTaskDisplayName(type)} has been queued and will run in the background.`);
        } else {
          if (response.status === TaskStatus.COMPLETED) {
            this.showMessage('success', 'Task Completed', `${this.getTaskDisplayName(type)} has been completed successfully.`);
          } else if (response.status === TaskStatus.FAILED) {
            this.showMessage('error', 'Task Failed', response.message || `${this.getTaskDisplayName(type)} failed to complete.`);
          } else {
            this.showMessage('success', 'Task Started', `${this.getTaskDisplayName(type)} has been started successfully.`);
          }
        }
        this.loadTasks();
      },
      error: (error) => {
        console.error('Error starting task:', error);
        this.showMessage('error', 'Error', `Failed to start ${this.getTaskDisplayName(type)}.`);
      }
    });
  }

  cancelTask(task: Task): void {
    if (!task.id) {
      this.showMessage('error', 'Error', 'Cannot cancel task without ID.');
      return;
    }

    this.taskService.cancelTask(task.id).subscribe({
      next: (response) => {
        if (response.cancelled) {
          this.showMessage('success', 'Task Cancelled', response.message || 'Task has been cancelled successfully.');
          this.loadTasks(); // Refresh the task list
        } else {
          this.showMessage('error', 'Cancellation Failed', response.message || 'Failed to cancel the task.');
        }
      },
      error: (error) => {
        console.error('Error cancelling task:', error);
        this.showMessage('error', 'Error', 'Failed to cancel the task. The task may already be completed or failed.');
        // Refresh tasks in case the task actually completed
        this.loadTasks();
      }
    });
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleString();
  }

  hasMetadata(task: Task): boolean {
    return task.metadata && Object.keys(task.metadata).length > 0;
  }

  isTaskRunning(task: Task): boolean {
    return task.status === TaskStatus.IN_PROGRESS || task.status === TaskStatus.PENDING;
  }

  isTaskStale(task: Task): boolean {
    if (!task.updatedAt || !this.isTaskRunning(task)) {
      return false;
    }

    const lastUpdate = new Date(task.updatedAt);
    const now = new Date();
    const timeDiff = now.getTime() - lastUpdate.getTime();
    const minutesDiff = timeDiff / (1000 * 60);

    return minutesDiff > 2;
  }

  getTaskStatusMessage(task: Task): string {
    if (this.isTaskStale(task)) {
      return 'Task appears to be stuck. You may need to cancel it.';
    }
    return task.message || 'Task is running...';
  }

  getLastRunInfoClass(status: string | null): string {
    if (status === 'COMPLETED') return 'success';
    if (status === 'FAILED') return 'error';
    if (status === 'CANCELLED') return 'warning';
    return 'info';
  }

  getLastRunMessage(task: Task): string {
    const timeText = task.completedAt ? this.formatDate(task.completedAt) :
      task.updatedAt ? this.formatDate(task.updatedAt) : '';

    if (task.status === 'COMPLETED') {
      return `Last completed: ${timeText}`;
    } else if (task.status === 'FAILED') {
      return `Failed: ${timeText}`;
    } else if (task.status === 'CANCELLED') {
      return `Cancelled: ${timeText}`;
    }

    return timeText ? `Last run: ${timeText}` : 'Not run yet';
  }

  getMetadataReplaceDescription(mode: MetadataReplaceMode): string {
    const descriptions: Record<MetadataReplaceMode, string> = {
      [MetadataReplaceMode.REPLACE_ALL]: 'Replace all existing metadata with data from files, even if metadata already exists in the database.',
      [MetadataReplaceMode.REPLACE_MISSING]: 'Only update metadata that is missing or empty in the database, preserving existing metadata.'
    };
    return descriptions[mode];
  }

  private getTaskByType(type: string): Task {
    return this.tasks.find(task => task.type === type) || {
      id: null,
      type,
      status: null,
      progressPercentage: null,
      message: null,
      createdAt: null,
      updatedAt: null,
      completedAt: null,
      metadata: {}
    };
  }

  private showMessage(severity: 'success' | 'info' | 'warn' | 'error', summary: string, detail: string): void {
    this.messageService.add({
      severity,
      summary,
      detail
    });
  }

  protected readonly TaskType = TaskType;
  protected readonly MetadataReplaceMode = MetadataReplaceMode;
}
