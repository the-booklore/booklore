import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {of, Subject, take} from 'rxjs';
import {IconPickerService, IconSelection} from './icon-picker.service';
import {DialogLauncherService} from '../services/dialog-launcher.service';

describe('IconPickerService', () => {
  let service: IconPickerService;
  let dialogLauncherServiceMock: any;

  beforeEach(() => {
    dialogLauncherServiceMock = {
      openIconPickerDialog: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        IconPickerService,
        {provide: DialogLauncherService, useValue: dialogLauncherServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(IconPickerService));
  });

  it('should open icon picker dialog and return selection observable', () => {
    const selection: IconSelection = {type: 'PRIME_NG', value: 'pi pi-book'};
    const subject = new Subject<IconSelection>();
    dialogLauncherServiceMock.openIconPickerDialog.mockReturnValue({onClose: subject.asObservable()});
    let result: IconSelection | undefined;
    service.open().pipe(take(1)).subscribe(sel => {
      result = sel;
    });
    subject.next(selection);
    expect(result).toEqual(selection);
  });

  it('should call openIconPickerDialog on open()', () => {
    dialogLauncherServiceMock.openIconPickerDialog.mockReturnValue({onClose: of({type: 'CUSTOM_SVG', value: '<svg></svg>'})});
    service.open().pipe(take(1)).subscribe();
    expect(dialogLauncherServiceMock.openIconPickerDialog).toHaveBeenCalled();
  });
});

describe('IconPickerService - API Contract Tests', () => {
  let service: IconPickerService;
  let dialogLauncherServiceMock: any;

  beforeEach(() => {
    dialogLauncherServiceMock = {
      openIconPickerDialog: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        IconPickerService,
        {provide: DialogLauncherService, useValue: dialogLauncherServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(IconPickerService));
  });

  describe('IconSelection interface contract', () => {
    it('should have required fields', () => {
      const selection: IconSelection = {type: 'PRIME_NG', value: 'pi pi-user'};
      expect(selection).toHaveProperty('type');
      expect(selection).toHaveProperty('value');
      expect(typeof selection.type).toBe('string');
      expect(typeof selection.value).toBe('string');
    });

    it('should only allow valid type values', () => {
      const validTypes = ['PRIME_NG', 'CUSTOM_SVG'];
      const selection: IconSelection = {type: 'PRIME_NG', value: 'pi pi-user'};
      expect(validTypes).toContain(selection.type);
    });
  });

  describe('API contract', () => {
    it('should expose open as a function', () => {
      expect(typeof service.open).toBe('function');
    });

    it('should return Observable<IconSelection> from open()', () => {
      const selection: IconSelection = {type: 'CUSTOM_SVG', value: '<svg></svg>'};
      dialogLauncherServiceMock.openIconPickerDialog.mockReturnValue({onClose: of(selection)});
      let result: IconSelection | undefined;
      service.open().pipe(take(1)).subscribe(sel => {
        result = sel;
      });
      expect(result).toEqual(selection);
    });
  });
});
