import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {BookTableComponent} from './book-table.component';
import {BookService} from '../../../service/book.service';
import {UserService} from '../../../../settings/user-management/user.service';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {MessageService} from 'primeng/api';
import {ReadStatusHelper} from '../../../helpers/read-status.helper';
import {BehaviorSubject, of} from 'rxjs';
import {provideRouter} from '@angular/router';

describe('BookTableComponent', () => {
  let component: BookTableComponent;
  let fixture: ComponentFixture<BookTableComponent>;
  let userService: any;
  let bookService: any;
  let urlHelper: any;
  let messageService: any;
  let readStatusHelper: any;
  let userStateSubject: BehaviorSubject<any>;

  beforeEach(async () => {
    userStateSubject = new BehaviorSubject({
      loaded: true,
      user: {
        id: 1,
        userSettings: {
          metadataCenterViewMode: 'route'
        }
      }
    });

    userService = {
      userState$: userStateSubject.asObservable(),
      getCurrentUser: vi.fn().mockReturnValue(userStateSubject.value.user)
    };

    bookService = {
      getBooksByIdsFromState: vi.fn().mockReturnValue([]),
      toggleAllLock: vi.fn().mockReturnValue(of({}))
    };

    urlHelper = {
      getBookUrl: vi.fn().mockReturnValue('/book/1'),
      getThumbnailUrl: vi.fn().mockReturnValue('thumb.jpg'),
      filterBooksBy: vi.fn().mockReturnValue('/filter')
    };

    messageService = {
      add: vi.fn()
    };

    readStatusHelper = {
      getReadStatusIcon: vi.fn(),
      getReadStatusClass: vi.fn(),
      getReadStatusTooltip: vi.fn(),
      shouldShowStatusIcon: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [BookTableComponent],
      providers: [
        provideRouter([]),
        { provide: UserService, useValue: userService },
        { provide: BookService, useValue: bookService },
        { provide: UrlHelperService, useValue: urlHelper },
        { provide: MessageService, useValue: messageService },
        { provide: ReadStatusHelper, useValue: readStatusHelper }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BookTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should memoize star color results', () => {
    // Accessing private for test verification
    const cache = (component as any).starColorCache;
    expect(cache.size).toBe(0);

    const color1 = component.getStarColor(4.8);
    expect(cache.size).toBe(1);
    
    const color2 = component.getStarColor(4.8);
    expect(cache.size).toBe(1); // Should reuse cached value
    expect(color1).toBe(color2);

    component.getStarColor(2.0);
    expect(cache.size).toBe(2);
  });

  it('should correctly identify sortable fields using Set-based lookup', () => {
    component.validSortFields = ['title', 'authors'];
    
    expect(component.isSortable('title')).toBe(true);
    expect(component.isSortable('authors')).toBe(true);
    expect(component.isSortable('publisher')).toBe(false);
    
    // Check internal Set update
    const set = (component as any).validSortFieldsSet;
    expect(set.has('title')).toBe(true);
    expect(set.size).toBe(2);
  });

  it('should format file size correctly', () => {
    expect(component.formatFileSize(1024)).toBe('1.0 MB');
    expect(component.formatFileSize(1536)).toBe('1.5 MB');
    expect(component.formatFileSize(512)).toBe('0.50 MB');
    expect(component.formatFileSize(undefined)).toBe('-');
  });

  it('should track by book ID for performance', () => {
    const book = { id: 123 } as any;
    expect(component.trackByBookId(0, book)).toBe(123);
  });
});
