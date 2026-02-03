import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {ActivatedRoute, Router} from '@angular/router';
import {Observable, of} from 'rxjs';
import {MessageService} from 'primeng/api';

import {MetadataManagerComponent} from './metadata-manager.component';
import {BookService} from '../../../book/service/book.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';

describe('MetadataManagerComponent singular labels', () => {
  let fixture: ComponentFixture<MetadataManagerComponent>;
  let component: MetadataManagerComponent;
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let bookServiceMock: {
    bookState$: Observable<{ loaded: boolean }>;
    consolidateMetadata: ReturnType<typeof vi.fn>;
    deleteMetadata: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    if (!globalThis.ResizeObserver) {
      globalThis.ResizeObserver = class {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();
      };
    }

    messageServiceMock = {
      add: vi.fn()
    };

    bookServiceMock = {
      bookState$: of({loaded: false}),
      consolidateMetadata: vi.fn().mockReturnValue(of(void 0)),
      deleteMetadata: vi.fn().mockReturnValue(of(void 0))
    };

    const routerMock = {
      navigate: vi.fn()
    };

    const routeMock = {
      queryParams: of({tab: 'series'})
    };

    const pageTitleServiceMock = {
      setPageTitle: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [MetadataManagerComponent, NoopAnimationsModule],
      providers: [
        {provide: BookService, useValue: bookServiceMock},
        {provide: MessageService, useValue: messageServiceMock},
        {provide: Router, useValue: routerMock},
        {provide: ActivatedRoute, useValue: routeMock},
        {provide: PageTitleService, useValue: pageTitleServiceMock}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MetadataManagerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('uses the correct singular in merge validation warning details for all metadata types', () => {
    const cases = component.tabConfigs
      .filter(tab => component['isSingleValueField'](tab.type))
      .map(tab => ({
        type: tab.type,
        singular: component.pluralize.singular(tab.type)
      }));

    for (const {type, singular} of cases) {
      messageServiceMock.add.mockClear();

      component.currentMergeType = type;
      component.mergeTarget = 'One, Two';

      component.confirmMerge();

      const expectedDetail = `Each book can only have one ${singular}. Please enter only one target value to standardize to.`;
      expect(messageServiceMock.add).toHaveBeenCalledWith(
        expect.objectContaining({detail: expectedDetail})
      );
    }
  });

  it('uses the correct singular in rename validation warning details for all metadata types', () => {
    const cases = component.tabConfigs
      .filter(tab => component['isSingleValueField'](tab.type))
      .map(tab => ({
        type: tab.type,
        singular: component.pluralize.singular(tab.type)
      }));

    for (const {type, singular} of cases) {
      messageServiceMock.add.mockClear();

      component.currentMergeType = type;
      component.currentRenameItem = {
        value: 'Example',
        count: 1,
        bookIds: [],
        selected: false
      };
      component.renameTarget = 'One, Two';

      component.confirmRename();

      const expectedDetail = `Each book can only have one ${singular}. Please enter only one value.`;
      expect(messageServiceMock.add).toHaveBeenCalledWith(
        expect.objectContaining({detail: expectedDetail})
      );
    }
  });

  it('uses the correct singular in delete dialog text and success message for every metadata type', () => {
    const cases = component.tabConfigs.map(tab => ({
      type: tab.type,
      singular: component.pluralize.singular(tab.type)
    }));

    for (const {type, singular} of cases) {
      messageServiceMock.add.mockClear();

      component.currentMergeType = type;
      component.currentDeleteItem = {
        value: 'Example',
        count: 1,
        bookIds: [],
        selected: false
      };
      component.showDeleteDialog = true;

      fixture.detectChanges();

      const deleteInfoText = (fixture.nativeElement as HTMLElement)
        .querySelector('.delete-info')
        ?.textContent;

      expect(deleteInfoText).toContain(`The ${singular} will be removed from all affected books`);

      component.confirmDelete();

      const expectedDetail = `Successfully deleted 1 ${singular}. 1 book updated.`;
      expect(messageServiceMock.add).toHaveBeenCalledWith(
        expect.objectContaining({detail: expectedDetail})
      );
    }
  });
});
