import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {of} from 'rxjs';

import {MetadataProgressService} from './metadata-progress.service';
import {MetadataBatchProgressNotification, MetadataBatchStatus} from '../model/metadata-batch-progress.model';
import {MetadataTaskService} from '../../features/book/service/metadata-task';
import {UserService} from '../../features/settings/user-management/user.service';

describe('MetadataProgressService', () => {
  let service: MetadataProgressService;
  let metadataTaskServiceMock: any;
  let userServiceMock: any;

  const mockUserState = {
    user: {
      permissions: {
        admin: true,
        canEditMetadata: true
      }
    }
  };

  const mockTasks: MetadataBatchProgressNotification[] = [
    {taskId: '1', completed: 10, total: 100, message: '', status: MetadataBatchStatus.IN_PROGRESS, review: false},
    {taskId: '2', completed: 100, total: 100, message: '', status: MetadataBatchStatus.COMPLETED, review: false}
  ];

  beforeEach(() => {
    metadataTaskServiceMock = {
      getActiveTasks: vi.fn().mockReturnValue(of(mockTasks))
    };
    userServiceMock = {
      userState$: of(mockUserState)
    };

    TestBed.configureTestingModule({
      providers: [
        MetadataProgressService,
        {provide: MetadataTaskService, useValue: metadataTaskServiceMock},
        {provide: UserService, useValue: userServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(
      injector,
      () => TestBed.inject(MetadataProgressService)
    );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should initialize and fetch active tasks if user has permissions', () => {
    expect(metadataTaskServiceMock.getActiveTasks).toHaveBeenCalled();
  });

  it('should not fetch active tasks if user has no permissions', () => {
    userServiceMock.userState$ = of({user: {permissions: {admin: false, canEditMetadata: false}}});
    metadataTaskServiceMock.getActiveTasks.mockClear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        MetadataProgressService,
        {provide: MetadataTaskService, useValue: metadataTaskServiceMock},
        {provide: UserService, useValue: userServiceMock}
      ]
    });
    const injector = TestBed.inject(EnvironmentInjector);
    TestBed.inject(MetadataProgressService);
    expect(metadataTaskServiceMock.getActiveTasks).not.toHaveBeenCalled();
  });

  it('should handle incoming progress and update state', () => {
    const progress: MetadataBatchProgressNotification = {
      taskId: '3',
      completed: 50,
      total: 100,
      message: '',
      status: MetadataBatchStatus.IN_PROGRESS,
      review: false
    };
    let update: MetadataBatchProgressNotification | undefined;
    const sub = service.progressUpdates$.subscribe(val => update = val);
    service.handleIncomingProgress(progress);
    expect(update).toEqual(progress);
    expect(service.getActiveTasks()['3']).toEqual(progress);
    sub.unsubscribe();
  });

  it('should clear task from active tasks', () => {
    const progress: MetadataBatchProgressNotification = {
      taskId: '4',
      completed: 20,
      total: 100,
      message: '',
      status: MetadataBatchStatus.IN_PROGRESS,
      review: false
    };
    service.handleIncomingProgress(progress);
    expect(service.getActiveTasks()['4']).toBeDefined();
    service.clearTask('4');
    expect(service.getActiveTasks()['4']).toBeUndefined();
  });

  it('should complete all subjects on destroy', () => {
    const spy = vi.spyOn(service['progressUpdatesSubject'], 'complete');
    service.ngOnDestroy();
    expect(spy).toHaveBeenCalled();
  });
});

describe('MetadataProgressService - API Contract Tests', () => {
  let service: MetadataProgressService;
  let metadataTaskServiceMock: any;
  let userServiceMock: any;

  beforeEach(() => {
    metadataTaskServiceMock = {
      getActiveTasks: vi.fn().mockReturnValue(of([]))
    };
    userServiceMock = {
      userState$: of({
        user: {
          permissions: {
            admin: true,
            canEditMetadata: true
          }
        }
      })
    };

    TestBed.configureTestingModule({
      providers: [
        MetadataProgressService,
        {provide: MetadataTaskService, useValue: metadataTaskServiceMock},
        {provide: UserService, useValue: userServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(
      injector,
      () => TestBed.inject(MetadataProgressService)
    );
  });

  describe('MetadataBatchProgressNotification contract', () => {
    it('should require taskId, completed, total, message, status, and review fields', () => {
      const progress: MetadataBatchProgressNotification = {
        taskId: 'abc',
        completed: 42,
        total: 100,
        message: '',
        status: MetadataBatchStatus.IN_PROGRESS,
        review: false
      };
      expect(progress).toHaveProperty('taskId');
      expect(progress).toHaveProperty('completed');
      expect(progress).toHaveProperty('total');
      expect(progress).toHaveProperty('message');
      expect(progress).toHaveProperty('status');
      expect(progress).toHaveProperty('review');
    });
  });

  describe('Active tasks structure', () => {
    it('should return an object with taskId keys and progress notification values', () => {
      const progress: MetadataBatchProgressNotification = {
        taskId: 'xyz',
        completed: 99,
        total: 100,
        message: '',
        status: MetadataBatchStatus.COMPLETED,
        review: false
      };
      service.handleIncomingProgress(progress);
      const active = service.getActiveTasks();
      expect(active).toHaveProperty('xyz');
      expect(active['xyz']).toEqual(progress);
    });
  });

  describe('Observable contract', () => {
    it('should emit progress updates on progressUpdates$', () => {
      const progress: MetadataBatchProgressNotification = {
        taskId: 'emit',
        completed: 1,
        total: 100,
        message: '',
        status: MetadataBatchStatus.IN_PROGRESS,
        review: false
      };
      let emitted: MetadataBatchProgressNotification | undefined;
      const sub = service.progressUpdates$.subscribe(val => {
        emitted = val;
      });
      service.handleIncomingProgress(progress);
      expect(emitted).toEqual(progress);
      sub.unsubscribe();
    });

    it('should emit active tasks on activeTasks$', () => {
      const progress: MetadataBatchProgressNotification = {
        taskId: 'active',
        completed: 2,
        total: 100,
        message: '',
        status: MetadataBatchStatus.IN_PROGRESS,
        review: false
      };
      let emitted: { [taskId: string]: MetadataBatchProgressNotification } | undefined;
      const sub = service.activeTasks$.subscribe(val => {
        emitted = val;
      });
      service.handleIncomingProgress(progress);
      expect(emitted && emitted['active']).toEqual(progress);
      sub.unsubscribe();
    });
  });

  describe('Permissions contract', () => {
    it('should only fetch tasks if user has admin or canEditMetadata', () => {
      userServiceMock.userState$ = of({user: {permissions: {admin: false, canEditMetadata: false}}});
      metadataTaskServiceMock.getActiveTasks.mockClear();
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          MetadataProgressService,
          {provide: MetadataTaskService, useValue: metadataTaskServiceMock},
          {provide: UserService, useValue: userServiceMock}
        ]
      });
      TestBed.inject(MetadataProgressService);
      expect(metadataTaskServiceMock.getActiveTasks).not.toHaveBeenCalled();
    });
  });
});
