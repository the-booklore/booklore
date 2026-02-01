import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MetadataViewerComponent} from './metadata-viewer.component';
import {BookService} from '../../../../../book/service/book.service';
import {BookDialogHelperService} from '../../../../../book/service/book-dialog-helper.service';
import {BookNavigationService} from '../../../../../book/service/book-navigation.service';
import {MetadataHostService} from '../../../../service/metadata-host.service';
import {ActivatedRoute, Router} from '@angular/router';
import {ConfirmationService, MessageService} from 'primeng/api';
import {Book} from '../../../../../book/model/book.model';

describe('MetadataViewerComponent', () => {
  let component: MetadataViewerComponent;
  let fixture: ComponentFixture<MetadataViewerComponent>;

  const mockBookService = {
    updateDateFinished: vi.fn(),
  };

  const mockBookDialogHelperService = {
    openFileMoverDialog: vi.fn(),
  };

  const mockBookNavigationService = {
    canNavigatePrevious: vi.fn(),
    canNavigateNext: vi.fn(),
    getPreviousBookId: vi.fn(),
    getNextBookId: vi.fn(),
    getCurrentPosition: vi.fn(),
  };

  const mockMetadataHostService = {
    switchBook: vi.fn(),
  };

  const mockRouter = {
    navigate: vi.fn(),
  };

  const mockMessageService = {
    add: vi.fn(),
  };

  const mockConfirmationService = {
    confirm: vi.fn(),
  };

  const mockActivatedRoute = {
    snapshot: {
      paramMap: {
        get: () => '1',
      },
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MetadataViewerComponent],
      providers: [
        { provide: BookService, useValue: mockBookService },
        { provide: BookDialogHelperService, useValue: mockBookDialogHelperService },
        { provide: BookNavigationService, useValue: mockBookNavigationService },
        { provide: MetadataHostService, useValue: mockMetadataHostService },
        { provide: Router, useValue: mockRouter },
        { provide: MessageService, useValue: mockMessageService },
        { provide: ConfirmationService, useValue: mockConfirmationService },
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MetadataViewerComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display Hardcover rating when present', () => {
    const book: Book = {
      id: 1,
      title: 'Test Book',
      filePath: 'test.cbz',
      metadata: {
        title: 'Test Book',
        hardcoverId: 'test-hc-id',
        hardcoverRating: 4.0,
        hardcoverReviewCount: 10,
        hardcoverBookId: 100
      }
    };

    component.book = book;
    fixture.detectChanges();

    const tooltip = component.getRatingTooltip(book, 'hardcover');
    expect(tooltip).toBe('★ 4 | 10 reviews');

    const percent = component.getRatingPercent(book.metadata?.hardcoverRating);
    expect(percent).toBe(80);
  });
});
