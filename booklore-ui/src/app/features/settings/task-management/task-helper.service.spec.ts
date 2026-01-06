import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {of, throwError} from 'rxjs';
import {TaskHelperService} from './task-helper.service';
import {TaskCreateRequest, TaskService, TaskType} from './task.service';
import {MessageService} from 'primeng/api';
import {MetadataRefreshRequest} from '../../metadata/model/request/metadata-refresh-request.model';
import {MetadataRefreshType} from '../../metadata/model/request/metadata-refresh-type.enum';

describe('TaskHelperService', () => {
  let service: TaskHelperService;
  let taskServiceMock: any;
  let messageServiceMock: any;

  beforeEach(() => {
    taskServiceMock = {
      startTask: vi.fn()
    };
    messageServiceMock = {
      add: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        TaskHelperService,
        {provide: TaskService, useValue: taskServiceMock},
        {provide: MessageService, useValue: messageServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(TaskHelperService));
  });

  it('should schedule metadata refresh and show success message', () => {
    taskServiceMock.startTask.mockReturnValue(of({}));
    const options: MetadataRefreshRequest = {refreshType: MetadataRefreshType.BOOKS, bookIds: [1, 2]};
    service.refreshMetadataTask(options).subscribe(result => {
      expect(result).toEqual({success: true});
      expect(taskServiceMock.startTask).toHaveBeenCalledWith({
        taskType: TaskType.REFRESH_METADATA_MANUAL,
        options
      });
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({
        severity: 'success',
        summary: 'Metadata Update Scheduled'
      }));
    });
  });

  it('should show error message if task already running (409)', () => {
    taskServiceMock.startTask.mockReturnValue(throwError(() => ({status: 409})));
    const options: MetadataRefreshRequest = {refreshType: MetadataRefreshType.BOOKS, bookIds: [1]};
    service.refreshMetadataTask(options).subscribe(result => {
      expect(result).toEqual({success: false});
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({
        severity: 'error',
        summary: 'Task Already Running'
      }));
    });
  });

  it('should show generic error message for other errors', () => {
    taskServiceMock.startTask.mockReturnValue(throwError(() => ({status: 500})));
    const options: MetadataRefreshRequest = {refreshType: MetadataRefreshType.BOOKS, bookIds: [1]};
    service.refreshMetadataTask(options).subscribe(result => {
      expect(result).toEqual({success: false});
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({
        severity: 'error',
        summary: 'Metadata Update Failed'
      }));
    });
  });
});

describe('TaskHelperService - API Contract Tests', () => {
  let service: TaskHelperService;
  let taskServiceMock: any;
  let messageServiceMock: any;

  beforeEach(() => {
    taskServiceMock = {
      startTask: vi.fn()
    };
    messageServiceMock = {
      add: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        TaskHelperService,
        {provide: TaskService, useValue: taskServiceMock},
        {provide: MessageService, useValue: messageServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(TaskHelperService));
  });

  it('should send TaskCreateRequest with correct structure', () => {
    taskServiceMock.startTask.mockReturnValue(of({}));
    const options: MetadataRefreshRequest = {refreshType: MetadataRefreshType.BOOKS, bookIds: [1, 2, 3]};
    service.refreshMetadataTask(options).subscribe(() => {
      const req: TaskCreateRequest = taskServiceMock.startTask.mock.calls[0][0];
      expect(req).toHaveProperty('taskType', TaskType.REFRESH_METADATA_MANUAL);
      expect(req).toHaveProperty('options', options);
    });
  });

  it('should expect {success: true} on success', () => {
    taskServiceMock.startTask.mockReturnValue(of({}));
    service.refreshMetadataTask({refreshType: MetadataRefreshType.BOOKS, bookIds: [1]}).subscribe(result => {
      expect(result).toEqual({success: true});
    });
  });

  it('should expect {success: false} on error', () => {
    taskServiceMock.startTask.mockReturnValue(throwError(() => ({status: 409})));
    service.refreshMetadataTask({refreshType: MetadataRefreshType.BOOKS, bookIds: [1]}).subscribe(result => {
      expect(result).toEqual({success: false});
    });
  });

  it('should call MessageService.add with correct contract', () => {
    taskServiceMock.startTask.mockReturnValue(of({}));
    service.refreshMetadataTask({refreshType: MetadataRefreshType.BOOKS, bookIds: [1]}).subscribe(() => {
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({
        severity: 'success',
        summary: expect.any(String),
        detail: expect.any(String)
      }));
    });
  });
});
