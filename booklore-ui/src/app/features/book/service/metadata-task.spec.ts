import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, of, throwError} from 'rxjs';

import {MetadataFetchTask, MetadataTaskService} from './metadata-task';
import {API_CONFIG} from '../../../core/config/api-config';
import {MetadataBatchProgressNotification, MetadataBatchStatus} from '../../../shared/model/metadata-batch-progress.model';

describe('MetadataTaskService', () => {
  let service: MetadataTaskService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      delete: vi.fn(),
      post: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        MetadataTaskService,
        { provide: HttpClient, useValue: httpClientMock }
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);

    service = runInInjectionContext(
      injector,
      () => TestBed.inject(MetadataTaskService)
    );
  });

  it('should get task with proposals', async () => {
    const task: MetadataFetchTask = {
      id: 'task1',
      status: 'IN_PROGRESS',
      completed: 1,
      totalBooks: 2,
      startedAt: '2024-01-01T00:00:00Z',
      completedAt: null,
      initiatedBy: 'user1',
      errorMessage: null,
      proposals: []
    };
    httpClientMock.get.mockReturnValue(of({ task }));
    const result = await firstValueFrom(service.getTaskWithProposals('task1'));
    expect(result).toEqual(task);
    expect(httpClientMock.get).toHaveBeenCalledWith(`${API_CONFIG.BASE_URL}/api/metadata/tasks/task1`);
  });

  it('should handle error in getTaskWithProposals', async () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    try {
      await firstValueFrom(service.getTaskWithProposals('task2'));
    } catch (e: any) {
      expect(e.message).toBe('fail');
    }
  });

  it('should delete a task', async () => {
    httpClientMock.delete.mockReturnValue(of(void 0));
    await firstValueFrom(service.deleteTask('task3'));
    expect(httpClientMock.delete).toHaveBeenCalledWith(`${API_CONFIG.BASE_URL}/api/metadata/tasks/task3`);
  });

  it('should handle error in deleteTask', async () => {
    httpClientMock.delete.mockReturnValue(throwError(() => new Error('delete error')));
    try {
      await firstValueFrom(service.deleteTask('task4'));
    } catch (e: any) {
      expect(e.message).toBe('delete error');
    }
  });

  it('should update proposal status', async () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    await firstValueFrom(service.updateProposalStatus('task5', 123, 'ACCEPTED'));
    expect(httpClientMock.post).toHaveBeenCalledWith(
      `${API_CONFIG.BASE_URL}/api/metadata/tasks/task5/proposals/123/status`,
      null,
      { params: { status: 'ACCEPTED' } }
    );
  });

  it('should handle error in updateProposalStatus', async () => {
    httpClientMock.post.mockReturnValue(throwError(() => new Error('status error')));
    try {
      await firstValueFrom(service.updateProposalStatus('task6', 456, 'REJECTED'));
    } catch (e: any) {
      expect(e.message).toBe('status error');
    }
  });

  it('should get active tasks', async () => {
    const notifications: MetadataBatchProgressNotification[] = [
      {
        taskId: 't1',
        completed: 1,
        total: 2,
        message: 'Running',
        status: MetadataBatchStatus.IN_PROGRESS,
        review: false
      }
    ];
    httpClientMock.get.mockReturnValue(of(notifications));
    const result = await firstValueFrom(service.getActiveTasks());
    expect(result).toEqual(notifications);
    expect(httpClientMock.get).toHaveBeenCalledWith(`${API_CONFIG.BASE_URL}/api/metadata/tasks/active`);
  });

  it('should handle error in getActiveTasks', async () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('active error')));
    try {
      await firstValueFrom(service.getActiveTasks());
    } catch (e: any) {
      expect(e.message).toBe('active error');
    }
  });
});
