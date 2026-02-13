import {Component, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Message} from 'primeng/message';
import {Router} from '@angular/router';

import {Password} from 'primeng/password';
import {MessageService} from 'primeng/api';
import {UserService} from '../../../features/settings/user-management/user.service';
import {AuthService} from '../../service/auth.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    Button,
    FormsModule,
    Message,
    Password,
    ReactiveFormsModule,
    TranslocoDirective
  ],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.scss'
})

export class ChangePasswordComponent implements OnInit {
  currentPassword: string = '';
  newPassword: string = '';
  confirmNewPassword: string = '';
  errorMessage: string | null = null;
  successMessage: string | null = null;
  isInitialSetup: boolean = false;
  isCreatingLocal: boolean = false;

  protected userService = inject(UserService);
  protected authService = inject(AuthService);
  protected messageService = inject(MessageService);
  protected router = inject(Router);
  protected t = inject(TranslocoService);

  ngOnInit() {
    const navigation = history.state;
    if (navigation && navigation.isInitialSetup) {
      this.isInitialSetup = true;
    }
  }

  get passwordsMatch(): boolean {
    return this.newPassword === this.confirmNewPassword;
  }

  changePassword() {
    this.errorMessage = null;
    this.successMessage = null;

    if (!this.currentPassword || !this.newPassword || !this.confirmNewPassword) {
      this.errorMessage = this.t ? this.t.translate('shared.changePassword.validation.allFieldsRequired') : 'New password and confirm password fields are required.';
      return;
    }

    if (!this.passwordsMatch) {
      this.errorMessage = this.t.translate('shared.changePassword.validation.passwordsDoNotMatch');
      return;
    }

    // For OIDC users during initial setup, we should set their local password
    if (this.isInitialSetup) {
      // For OIDC users, we need to set their password which will allow them to login locally
      // This is essentially changing their user record to allow local authentication
      this.userService.getMyself().subscribe({
        next: (user) => {
          // Use changeUserPassword endpoint to set the password for this user
          this.userService.changeUserPassword(user.id, this.newPassword).subscribe({
            next: () => {
              this.successMessage = 'Local password set successfully!';
              setTimeout(() => {
                this.router.navigate(['/dashboard']);
              }, 1500);
            },
            error: (err) => {
              this.errorMessage = err.message || 'Failed to set local password.';
              this.messageService.add({
                severity: 'error',
                summary: 'Password Setup Failed',
                detail: this.errorMessage ?? 'An unknown error occurred.'
              });
            }
          });
        },
        error: () => {
          this.errorMessage = 'Failed to retrieve user information.';
          this.messageService.add({
            severity: 'error',
            summary: 'User Info Error',
            detail: this.errorMessage ?? 'An unknown error occurred.'
          });
        }
      });
    } else {
      // For non-initial setup, use the regular change password flow
      if (!this.currentPassword) {
        this.errorMessage = 'Current password is required.';
        return;
      }

      if (this.currentPassword === this.newPassword) {
        this.errorMessage = this.t.translate('shared.changePassword.validation.sameAsCurrentPassword');
        return;
      }

      this.userService.changePassword(this.currentPassword, this.newPassword).subscribe({
        next: () => {
          this.successMessage = this.t.translate('shared.changePassword.toast.success');
          this.logout(); // Log out after password change for security
        },
        error: (err) => {
          this.errorMessage = err.message;
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('shared.changePassword.toast.failedSummary'),
            detail: this.errorMessage ?? this.t.translate('shared.changePassword.toast.failedDetailDefault')
          });
        }
      });
    }
  }

  createLocalAccount() {
    this.errorMessage = null;
    this.successMessage = null;
    this.isCreatingLocal = true;

    if (!this.newPassword || !this.confirmNewPassword) {
      this.errorMessage = 'New password and confirm password fields are required.';
      this.isCreatingLocal = false;
      return;
    }
    if (!this.passwordsMatch) {
      this.errorMessage = 'New passwords do not match.';
      this.isCreatingLocal = false;
      return;
    }

    // For OIDC users, we need to set their local password
    this.userService.getMyself().subscribe({
      next: (user) => {
        // Use changeUserPassword endpoint to set the password for this user
        this.userService.changeUserPassword(user.id, this.newPassword).subscribe({
          next: () => {
            this.successMessage = 'Local password set successfully!';
            setTimeout(() => {
              this.router.navigate(['/dashboard']);
            }, 1500);
          },
          error: (err) => {
            this.errorMessage = err.message || 'Failed to set local password.';
            this.isCreatingLocal = false;
          }
        });
      },
      error: () => {
        this.errorMessage = 'Failed to retrieve user information. Cannot set local password.';
        this.isCreatingLocal = false;
      }
    });
  }

  logout() {
    this.authService.logout();
  }

  skip() {
    // Redirect to dashboard without making changes
    this.router.navigate(['/dashboard']);
  }
}
