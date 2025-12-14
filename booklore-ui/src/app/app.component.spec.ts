import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { AuthInitializationService } from './core/security/auth-initialization-service';
import { of } from 'rxjs';
import { BookService } from './features/book/service/book.service';
import { RxStompService } from './shared/websocket/rx-stomp.service';
import { NotificationEventService } from './shared/websocket/notification-event.service';
import { MetadataProgressService } from './shared/service/metadata-progress-service';
import { BookdropFileService } from './features/bookdrop/service/bookdrop-file.service';
import { TaskService } from './features/settings/task-management/task.service';
import { AppConfigService } from './shared/service/app-config.service';
import { signal } from '@angular/core';

describe('AppComponent', () => {
  let authInitSpy: jasmine.SpyObj<AuthInitializationService>;
  let bookServiceSpy: jasmine.SpyObj<BookService>;
  let rxStompServiceSpy: jasmine.SpyObj<RxStompService>;
  let notificationEventServiceSpy: jasmine.SpyObj<NotificationEventService>;
  let metadataProgressServiceSpy: jasmine.SpyObj<MetadataProgressService>;
  let bookdropFileServiceSpy: jasmine.SpyObj<BookdropFileService>;
  let taskServiceSpy: jasmine.SpyObj<TaskService>;
  let appConfigServiceSpy: jasmine.SpyObj<AppConfigService>;

  beforeEach(async () => {
    authInitSpy = jasmine.createSpyObj('AuthInitializationService', ['markAsInitialized'], {
      initialized$: of(true)
    });
    bookServiceSpy = jasmine.createSpyObj('BookService', [
      'handleNewlyCreatedBook',
      'handleBookUpdate',
      'handleRemovedBookIds',
      'handleMultipleBookUpdates'
    ]);
    rxStompServiceSpy = jasmine.createSpyObj('RxStompService', ['watch']);
    rxStompServiceSpy.watch.and.returnValue(of({ body: '{}' } as any));
    
    notificationEventServiceSpy = jasmine.createSpyObj('NotificationEventService', ['handleNewNotification']);
    metadataProgressServiceSpy = jasmine.createSpyObj('MetadataProgressService', ['handleIncomingProgress']);
    bookdropFileServiceSpy = jasmine.createSpyObj('BookdropFileService', ['handleIncomingFile']);
    taskServiceSpy = jasmine.createSpyObj('TaskService', ['handleTaskProgress']);
    appConfigServiceSpy = jasmine.createSpyObj('AppConfigService', [], { appState: signal({}) });

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        MessageService,
        { provide: AuthInitializationService, useValue: authInitSpy },
        { provide: BookService, useValue: bookServiceSpy },
        { provide: RxStompService, useValue: rxStompServiceSpy },
        { provide: NotificationEventService, useValue: notificationEventServiceSpy },
        { provide: MetadataProgressService, useValue: metadataProgressServiceSpy },
        { provide: BookdropFileService, useValue: bookdropFileServiceSpy },
        { provide: TaskService, useValue: taskServiceSpy },
        { provide: AppConfigService, useValue: appConfigServiceSpy }
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});
