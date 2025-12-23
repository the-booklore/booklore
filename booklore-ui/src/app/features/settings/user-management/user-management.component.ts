import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button, ButtonDirective} from 'primeng/button';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {TableModule} from 'primeng/table';
import {LowerCasePipe, TitleCasePipe} from '@angular/common';
import {User, UserService} from './user.service';
import {MessageService} from 'primeng/api';
import {Checkbox} from 'primeng/checkbox';
import {MultiSelect} from 'primeng/multiselect';
import {Library} from '../../book/model/library.model';
import {LibraryService} from '../../book/service/library.service';
import {Dialog} from 'primeng/dialog';
import {Password} from 'primeng/password';
import {filter, take, takeUntil} from 'rxjs/operators';
import {Subject} from 'rxjs';
import {Tooltip} from 'primeng/tooltip';
import {DialogLauncherService} from '../../../shared/services/dialog-launcher.service';

@Component({
  selector: 'app-user-management',
  imports: [
    FormsModule,
    Button,
    TableModule,
    Checkbox,
    MultiSelect,
    Dialog,
    Password,
    LowerCasePipe,
    TitleCasePipe,
    Tooltip,
    ButtonDirective
  ],
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.scss'],
})
export class UserManagementComponent implements OnInit, OnDestroy {
  ref: DynamicDialogRef | undefined | null;
  private dialogLauncherService = inject(DialogLauncherService);
  private userService = inject(UserService);
  private libraryService = inject(LibraryService);
  private messageService = inject(MessageService);
  private readonly destroy$ = new Subject<void>();

  users: User[] = [];
  currentUser: User | null = null;
  editingLibraryIds: number[] = [];
  allLibraries: Library[] = [];
  expandedRows: { [key: string]: boolean } = {};

  isPasswordDialogVisible = false;
  selectedUser: User | null = null;
  newPassword = '';
  confirmNewPassword = '';
  passwordError = '';
  isAdmin = false;

  ngOnInit() {
    this.loadUsers();

    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        take(1),
        takeUntil(this.destroy$)
      )
      .subscribe(userState => {
        this.currentUser = userState.user;
        this.isAdmin = userState.user?.permissions?.admin || false;
      });

