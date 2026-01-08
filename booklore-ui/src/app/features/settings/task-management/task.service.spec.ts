import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';

import {
  TaskService,
  TaskType,
  TASK_TYPE_CONFIG,
  TaskCreateRequest,
  TaskCreateResponse,
  TaskStatusResponse,
  TaskStatus,
  TaskCancelResponse,
  TaskInfo,
  TaskHistory,
  CronConfig,
  TaskCronConfigRequest,
  TaskProgressPayload,
  MetadataReplaceMode
} from './task.service';

describe('TaskService', () => {
  let service: TaskService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        TaskService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(TaskService));
  });

  it('should get available tasks', () => {
    const mockTasks: TaskInfo[] = [
      {
        taskType: TaskType.REFRESH_LIBRARY_METADATA,
        name: 'Refresh Metadata',
        description: 'desc',
        parallel: false,
        async: true,
        cronSupported: true,
        cronConfig: null
      }
    ];
    httpClientMock.get.mockReturnValue(of(mockTasks));
    service.getAvailableTasks().subscribe(tasks => {
      expect(tasks).toEqual(mockTasks);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringContaining('/api/v1/tasks'));
    });
  });

  it('should start a task', () => {
    const req: TaskCreateRequest = {taskType: TaskType.REFRESH_LIBRARY_METADATA};
    const resp: TaskCreateResponse = {id: '1', type: TaskType.REFRESH_LIBRARY_METADATA, status: TaskStatus.ACCEPTED};
    httpClientMock.post.mockReturnValue(of(resp));
    service.startTask(req).subscribe(result => {
      expect(result).toEqual(resp);
      expect(httpClientMock.post).toHaveBeenCalledWith(expect.stringContaining('/start'), req);
    });
  });

  it('should get latest tasks for each type', () => {
    const resp: TaskStatusResponse = {taskHistories: []};
    httpClientMock.get.mockReturnValue(of(resp));
    service.getLatestTasksForEachType().subscribe(result => {
      expect(result).toEqual(resp);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringContaining('/last'));
    });
  });

  it('should cancel a task', () => {
    const resp: TaskCancelResponse = {taskId: '1', cancelled: true, message: 'Cancelled'};
    httpClientMock.delete.mockReturnValue(of(resp));
    service.cancelTask('1').subscribe(result => {
      expect(result).toEqual(resp);
      expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringContaining('/1/cancel'));
    });
  });

  it('should update cron config', () => {
    const req: TaskCronConfigRequest = {cronExpression: '* * * * *', enabled: true};
    const resp: CronConfig = {
      id: 1,
      taskType: TaskType.REFRESH_LIBRARY_METADATA,
      cronExpression: '* * * * *',
      enabled: true,
      options: null,
      createdAt: null,
      updatedAt: null
    };
    httpClientMock.patch.mockReturnValue(of(resp));
    service.updateCronConfig(TaskType.REFRESH_LIBRARY_METADATA, req).subscribe(result => {
      expect(result).toEqual(resp);
      expect(httpClientMock.patch).toHaveBeenCalledWith(expect.stringContaining(`/${TaskType.REFRESH_LIBRARY_METADATA}/cron`), req);
    });
  });

  it('should handle task progress subject', () => {
    const progress: TaskProgressPayload = {
      taskId: '1',
      taskType: TaskType.REFRESH_LIBRARY_METADATA,
      message: 'Running',
      progress: 50,
      taskStatus: TaskStatus.IN_PROGRESS
    };
    let emitted: TaskProgressPayload | null = null;
    service.taskProgress$.subscribe(val => emitted = val);
    service.handleTaskProgress(progress);
    expect(emitted).toEqual(progress);
  });
});

