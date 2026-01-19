import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of} from 'rxjs';
import {MessageService} from 'primeng/api';
import {DynamicDialogRef} from 'primeng/dynamicdialog';

import {CreateUserDialogComponent} from './create-user-dialog.component';
import {LibraryService} from '../../../book/service/library.service';
import {UserService} from '../user.service';
import {Library} from '../../../book/model/library.model';

describe('CreateUserDialogComponent confirm password', () => {
  let fixture: ComponentFixture<CreateUserDialogComponent>;
  let component: CreateUserDialogComponent;
  let libraryServiceMock: { getLibrariesFromState: ReturnType<typeof vi.fn> };
  let userServiceMock: { createUser: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let dialogRefMock: { close: ReturnType<typeof vi.fn> };
  let libraries: Library[];

  beforeEach(async () => {
    if (!globalThis.navigator) {
      Object.defineProperty(globalThis, 'navigator', {
        value: {userAgent: 'node'},
        configurable: true
      });
    }

    libraries = [
      {
        id: 1,
        name: 'Main Library',
        icon: 'pi pi-book',
        watch: false,
        paths: []
      }
    ];

    libraryServiceMock = {
      getLibrariesFromState: vi.fn().mockReturnValue(libraries)
    };
    userServiceMock = {
      createUser: vi.fn().mockReturnValue(of(void 0))
    };
    messageServiceMock = {
      add: vi.fn()
    };
    dialogRefMock = {
      close: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [CreateUserDialogComponent],
      providers: [
        {provide: LibraryService, useValue: libraryServiceMock},
        {provide: UserService, useValue: userServiceMock},
        {provide: MessageService, useValue: messageServiceMock},
        {provide: DynamicDialogRef, useValue: dialogRefMock},
      ]
    }).compileComponents();
    console.log('doc === window.document', document === window.document);
    console.log('body ownerDocument === document',document.body?.ownerDocument === document);
    fixture = TestBed.createComponent(CreateUserDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  const getErrorTexts = () =>
    Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.field-error')
    )
      .map(el => el.textContent?.trim())
      .filter((text): text is string => Boolean(text));

  const setValidForm = () => {
    component.userForm.patchValue({
      name: 'Test User',
      email: 'test@example.com',
      username: 'testuser',
      password: '123456',
      confirmPassword: '123456',
      selectedLibraries: [libraries[0]]
    });
  };

  it('shows required error when confirm password is empty', () => {
    const confirmControl = component.userForm.get('confirmPassword');
    confirmControl?.setValue('');
    confirmControl?.markAsTouched();

    fixture.detectChanges();

    expect(getErrorTexts()).toContain('Password confirmation is required');
  });

  it('shows mismatch error when confirm password does not match', () => {
    component.userForm.get('password')?.setValue('abcdef');
    const confirmControl = component.userForm.get('confirmPassword');
    confirmControl?.setValue('abcdeg');
    confirmControl?.markAsTouched();

    fixture.detectChanges();

    expect(getErrorTexts()).toContain('Passwords do not match.');
  });

  it('hides mismatch error when confirm password matches', () => {
    component.userForm.get('password')?.setValue('abcdef');
    const confirmControl = component.userForm.get('confirmPassword');
    confirmControl?.setValue('abcdef');
    confirmControl?.markAsTouched();

    fixture.detectChanges();

    expect(getErrorTexts()).not.toContain('Passwords do not match.');
  });

  it('does not submit when confirm password does not match', () => {
    component.userForm.patchValue({
      name: 'Test User',
      email: 'test@example.com',
      username: 'testuser',
      password: '123456',
      confirmPassword: '123457',
      selectedLibraries: [libraries[0]]
    });

    component.createUser();

    expect(component.userForm.invalid).toBe(true);
    expect(userServiceMock.createUser).not.toHaveBeenCalled();
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({severity: 'warn'})
    );
  });

  it('submits when form is valid and excludes confirmPassword from payload', () => {
    setValidForm();

    component.createUser();

    expect(component.userForm.valid).toBe(true);
    expect(userServiceMock.createUser).toHaveBeenCalled();

    const callArg = userServiceMock.createUser.mock.calls[0]?.[0];
    expect(callArg.confirmPassword).toBeUndefined();
    expect(callArg.selectedLibraries).toEqual([1]);
    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });
});
