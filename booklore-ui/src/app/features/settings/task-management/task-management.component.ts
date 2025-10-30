import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Button} from 'primeng/button';
import {ProgressBar} from 'primeng/progressbar';
import {MessageService} from 'primeng/api';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {TaskInfo, MetadataReplaceMode, TaskHistory, TASK_TYPE_CONFIG, TaskCreateRequest, TaskCronConfigRequest, TaskProgressPayload, TaskService, TaskStatus, TaskType} from './task.service';
import {finalize, forkJoin, Subscription} from 'rxjs';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Badge} from 'primeng/badge';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-task-management',
  standalone: true,
  imports: [
    CommonModule,
    Button,
    ProgressBar,
    Select,
    FormsModule,
    ExternalDocLinkComponent,
    ToggleSwitch,
    Badge,
    Tooltip
  ],
  templateUrl: './task-management.component.html',
  styleUrl: './task-management.component.scss'
})
export class TaskManagementComponent implements OnInit, OnDestroy {
  // Services
  private messageService = inject(MessageService);
  private taskService = inject(TaskService);

  // State
  taskInfos: TaskInfo[] = [];
  taskHistories: Map<string, TaskHistory> = new Map();
  loading = false;
  executingTasks = new Set<string>();
  private subscription?: Subscription;

  // Metadata Replace Options
  metadataReplaceOptions = [
    {
      label: 'Update Missing Metadata Only (Recommended)',
      value: MetadataReplaceMode.REPLACE_MISSING
    },
    {
      label: 'Replace All Metadata (Overwrite Existing)',
      value: MetadataReplaceMode.REPLACE_ALL
    }
  ];
  selectedMetadataReplaceMode: MetadataReplaceMode = MetadataReplaceMode.REPLACE_MISSING;

  // Cron Editing State
  cronUpdating = false;
  editingCronTaskType: string | null = null;
  editingCronExpression: string = '';
  cronValidationError: string | null = null;

  // Constants
  private readonly STALE_TASK_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes
  protected readonly TaskType = TaskType;

  // ============================================================================
  // Lifecycle Hooks
  // ============================================================================

