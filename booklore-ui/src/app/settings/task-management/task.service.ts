import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable} from 'rxjs';
import {API_CONFIG} from '../../config/api-config';

export enum TaskType {
  CLEAR_CBX_CACHE = 'CLEAR_CBX_CACHE',
  CLEAR_PDF_CACHE = 'CLEAR_PDF_CACHE',
  RE_SCAN_LIBRARY = 'RE_SCAN_LIBRARY'
}

export const TASK_TYPE_CONFIG: Record<TaskType, { parallel: boolean; async: boolean }> = {
  [TaskType.CLEAR_CBX_CACHE]: {parallel: false, async: false},
  [TaskType.CLEAR_PDF_CACHE]: {parallel: false, async: false},
  [TaskType.RE_SCAN_LIBRARY]: {parallel: false, async: true}
};

export enum MetadataReplaceMode {
  REPLACE_ALL = 'REPLACE_ALL',
  REPLACE_MISSING = 'REPLACE_MISSING'
}

export interface LibraryRescanOptions {
  metadataReplaceMode?: MetadataReplaceMode;
}

export interface TaskCreateRequest {
  taskType: TaskType;
  options?: LibraryRescanOptions | null;
}

export interface TaskCreateResponse {
  id?: string;
  type: string;
  status: TaskStatus;
  message?: string;
  createdAt?: string;
}

export interface TaskStatusResponse {
  tasks: Task[];
}

export enum TaskStatus {
  ACCEPTED = 'ACCEPTED',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED',
  PENDING = 'PENDING'
}

export interface TaskMetadata {
  currentCacheSizeBytes?: number;
  currentCacheSize?: string;

  [key: string]: any;
}

export interface Task {
  id: string | null;
  type: string;
  status: string | null;
  progressPercentage: number | null;
  message: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  completedAt: string | null;
  metadata: TaskMetadata;
}

export interface TaskCancelResponse {
  taskId: string;
  cancelled: boolean;
  message: string;
}

export interface TaskProgressPayload {
  taskId: string;
  taskType: string;
  message: string;
  progress: number; // 0-100 percentage
  taskStatus: TaskStatus;
}

@Injectable({
  providedIn: 'root'
})
export class TaskService {
  private http = inject(HttpClient);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v2/tasks`;

  private taskProgressSubject = new BehaviorSubject<TaskProgressPayload | null>(null);
  public taskProgress$ = this.taskProgressSubject.asObservable();

  startTask(request: TaskCreateRequest): Observable<TaskCreateResponse> {
    return this.http.post<TaskCreateResponse>(this.baseUrl, request);
  }

  getLatestTasksForEachType(): Observable<TaskStatusResponse> {
    return this.http.get<TaskStatusResponse>(`${this.baseUrl}/latest`);
  }

  cancelTask(taskId: string): Observable<TaskCancelResponse> {
    return this.http.delete<TaskCancelResponse>(`${this.baseUrl}/${taskId}`);
  }

  handleTaskProgress(progress: TaskProgressPayload): void {
    this.taskProgressSubject.next(progress);
  }
}
