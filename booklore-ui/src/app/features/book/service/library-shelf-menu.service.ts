import {inject, Injectable} from '@angular/core';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {Router} from '@angular/router';
import {LibraryService} from './library.service';
import {ShelfService} from './shelf.service';
import {Library} from '../model/library.model';
import {Shelf} from '../model/shelf.model';
import {MetadataRefreshType} from '../../metadata/model/request/metadata-refresh-type.enum';
import {MagicShelf, MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {TaskHelperService} from '../../settings/task-management/task-helper.service';
import {UserService} from "../../settings/user-management/user.service";
import {LoadingService} from '../../../core/services/loading.service';
import {finalize} from 'rxjs';
import {DialogLauncherService} from '../../../shared/services/dialog-launcher.service';
import {BookDialogHelperService} from '../components/book-browser/book-dialog-helper.service';

@Injectable({
  providedIn: 'root',
})
export class LibraryShelfMenuService {

  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);
  private libraryService = inject(LibraryService);
  private shelfService = inject(ShelfService);
  private taskHelperService = inject(TaskHelperService);
  private router = inject(Router);
  private dialogLauncherService = inject(DialogLauncherService);
  private magicShelfService = inject(MagicShelfService);
  private userService = inject(UserService);
  private loadingService = inject(LoadingService);
  private bookDialogHelperService = inject(BookDialogHelperService);

  initializeLibraryMenuItems(entity: Library | Shelf | MagicShelf | null): MenuItem[] {
    return [
      {
        label: 'Options',
        items: [
          {
            label: 'Add Physical Book',
            icon: 'pi pi-book',
            command: () => {
              this.bookDialogHelperService.openAddPhysicalBookDialog(entity?.id as number);
            }
          },
          {
            separator: true
          },
          {
            label: 'Edit Library',
            icon: 'pi pi-pen-to-square',
            command: () => {
              this.dialogLauncherService.openLibraryEditDialog((entity?.id as number));
            }
          },
          {
            label: 'Re-scan Library',
            icon: 'pi pi-refresh',
            command: () => {
              this.confirmationService.confirm({
                message: `Are you sure you want to refresh library: ${entity?.name}?`,
                header: 'Confirmation',
                icon: undefined,
                acceptLabel: 'Yes',
                rejectLabel: 'Cancel',
                acceptIcon: undefined,
                rejectIcon: undefined,
                acceptButtonStyleClass: undefined,
                rejectButtonStyleClass: undefined,
                rejectButtonProps: {
                  label: 'Cancel',
                  severity: 'secondary',
                },
                acceptButtonProps: {
                  label: 'Yes',
                  severity: 'success',
                },
                accept: () => {
                  this.libraryService.refreshLibrary(entity?.id!).subscribe({
                    complete: () => {
                      this.messageService.add({severity: 'info', summary: 'Success', detail: 'Library refresh scheduled'});
                    },
                    error: () => {
                      this.messageService.add({
                        severity: 'error',
                        summary: 'Failed',
                        detail: 'Failed to refresh library',
                      });
                    }
                  });
                }
              });
            }
          },
          {
            label: 'Custom Fetch Metadata',
            icon: 'pi pi-sync',
            command: () => {
              this.dialogLauncherService.openLibraryMetadataFetchDialog((entity?.id as number));
            }
          },
          {
            label: 'Auto Fetch Metadata',
            icon: 'pi pi-bolt',
            command: () => {
              this.taskHelperService.refreshMetadataTask({
                refreshType: MetadataRefreshType.LIBRARY,
                libraryId: entity?.id ?? undefined
              }).subscribe();
            }
          },
          {
            separator: true
          },
          {
            label: 'Delete Library',
            icon: 'pi pi-trash',
            command: () => {
              this.confirmationService.confirm({
                message: `Are you sure you want to delete library: ${entity?.name}?`,
                header: 'Confirmation',
                acceptLabel: 'Yes',
                rejectLabel: 'Cancel',
                rejectButtonProps: {
                  label: 'Cancel',
                  severity: 'secondary',
                },
                acceptButtonProps: {
                  label: 'Yes',
                  severity: 'danger',
                },
                accept: () => {
                  const loader = this.loadingService.show(`Deleting library '${entity?.name}'...`);

                  this.libraryService.deleteLibrary(entity?.id!)
                    .pipe(finalize(() => this.loadingService.hide(loader)))
                    .subscribe({
                      complete: () => {
                        this.router.navigate(['/']);
                        this.messageService.add({severity: 'info', summary: 'Success', detail: 'Library was deleted'});
                      },
                      error: () => {
                        this.messageService.add({
                          severity: 'error',
                          summary: 'Failed',
                          detail: 'Failed to delete library',
                        });
                      }
                    });
                }
              });
            }
          }
        ]
      }
    ];
  }

  initializeShelfMenuItems(entity: any): MenuItem[] {
    const user = this.userService.getCurrentUser();
    const isOwner = entity?.userId === user?.id;
    const isPublicShelf = entity?.publicShelf ?? false;
    const disableOptions = !isOwner;

    return [
      {
        label: (isPublicShelf ? 'Public Shelf - ' : '') + (disableOptions ? 'Read only' : 'Options'),
        items: [
          {
            label: 'Edit Shelf',
            icon: 'pi pi-pen-to-square',
            disabled: disableOptions,
            command: () => {
              this.dialogLauncherService.openShelfEditDialog((entity?.id as number));
            }
          },
          {
            separator: true
          },
          {
            label: 'Delete Shelf',
            icon: 'pi pi-trash',
            disabled: disableOptions,
            command: () => {
              this.confirmationService.confirm({
                message: `Are you sure you want to delete shelf: ${entity?.name}?`,
                header: 'Confirmation',
                acceptLabel: 'Yes',
                rejectLabel: 'Cancel',
                acceptButtonProps: {
                  label: 'Yes',
                  severity: 'danger'
                },
                rejectButtonProps: {
                  label: 'Cancel',
                  severity: 'secondary'
                },
                accept: () => {
                  this.shelfService.deleteShelf(entity?.id!).subscribe({
                    complete: () => {
                      this.router.navigate(['/']);
                      this.messageService.add({severity: 'info', summary: 'Success', detail: 'Shelf was deleted'});
                    },
                    error: () => {
                      this.messageService.add({
                        severity: 'error',
                        summary: 'Failed',
                        detail: 'Failed to delete shelf',
                      });
                    }
                  });
                }
              });
            }
          }
        ]
      }
    ];
  }

  initializeMagicShelfMenuItems(entity: MagicShelf | null): MenuItem[] {
    const isAdmin = this.userService.getCurrentUser()?.permissions.admin ?? false;
    const isPublicShelf = entity?.isPublic ?? false;
    const disableOptions = isPublicShelf && !isAdmin;

    return [
      {
        label: 'Options',
        items: [
          {
            label: 'Edit Magic Shelf',
            icon: 'pi pi-pen-to-square',
            disabled: disableOptions,
            command: () => {
              this.dialogLauncherService.openMagicShelfEditDialog((entity?.id as number));
            }
          },
          {
            separator: true
          },
          {
            label: 'Delete Magic Shelf',
            icon: 'pi pi-trash',
            disabled: disableOptions,
            command: () => {
              this.confirmationService.confirm({
                message: `Are you sure you want to delete magic shelf: ${entity?.name}?`,
                header: 'Confirmation',
                acceptLabel: 'Yes',
                rejectLabel: 'Cancel',
                acceptButtonProps: {
                  label: 'Yes',
                  severity: 'danger'
                },
                rejectButtonProps: {
                  label: 'Cancel',
                  severity: 'secondary'
                },
                accept: () => {
                  this.magicShelfService.deleteShelf(entity?.id!).subscribe({
                    complete: () => {
                      this.router.navigate(['/']);
                      this.messageService.add({severity: 'info', summary: 'Success', detail: 'Magic shelf was deleted'});
                    },
                    error: () => {
                      this.messageService.add({
                        severity: 'error',
                        summary: 'Failed',
                        detail: 'Failed to delete shelf',
                      });
                    }
                  });
                }
              });
            }
          }
        ]
      }
    ];
  }
}
