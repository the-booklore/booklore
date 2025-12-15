import {Component, DestroyRef, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Message} from 'primeng/message';

import {Password} from 'primeng/password';
import {MessageService} from 'primeng/api';
import {UserService} from '../../../features/settings/user-management/user.service';
import {AuthService} from '../../service/auth.service';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    Button,
    FormsModule,
    Message,
    Password,
    ReactiveFormsModule
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
  isOidcUser: boolean = false;

  protected userService = inject(UserService);
  protected authService = inject(AuthService);
  protected messageService = inject(MessageService);
  private destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.userService.userState$.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(state => {
      if (state.user) {
        this.isOidcUser = state.user.provisioningMethod === 'OIDC';
      }
    });
  }

  get passwordsMatch(): boolean {
    return this.newPassword === this.confirmNewPassword;
  }

  changePassword() {
    this.errorMessage = null;
    this.successMessage = null;

    if ((!this.isOidcUser && !this.currentPassword) || !this.newPassword || !this.confirmNewPassword) {
      this.errorMessage = 'All fields are required.';
      return;
    }

    if (!this.passwordsMatch) {
      this.errorMessage = 'New passwords do not match.';
      return;
    }

    if (!this.isOidcUser && this.currentPassword === this.newPassword) {
      this.errorMessage = 'New password cannot be the same as the current password.';
      return;
    }

    this.userService.changePassword(this.isOidcUser ? '' : this.currentPassword, this.newPassword).subscribe({
      next: () => {
        this.successMessage = 'Password changed successfully!';
        this.logout();
      },
      error: (err) => {
        this.errorMessage = err.message;
        this.messageService.add({
          severity: 'error',
          summary: 'Password Change Failed',
          detail: this.errorMessage ?? 'An unknown error occurred.'
        });
      }
    });
  }

  logout() {
    this.authService.logout();
  }
}
