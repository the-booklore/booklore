import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {Router} from '@angular/router';
import {of} from 'rxjs';

import {SetupComponent} from './setup.component';
import {SetupService} from './setup.service';

describe('SetupComponent confirm password', () => {
  let fixture: ComponentFixture<SetupComponent>;
  let component: SetupComponent;
  let setupServiceMock: { createAdmin: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    setupServiceMock = {
      createAdmin: vi.fn().mockReturnValue(of(void 0))
    };
    const routerMock = {
      navigate: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [SetupComponent],
      providers: [
        {provide: SetupService, useValue: setupServiceMock},
        {provide: Router, useValue: routerMock}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SetupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  const getErrorTexts = () =>
  Array.from(
    (fixture.nativeElement as HTMLElement).querySelectorAll('.field-error')
  )
    .map(el => el.textContent?.trim())
    .filter((text): text is string => Boolean(text));


  it('shows required error when confirm password is empty', () => {
    const confirmControl = component.setupForm.get('confirmPassword');
    confirmControl?.setValue('');
    confirmControl?.markAsTouched();

    fixture.detectChanges();
    expect(getErrorTexts()).toContain('Password confirmation is required');
  });

  it('shows mismatch error when confirm password does not match', () => {
    component.setupForm.get('password')?.setValue('abcdef');
    const confirmControl = component.setupForm.get('confirmPassword');
    confirmControl?.setValue('abcdeg');
    confirmControl?.markAsTouched();

    fixture.detectChanges();

    expect(getErrorTexts()).toContain('Passwords do not match');
  });

  it('hides mismatch error when confirm password matches', () => {
    component.setupForm.get('password')?.setValue('abcdef');
    const confirmControl = component.setupForm.get('confirmPassword');
    confirmControl?.setValue('abcdef');
    confirmControl?.markAsTouched();

    fixture.detectChanges();

    expect(getErrorTexts()).not.toContain('Passwords do not match');
  });

  it('does not submit when confirm password is missing', () => {
    component.setupForm.patchValue({
      username: 'admin',
      name: 'Admin User',
      email: 'admin@example.com',
      password: '123456',
      confirmPassword: ''
    });

    component.onSubmit();

    expect(component.setupForm.invalid).toBe(true);
    expect(setupServiceMock.createAdmin).not.toHaveBeenCalled();
  });

  it('does not submit when confirm password does not match', () => {
    component.setupForm.patchValue({
      username: 'admin',
      name: 'Admin User',
      email: 'admin@example.com',
      password: '123456',
      confirmPassword: '123457'
    });

    component.onSubmit();

    expect(component.setupForm.invalid).toBe(true);
    expect(setupServiceMock.createAdmin).not.toHaveBeenCalled();
  });

  it('submits when form is valid and excludes confirmPassword from payload', () => {
    component.setupForm.patchValue({
      username: 'admin',
      name: 'Admin User',
      email: 'admin@example.com',
      password: '123456',
      confirmPassword: '123456'
    });

    component.onSubmit();

    expect(component.setupForm.valid).toBe(true);
    expect(setupServiceMock.createAdmin).toHaveBeenCalledWith({
      username: 'admin',
      name: 'Admin User',
      email: 'admin@example.com',
      password: '123456'
    });
  });
});
