import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {Password} from 'primeng/password';
import {User, UserService} from '../user-management/user.service';
import {MessageService} from 'primeng/api';
import {Subject} from 'rxjs';
import {filter, takeUntil} from 'rxjs/operators';
import {DynamicDialogRef} from 'primeng/dynamicdialog';

export const passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const newPassword = control.get('newPassword');
  const confirmNewPassword = control.get('confirmNewPassword');

  if (!newPassword || !confirmNewPassword) {
    return null;
  }

  return newPassword.value === confirmNewPassword.value ? null : {passwordMismatch: true};
};

@Component({
  selector: 'app-user-profile-dialog',
  imports: [
    Button,
    FormsModule,
    ReactiveFormsModule,
    InputText,
    Password
  ],
  templateUrl: './user-profile-dialog.component.html',
  styleUrls: ['./user-profile-dialog.component.scss']
})
export class UserProfileDialogComponent implements OnInit, OnDestroy {

  isEditing = false;
  currentUser: User | null = null;
  editUserData: Partial<User> = {};
  private readonly destroy$ = new Subject<void>();

  changePasswordForm: FormGroup;

  protected readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(DynamicDialogRef);

  constructor() {
    this.changePasswordForm = this.fb.group(
      {
        currentPassword: ['', Validators.required],
        newPassword: ['', [Validators.required, Validators.minLength(6)]],
        confirmNewPassword: ['', Validators.required]
      },
      {validators: passwordMatchValidator}
    );
  }

  ngOnInit(): void {
    this.userService.userState$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      takeUntil(this.destroy$)
    ).subscribe(userState => {
      this.currentUser = userState.user;
      this.resetEditForm();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  toggleEdit(): void {
    this.isEditing = !this.isEditing;
    if (this.isEditing) {
      this.resetEditForm();
    }
  }

  resetEditForm(): void {
    if (this.currentUser) {
      this.editUserData = {
        username: this.currentUser.username,
        name: this.currentUser.name,
        email: this.currentUser.email,
      };
    }
  }

  updateProfile(): void {
    if (!this.currentUser) {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'User data not available.',
      });
      return;
    }

    if (this.editUserData.name === this.currentUser.name && this.editUserData.email === this.currentUser.email) {
      this.messageService.add({severity: 'info', summary: 'Info', detail: 'No changes detected.'});
      this.isEditing = false;
      return;
    }

    this.userService.updateUser(this.currentUser.id, this.editUserData).subscribe({
      next: () => {
        this.messageService.add({severity: 'success', summary: 'Success', detail: 'Profile updated successfully'});
        this.isEditing = false;
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: err.error?.message || 'Failed to update profile',
        });
      },
    });
  }

  submitPasswordChange(): void {
    if (this.changePasswordForm.invalid) {
      this.changePasswordForm.markAllAsTouched();
      return;
    }

    const {currentPassword, newPassword} = this.changePasswordForm.value;

    this.userService.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.messageService.add({severity: 'success', summary: 'Success', detail: 'Password changed successfully'});
        this.resetPasswordForm();
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: err?.message || 'Failed to change password',
        });
      }
    });
  }

  resetPasswordForm(): void {
    this.changePasswordForm.reset();
  }

  closeDialog(): void {
    this.dialogRef.close();
  }
}