    this.libraryService.libraryState$
      .pipe(
        filter(state => !!state?.loaded),
        take(1),
        takeUntil(this.destroy$)
      )
      .subscribe(libraries => this.allLibraries = libraries.libraries ?? []);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }


  loadUsers() {
    this.userService.getUsers().subscribe({
      next: (data) => {
        this.users = data.map((user) => ({
          ...user,
          isEditing: false,
          selectedLibraryIds: user.assignedLibraries?.map((lib) => lib.id) || [],
          libraryNames:
            user.assignedLibraries?.map((lib) => lib.name).join(', ') || '',
        }));
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to fetch users',
        });
      },
    });
  }

  openCreateUserDialog() {
    this.ref = this.dialogLauncherService.openCreateUserDialog();
    this.ref?.onClose.subscribe((result) => {
      if (result) {
        this.loadUsers();
      }
    });
  }

  toggleEdit(user: any) {
    user.isEditing = !user.isEditing;
    if (user.isEditing) {
      this.editingLibraryIds = [...user.selectedLibraryIds];
    } else {
      user.libraryNames =
        user.assignedLibraries
          ?.map((lib: Library) => lib.name)
          .join(', ') || '';
    }
  }

  saveUser(user: any) {
    user.selectedLibraryIds = [...this.editingLibraryIds];
    this.userService
      .updateUser(user.id, {
        name: user.name,
        email: user.email,
        permissions: user.permissions,
        assignedLibraries: user.selectedLibraryIds,
      })
      .subscribe({
        next: () => {
          user.isEditing = false;
          this.loadUsers();
          this.messageService.add({
            severity: 'success',
            summary: 'Success',
            detail: 'User updated successfully',
          });
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'Failed to update user',
          });
        },
      });
  }

  deleteUser(user: User) {
    if (confirm(`Are you sure you want to delete ${user.username}?`)) {
      this.userService.deleteUser(user.id).subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Success',
            detail: `User ${user.username} deleted successfully`,
          });
          this.loadUsers();
        },
        error: (err) => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail:
              err.error?.message ||
              `Failed to delete user ${user.username}`,
          });
        },
      });
    }
  }

  openChangePasswordDialog(user: User) {
    this.selectedUser = user;
    this.newPassword = '';
    this.confirmNewPassword = '';
    this.passwordError = '';
    this.isPasswordDialogVisible = true;
  }

  submitPasswordChange() {
    if (!this.newPassword || !this.confirmNewPassword) {
      this.passwordError = 'Both fields are required';
      return;
    }

    if (this.newPassword !== this.confirmNewPassword) {
      this.passwordError = 'Passwords do not match';
      return;
    }

    if (this.selectedUser) {
      this.userService
        .changeUserPassword(this.selectedUser.id, this.newPassword)
        .subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Success',
              detail: 'Password changed successfully',
            });
            this.isPasswordDialogVisible = false;
          },
          error: (err) => {
            this.passwordError = err;
          }
        });
    }
  }

  getBookManagementPermissionsCount(user: User): number {
    const permissions = user.permissions;
    let count = 0;
    if (permissions.canUpload) count++;
    if (permissions.canDownload) count++;
    if (permissions.canDeleteBook) count++;
    if (permissions.canEditMetadata) count++;
    if (permissions.canManageLibrary) count++;
    if (permissions.canEmailBook) count++;
    return count;
  }

  getDeviceSyncPermissionsCount(user: User): number {
    const permissions = user.permissions;
    let count = 0;
    if (permissions.canSyncKoReader) count++;
    if (permissions.canSyncKobo) count++;
    if (permissions.canAccessOpds) count++;
    return count;
  }

  getSystemAccessPermissionsCount(user: User): number {
    const permissions = user.permissions;
    let count = 0;
    if (permissions.canAccessBookdrop) count++;
    if (permissions.canAccessLibraryStats) count++;
    if (permissions.canAccessUserStats) count++;
    return count;
  }

  getSystemConfigPermissionsCount(user: User): number {
    const permissions = user.permissions;
    let count = 0;
    if (permissions.canAccessTaskManager) count++;
    if (permissions.canManageGlobalPreferences) count++;
    if (permissions.canManageMetadataConfig) count++;
    if (permissions.canManageIcons) count++;
    return count;
  }

  toggleRowExpansion(user: User) {
    if (this.expandedRows[user.id]) {
      delete this.expandedRows[user.id];
    } else {
      this.expandedRows[user.id] = true;
    }
  }

  isRowExpanded(user: User): boolean {
    return this.expandedRows[user.id];
  }

  onAdminCheckboxChange(user: any) {
    if (user.permissions.admin) {
      user.permissions.canUpload = true;
      user.permissions.canDownload = true;
      user.permissions.canDeleteBook = true;
      user.permissions.canEditMetadata = true;
      user.permissions.canManageLibrary = true;
      user.permissions.canEmailBook = true;
      user.permissions.canSyncKoReader = true;
      user.permissions.canSyncKobo = true;
      user.permissions.canAccessOpds = true;
      user.permissions.canAccessBookdrop = true;
      user.permissions.canAccessLibraryStats = true;
      user.permissions.canAccessUserStats = true;
      user.permissions.canManageMetadataConfig = true;
      user.permissions.canManageGlobalPreferences = true;
      user.permissions.canAccessTaskManager = true;
      user.permissions.canManageEmailConfig = true;
      user.permissions.canManageIcons = true;
    }
  }

  isPermissionDisabled(user: any): boolean {
    return !user.isEditing || user.permissions.admin;
  }
}