  ngOnInit(): void {
    this.loadTasks();
    this.subscribeToTaskProgress();
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  // ============================================================================
  // Data Loading & Real-time Updates
  // ============================================================================

  loadTasks(): void {
    this.loading = true;

    forkJoin({
      available: this.taskService.getAvailableTasks(),
      latest: this.taskService.getLatestTasksForEachType()
    })
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: ({available, latest}) => {
          this.taskInfos = this.sortTasksByDisplayOrder(available);
          this.taskHistories.clear();
          latest.taskHistories.forEach(history => {
            this.taskHistories.set(history.type, history);
          });
        },
        error: (error) => {
          console.error('Error loading tasks:', error);
          this.showMessage('error', 'Error', 'Failed to load tasks');
        }
      });
  }

  private subscribeToTaskProgress(): void {
    this.subscription = this.taskService.taskProgress$.subscribe(progress => {
      if (progress) {
        this.updateTaskWithProgress(progress);
      }
    });
  }

  private updateTaskWithProgress(progress: TaskProgressPayload): void {
    const existingHistory = this.taskHistories.get(progress.taskType);

    const updatedHistory: TaskHistory = {
      id: progress.taskId,
      type: progress.taskType,
      status: progress.taskStatus,
      progressPercentage: progress.progress,
      message: progress.message,
      createdAt: existingHistory?.createdAt || new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      completedAt: (progress.taskStatus === TaskStatus.COMPLETED || progress.taskStatus === TaskStatus.FAILED)
        ? new Date().toISOString()
        : existingHistory?.completedAt || null
    };

    this.taskHistories.set(progress.taskType, updatedHistory);

    if (progress.taskStatus === TaskStatus.COMPLETED || progress.taskStatus === TaskStatus.FAILED) {
      setTimeout(() => this.loadTasks(), 1000);
    }
  }

  private sortTasksByDisplayOrder(tasks: TaskInfo[]): TaskInfo[] {
    return tasks.sort((a, b) => {
      const orderA = TASK_TYPE_CONFIG[a.taskType as TaskType]?.displayOrder ?? 999;
      const orderB = TASK_TYPE_CONFIG[b.taskType as TaskType]?.displayOrder ?? 999;
      return orderA - orderB;
    });
  }

  // ============================================================================
  // Task Execution Operations
  // ============================================================================

  canExecuteTask(taskType: string): boolean {
    const history = this.taskHistories.get(taskType);
    return this.canRunTask(history) || this.isTaskStale(history);
  }

  executeTask(taskType: string): void {
    this.runTask(taskType);
  }

  runTask(type: string): void {
    const history = this.taskHistories.get(type);
    if (!this.canRunTask(history) && !this.isTaskStale(history)) {
      this.showMessage('warn', 'Task Already Running', 'This task is already in progress or pending.');
      return;
    }

    let options = null;

    if (type === TaskType.REFRESH_LIBRARY_METADATA) {
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

    this.executingTasks.add(type);
    this.taskService.startTask(request)
      .pipe(finalize(() => this.executingTasks.delete(type)))
      .subscribe({
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

  cancelTask(taskType: string): void {
    const history = this.taskHistories.get(taskType);
    if (!history?.id) {
      this.showMessage('error', 'Error', 'Cannot cancel task without ID.');
      return;
    }

    this.executingTasks.add(taskType);
    this.taskService.cancelTask(history.id)
      .pipe(finalize(() => this.executingTasks.delete(taskType)))
      .subscribe({
        next: (response) => {
          if (response.cancelled) {
            this.showMessage('success', 'Task Cancelled', response.message || 'Task has been cancelled successfully.');
            this.loadTasks();
          } else {
            this.showMessage('error', 'Cancellation Failed', response.message || 'Failed to cancel the task.');
          }
        },
        error: (error) => {
          console.error('Error cancelling task:', error);
          this.showMessage('error', 'Error', 'Failed to cancel the task. The task may already be completed or failed.');
          this.loadTasks();
        }
      });
  }

  canRunTask(history: TaskHistory | undefined): boolean {
    return !history?.status || history.status === TaskStatus.COMPLETED || history.status === TaskStatus.FAILED || history.status === TaskStatus.CANCELLED;
  }

  canCancelTask(history: TaskHistory | undefined): boolean {
    return history?.status === TaskStatus.IN_PROGRESS || history?.status === TaskStatus.PENDING;
  }

  isTaskExecuting(taskType: string): boolean {
    return this.executingTasks.has(taskType);
  }

  isTaskRunning(taskType: string): boolean {
    const history = this.taskHistories.get(taskType);
    return history?.status === TaskStatus.IN_PROGRESS || history?.status === TaskStatus.PENDING;
  }

  isTaskStale(history: TaskHistory | undefined): boolean {
    if (!history || !this.isTaskRunningForHistory(history) || !history.updatedAt) {
      return false;
    }
    const lastUpdate = new Date(history.updatedAt).getTime();
    const now = Date.now();
    return (now - lastUpdate) > this.STALE_TASK_THRESHOLD_MS;
  }

  private isTaskRunningForHistory(history: TaskHistory): boolean {
    return history.status === TaskStatus.IN_PROGRESS || history.status === TaskStatus.PENDING;
  }

  // ============================================================================
  // Cron Configuration Management
  // ============================================================================

  isCronSupported(taskType: string): boolean {
    const taskInfo = this.taskInfos.find(t => t.taskType === taskType);
    return taskInfo?.cronSupported || false;
  }

  getCronConfig(taskType: string): any {
    const taskInfo = this.taskInfos.find(t => t.taskType === taskType);
    return taskInfo?.cronConfig;
  }

  toggleCronEnabled(taskType: string): void {
    const cronConfig = this.getCronConfig(taskType);
    if (!cronConfig) return;

    const request: TaskCronConfigRequest = {
      enabled: !cronConfig.enabled
    };

    this.updateCronConfig(taskType, request);
  }

  isEditingCron(taskType: string): boolean {
    return this.editingCronTaskType === taskType;
  }

  startEditingCron(taskType: string): void {
    const cronConfig = this.getCronConfig(taskType);
    this.editingCronTaskType = taskType;
    this.editingCronExpression = cronConfig?.cronExpression || '';
    this.cronValidationError = null;
    this.validateCronExpression(this.editingCronExpression);
  }

  cancelEditingCron(): void {
    this.editingCronTaskType = null;
    this.editingCronExpression = '';
    this.cronValidationError = null;
  }

  onCronExpressionChange(): void {
    this.validateCronExpression(this.editingCronExpression);
  }

  saveCronExpression(taskType: string): void {
    if (this.cronValidationError) {
      return;
    }

    const expression = this.editingCronExpression.trim() || null;
    this.updateCronExpression(taskType, expression);
    this.cancelEditingCron();
  }

  updateCronExpression(taskType: string, expression: string | null): void {
    const request: TaskCronConfigRequest = {
      cronExpression: expression
    };

    this.updateCronConfig(taskType, request);
  }

  private updateCronConfig(taskType: string, request: TaskCronConfigRequest): void {
    this.cronUpdating = true;
    this.taskService.updateCronConfig(taskType, request)
      .pipe(finalize(() => this.cronUpdating = false))
      .subscribe({
        next: (updatedConfig) => {
          const taskInfoIndex = this.taskInfos.findIndex(t => t.taskType === taskType);
          if (taskInfoIndex !== -1) {
            this.taskInfos[taskInfoIndex].cronConfig = updatedConfig;
          }
          this.showMessage('success', 'Cron Updated', 'Scheduled task configuration has been updated successfully.');
        },
        error: (error) => {
          console.error('Error updating cron config:', error);
          this.showMessage('error', 'Error', 'Failed to update scheduled task configuration.');
        }
      });
  }

  // ============================================================================
  // Cron Validation
  // ============================================================================

  private validateCronExpression(expression: string): void {
    if (!expression || expression.trim() === '') {
      this.cronValidationError = null;
      return;
    }

    const trimmed = expression.trim();
    const parts = trimmed.split(/\s+/);

    if (parts.length !== 6) {
      this.cronValidationError = 'Cron expression must have exactly 6 fields (seconds, minutes, hours, day, month, weekday)';
      return;
    }

    const validations = [
      {field: parts[0], name: 'Seconds', range: [0, 59]},
      {field: parts[1], name: 'Minutes', range: [0, 59]},
      {field: parts[2], name: 'Hours', range: [0, 23]},
      {field: parts[3], name: 'Day of Month', range: [1, 31]},
      {field: parts[4], name: 'Month', range: [1, 12]},
      {field: parts[5], name: 'Day of Week', range: [0, 7]}
    ];

    for (const validation of validations) {
      if (!this.isValidCronField(validation.field, validation.range[0], validation.range[1])) {
        this.cronValidationError = `Invalid ${validation.name} field: ${validation.field}`;
        return;
      }
    }

    this.cronValidationError = null;
  }

  private isValidCronField(field: string, min: number, max: number): boolean {
    if (field === '*' || field === '?') {
      return true;
    }

    if (field.includes('-')) {
      const [start, end] = field.split('-').map(Number);
      return !isNaN(start) && !isNaN(end) && start >= min && end <= max && start <= end;
    }

    if (field.includes('/')) {
      const [range, step] = field.split('/');
      const stepNum = Number(step);
      if (isNaN(stepNum) || stepNum <= 0) return false;

      if (range === '*') return true;
      if (range.includes('-')) {
        const [start, end] = range.split('-').map(Number);
        return !isNaN(start) && !isNaN(end) && start >= min && end <= max;
      }
      return false;
    }

    if (field.includes(',')) {
      const values = field.split(',').map(Number);
      return values.every(val => !isNaN(val) && val >= min && val <= max);
    }

    const num = Number(field);
    return !isNaN(num) && num >= min && num <= max;
  }

  // ============================================================================
  // UI Helper Methods - Task Information
  // ============================================================================

  getTaskDisplayName(type: string): string {
    const taskInfo = this.taskInfos.find(t => t.taskType === type);
    return taskInfo?.name || type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
  }

  getTaskDescription(type: string): string {
    const taskInfo = this.taskInfos.find(t => t.taskType === type);
    return taskInfo?.description || 'System maintenance task.';
  }

  getTaskDisplayOrder(type: string): number {
    return TASK_TYPE_CONFIG[type as TaskType]?.displayOrder ?? 999;
  }

  getTaskLabel(taskType: string): string {
    return `${this.getTaskDisplayOrder(taskType)}. ${this.getTaskDisplayName(taskType)}`;
  }

  getTaskIcon(taskType: string): string {
    const icons: Record<string, string> = {
      [TaskType.CLEAR_CBX_CACHE]: 'pi-database',
      [TaskType.CLEAR_PDF_CACHE]: 'pi-database',
      [TaskType.REFRESH_LIBRARY_METADATA]: 'pi-refresh',
      [TaskType.UPDATE_BOOK_RECOMMENDATIONS]: 'pi-sparkles',
      [TaskType.CLEANUP_DELETED_BOOKS]: 'pi-trash',
      [TaskType.SYNC_LIBRARY_FILES]: 'pi-sync',
      [TaskType.CLEANUP_TEMP_METADATA]: 'pi-file'
    };
    return icons[taskType] || 'pi-cog';
  }

  getTaskMetadata(taskType: string): string | null {
    const taskInfo = this.taskInfos.find(t => t.taskType === taskType);
    return taskInfo?.metadata || null;
  }

  hasMetadata(taskType: string): boolean {
    const taskInfo = this.taskInfos.find(t => t.taskType === taskType);
    return !!taskInfo?.metadata && taskInfo.metadata.trim().length > 0;
  }

  getMetadataIcon(taskType: string): string {
    const icons: Record<string, string> = {
      [TaskType.CLEAR_CBX_CACHE]: 'pi-database',
      [TaskType.CLEAR_PDF_CACHE]: 'pi-database',
      [TaskType.CLEANUP_DELETED_BOOKS]: 'pi-trash',
      [TaskType.CLEANUP_TEMP_METADATA]: 'pi-file'
    };
    return icons[taskType] || 'pi-info-circle';
  }

  // ============================================================================
  // UI Helper Methods - Task History & Status
  // ============================================================================

  getTaskHistory(taskType: string): TaskHistory | undefined {
    return this.taskHistories.get(taskType);
  }

  getTaskProgressPercentage(taskType: string): number | null {
    return this.taskHistories.get(taskType)?.progressPercentage || null;
  }

  getTaskUpdatedAt(taskType: string): string | null {
    return this.taskHistories.get(taskType)?.updatedAt || null;
  }

  getTaskStatusMessage(taskType: string): string {
    const history = this.taskHistories.get(taskType);
    if (this.isTaskStale(history)) {
      return 'Task appears to be stuck (no updates received)';
    }
    return history?.message || 'Processing...';
  }

  getLastRunMessage(taskType: string): string {
    const history = this.taskHistories.get(taskType);
    if (!history?.completedAt && !history?.updatedAt) {
      return 'Never run';
    }

    const dateStr = history.completedAt || history.updatedAt;
    if (!dateStr) return 'Never run';

    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString();
  }

  getLastRunInfoClass(taskType: string): string {
    const history = this.taskHistories.get(taskType);
    if (!history?.status) return 'info';

    switch (history.status) {
      case TaskStatus.COMPLETED:
        return 'success';
      case TaskStatus.FAILED:
        return 'error';
      case TaskStatus.CANCELLED:
        return 'warning';
      default:
        return 'info';
    }
  }

  // ============================================================================
  // UI Helper Methods - Buttons & Icons
  // ============================================================================

  getTaskButtonIcon(taskType: string): string {
    if (this.isTaskExecuting(taskType)) {
      return 'pi pi-spinner pi-spin';
    }
    return 'pi ' + this.getTaskIcon(taskType);
  }

  getTaskButtonLabel(taskType: string): string {
    const history = this.taskHistories.get(taskType);
    if (this.isTaskStale(history)) {
      return 'Re-run';
    }
    return 'Run';
  }

  getCancelButtonIcon(taskType: string): string {
    if (this.isTaskExecuting(taskType)) {
      return 'pi pi-spinner pi-spin';
    }
    return 'pi pi-times';
  }

  // ============================================================================
  // UI Helper Methods - Metadata Replace
  // ============================================================================

  getMetadataReplaceDescription(mode: MetadataReplaceMode): string {
    switch (mode) {
      case MetadataReplaceMode.REPLACE_MISSING:
        return 'Only update books that are missing metadata information. Existing metadata will be preserved.';
      case MetadataReplaceMode.REPLACE_ALL:
        return 'Replace all metadata for all books, even if they already have metadata. Use with caution as this will overwrite existing data.';
      default:
        return '';
    }
  }

  // ============================================================================
  // Utility Methods
  // ============================================================================

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleString();
  }

  private showMessage(severity: 'success' | 'info' | 'warn' | 'error', summary: string, detail: string): void {
    this.messageService.add({
      severity,
      summary,
      detail
    });
  }
}
