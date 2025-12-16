import {Component, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Message} from 'primeng/message';
import {Router} from '@angular/router';

import {Password} from 'primeng/password';
import {MessageService} from 'primeng/api';
import {UserService} from '../../../features/settings/user-management/user.service';
import {AuthService} from '../../service/auth.service';

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
  isInitialSetup: boolean = false;

  protected userService = inject(UserService);
  protected authService = inject(AuthService);
  protected messageService = inject(MessageService);
  protected router = inject(Router);

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
      this.errorMessage = 'All fields are required.';
      return;
    }

    if (!this.passwordsMatch) {
      this.errorMessage = 'New passwords do not match.';
      return;
    }

    if (this.currentPassword === this.newPassword) {
      this.errorMessage = 'New password cannot be the same as the current password.';
      return;
    }

    this.userService.changePassword(this.currentPassword, this.newPassword).subscribe({
      next: () => {
        this.successMessage = 'Password changed successfully!';
        if (this.isInitialSetup) {
          setTimeout(() => {
            this.router.navigate(['/dashboard']);
          }, 1500);
        } else {
          this.logout();
        }
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

  skip() {
    this.router.navigate(['/dashboard']);
  }
}
