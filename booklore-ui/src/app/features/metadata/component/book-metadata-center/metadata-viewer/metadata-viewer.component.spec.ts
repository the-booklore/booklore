import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetadataViewerComponent } from './metadata-viewer.component';
import { BookService } from '../../../../book/service/book.service';
import { BookDialogHelperService } from '../../../../book/components/book-browser/book-dialog-helper.service';
import { EmailService } from '../../../../settings/email-v2/email.service';
import { MessageService, ConfirmationService } from 'primeng/api';
import { TaskHelperService } from '../../../../settings/task-management/task-helper.service';
import { UrlHelperService } from '../../../../../shared/service/url-helper.service';
import { UserService } from '../../../../settings/user-management/user.service';
import { Router, ActivatedRoute } from '@angular/router';
import { BookNavigationService } from '../../../../book/service/book-navigation.service';
import { BookMetadataHostService } from '../../../../../shared/service/book-metadata-host.service';
import { AppSettingsService } from '../../../../../shared/service/app-settings.service';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

describe('MetadataViewerComponent', () => {
  let component: MetadataViewerComponent;
  let fixture: ComponentFixture<MetadataViewerComponent>;
  let routerSpy: any;
  let userServiceSpy: any;
  let appSettingsServiceSpy: any;
  let bookServiceSpy: any;

  function createSpyObj(name: string, methods: string[]) {
    const obj: any = {};
    for (const method of methods) {
      obj[method] = vi.fn();
    }
    return obj;
  }

  beforeEach(async () => {
    routerSpy = createSpyObj('Router', ['navigate']);
    userServiceSpy = {
      userState$: of({ user: { userSettings: { metadataCenterViewMode: 'route' }, permissions: {} }, loaded: true })
    };
    appSettingsServiceSpy = {
      appSettings$: of({})
    };
    bookServiceSpy = createSpyObj('BookService', ['getBooksInSeries', 'downloadFile', 'downloadAdditionalFile', 'deleteAdditionalFile', 'updateBookReadStatus', 'resetProgress', 'updatePersonalRating', 'resetPersonalRating', 'updateDateFinished', 'deleteBooks', 'readBook']);

    const looseMocks = {
      provide: [
        { provide: Router, useValue: routerSpy },
        { provide: UserService, useValue: userServiceSpy },
        { provide: AppSettingsService, useValue: appSettingsServiceSpy },
        { provide: BookService, useValue: bookServiceSpy },
        { provide: BookDialogHelperService, useValue: createSpyObj('BookDialogHelperService', ['openMetadataFetchOptionsDialog', 'openAdditionalFileUploaderDialog', 'openFileMoverDialog', 'openCustomSendDialog', 'openShelfAssignerDialog']) },
        { provide: EmailService, useValue: createSpyObj('EmailService', ['emailBookQuick']) },
        { provide: MessageService, useValue: createSpyObj('MessageService', ['add']) },
        { provide: ConfirmationService, useValue: createSpyObj('ConfirmationService', ['confirm']) },
        { provide: TaskHelperService, useValue: createSpyObj('TaskHelperService', ['refreshMetadataTask']) },
        { provide: UrlHelperService, useValue: createSpyObj('UrlHelperService', []) },
        { provide: BookNavigationService, useValue: { ...createSpyObj('BookNavigationService', ['canNavigatePrevious', 'canNavigateNext', 'getPreviousBookId', 'getNextBookId', 'updateCurrentBook', 'getCurrentPosition']), getNavigationState: () => of(null) } },
        { provide: BookMetadataHostService, useValue: createSpyObj('BookMetadataHostService', ['switchBook']) },
        { provide: ActivatedRoute, useValue: {} }
      ]
    };

    await TestBed.configureTestingModule({
      imports: [MetadataViewerComponent],
      providers: looseMocks.provide
    }).compileComponents();

    fixture = TestBed.createComponent(MetadataViewerComponent);
    component = fixture.componentInstance;
  });

  it('should map PDF file type to PDF filter', () => {
    component.goToFileType('test.pdf');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/all-books'], expect.objectContaining({
      queryParams: expect.objectContaining({
        filter: 'bookType:PDF'
      })
    }));
  });

  it('should map CBR file type to CBX filter', () => {
    component.goToFileType('comic.cbr');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/all-books'], expect.objectContaining({
      queryParams: expect.objectContaining({
        filter: 'bookType:CBX'
      })
    }));
  });

  it('should map CBZ file type to CBX filter', () => {
    component.goToFileType('comic.cbz');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/all-books'], expect.objectContaining({
      queryParams: expect.objectContaining({
        filter: 'bookType:CBX'
      })
    }));
  });

  it('should map CB7 file type to CBX filter', () => {
    component.goToFileType('comic.cb7');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/all-books'], expect.objectContaining({
      queryParams: expect.objectContaining({
        filter: 'bookType:CBX'
      })
    }));
  });

  it('should map EPUB file type to EPUB filter', () => {
    component.goToFileType('book.epub');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/all-books'], expect.objectContaining({
      queryParams: expect.objectContaining({
        filter: 'bookType:EPUB'
      })
    }));
  });
});