describe('TaskService - API Contract Tests', () => {
  let service: TaskService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        TaskService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(TaskService));
  });

  describe('TaskInfo interface contract', () => {
    it('should validate all required TaskInfo fields exist', () => {
      const requiredFields: (keyof TaskInfo)[] = [
        'taskType', 'name', 'description', 'parallel', 'async', 'cronSupported', 'cronConfig'
      ];
      const mockResponse: TaskInfo = {
        taskType: TaskType.REFRESH_LIBRARY_METADATA,
        name: 'Refresh',
        description: 'desc',
        parallel: false,
        async: true,
        cronSupported: true,
        cronConfig: null
      };
      httpClientMock.get.mockReturnValue(of([mockResponse]));
      service.getAvailableTasks().subscribe(tasks => {
        requiredFields.forEach(field => {
          expect(tasks[0]).toHaveProperty(field);
        });
      });
    });
  });

  describe('TaskHistory interface contract', () => {
    it('should validate all required TaskHistory fields exist', () => {
      const requiredFields: (keyof TaskHistory)[] = [
        'id', 'type', 'status', 'progressPercentage', 'message', 'createdAt', 'updatedAt', 'completedAt'
      ];
      const mockHistory: TaskHistory = {
        id: '1',
        type: TaskType.REFRESH_LIBRARY_METADATA,
        status: TaskStatus.COMPLETED,
        progressPercentage: 100,
        message: 'Done',
        createdAt: 'now',
        updatedAt: 'now',
        completedAt: 'now'
      };
      httpClientMock.get.mockReturnValue(of({taskHistories: [mockHistory]}));
      service.getLatestTasksForEachType().subscribe(resp => {
        requiredFields.forEach(field => {
          expect(resp.taskHistories[0]).toHaveProperty(field);
        });
      });
    });
  });

  describe('Enum value contract', () => {
    it('should validate TaskType enum values', () => {
      const values = Object.values(TaskType);
      expect(values).toContain('CLEAR_CBX_CACHE');
      expect(values).toContain('REFRESH_LIBRARY_METADATA');
      expect(values).toContain('REFRESH_METADATA_MANUAL');
    });

    it('should validate TaskStatus enum values', () => {
      const values = Object.values(TaskStatus);
      expect(values).toContain('ACCEPTED');
      expect(values).toContain('IN_PROGRESS');
      expect(values).toContain('COMPLETED');
      expect(values).toContain('FAILED');
      expect(values).toContain('CANCELLED');
      expect(values).toContain('PENDING');
    });

    it('should validate MetadataReplaceMode enum values', () => {
      const values = Object.values(MetadataReplaceMode);
      expect(values).toContain('REPLACE_ALL');
      expect(values).toContain('REPLACE_MISSING');
    });
  });

  describe('HTTP endpoint contract', () => {
    it('should call correct endpoint for getAvailableTasks', () => {
      httpClientMock.get.mockReturnValue(of([]));
      service.getAvailableTasks().subscribe();
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/tasks$/));
    });

    it('should call correct endpoint for startTask', () => {
      httpClientMock.post.mockReturnValue(of({}));
      const req: TaskCreateRequest = {taskType: TaskType.CLEAR_CBX_CACHE};
      service.startTask(req).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/tasks\/start$/), req);
    });

    it('should call correct endpoint for getLatestTasksForEachType', () => {
      httpClientMock.get.mockReturnValue(of({}));
      service.getLatestTasksForEachType().subscribe();
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/tasks\/last$/));
    });

    it('should call correct endpoint for cancelTask', () => {
      httpClientMock.delete.mockReturnValue(of({}));
      service.cancelTask('abc').subscribe();
      expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/tasks\/abc\/cancel$/));
    });

    it('should call correct endpoint for updateCronConfig', () => {
      httpClientMock.patch.mockReturnValue(of({}));
      const req: TaskCronConfigRequest = {enabled: true};
      service.updateCronConfig(TaskType.CLEAR_PDF_CACHE, req).subscribe();
      expect(httpClientMock.patch).toHaveBeenCalledWith(expect.stringMatching(new RegExp(`/api/v1/tasks/${TaskType.CLEAR_PDF_CACHE}/cron$`)), req);
    });
  });

  describe('Request payload contract', () => {
    it('should send TaskCreateRequest with correct structure', () => {
      httpClientMock.post.mockReturnValue(of({}));
      const req: TaskCreateRequest = {taskType: TaskType.CLEAR_CBX_CACHE, options: {metadataReplaceMode: MetadataReplaceMode.REPLACE_ALL}};
      service.startTask(req).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(expect.any(String), req);
    });

    it('should send TaskCronConfigRequest with correct structure', () => {
      httpClientMock.patch.mockReturnValue(of({}));
      const req: TaskCronConfigRequest = {cronExpression: '0 0 * * *', enabled: false};
      service.updateCronConfig(TaskType.CLEAR_CBX_CACHE, req).subscribe();
      expect(httpClientMock.patch).toHaveBeenCalledWith(expect.any(String), req);
    });
  });

  describe('Response type contract', () => {
    it('should expect TaskInfo[] from getAvailableTasks', () => {
      const mock: TaskInfo[] = [{
        taskType: TaskType.CLEAR_CBX_CACHE,
        name: 'Clear CBX',
        description: 'desc',
        parallel: false,
        async: false,
        cronSupported: false,
        cronConfig: null
      }];
      httpClientMock.get.mockReturnValue(of(mock));
      service.getAvailableTasks().subscribe(tasks => {
        expect(Array.isArray(tasks)).toBe(true);
        expect(tasks[0]).toHaveProperty('taskType');
      });
    });

    it('should expect TaskCreateResponse from startTask', () => {
      const resp: TaskCreateResponse = {id: '1', type: TaskType.CLEAR_CBX_CACHE, status: TaskStatus.ACCEPTED};
      httpClientMock.post.mockReturnValue(of(resp));
      service.startTask({taskType: TaskType.CLEAR_CBX_CACHE}).subscribe(result => {
        expect(result).toHaveProperty('id');
        expect(result).toHaveProperty('type');
        expect(result).toHaveProperty('status');
      });
    });

    it('should expect TaskStatusResponse from getLatestTasksForEachType', () => {
      const resp: TaskStatusResponse = {taskHistories: []};
      httpClientMock.get.mockReturnValue(of(resp));
      service.getLatestTasksForEachType().subscribe(result => {
        expect(result).toHaveProperty('taskHistories');
        expect(Array.isArray(result.taskHistories)).toBe(true);
      });
    });

    it('should expect TaskCancelResponse from cancelTask', () => {
      const resp: TaskCancelResponse = {taskId: '1', cancelled: true, message: 'msg'};
      httpClientMock.delete.mockReturnValue(of(resp));
      service.cancelTask('1').subscribe(result => {
        expect(result).toHaveProperty('taskId');
        expect(result).toHaveProperty('cancelled');
        expect(result).toHaveProperty('message');
      });
    });

    it('should expect CronConfig from updateCronConfig', () => {
      const resp: CronConfig = {
        id: 1,
        taskType: TaskType.CLEAR_CBX_CACHE,
        cronExpression: '* * * * *',
        enabled: true,
        options: null,
        createdAt: null,
        updatedAt: null
      };
      httpClientMock.patch.mockReturnValue(of(resp));
      service.updateCronConfig(TaskType.CLEAR_CBX_CACHE, {enabled: true}).subscribe(result => {
        expect(result).toHaveProperty('id');
        expect(result).toHaveProperty('taskType');
        expect(result).toHaveProperty('enabled');
      });
    });
  });
});

