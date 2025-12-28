import {inject, Injectable} from '@angular/core';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {GithubSupportDialog} from '../components/github-support-dialog/github-support-dialog';
import {LibraryCreatorComponent} from '../../features/library-creator/library-creator.component';
import {BookUploaderComponent} from '../components/book-uploader/book-uploader.component';
import {UserProfileDialogComponent} from '../../features/settings/user-profile-dialog/user-profile-dialog.component';
import {MagicShelfComponent} from '../../features/magic-shelf/component/magic-shelf-component';
import {DashboardSettingsComponent} from '../../features/dashboard/components/dashboard-settings/dashboard-settings.component';
import {VersionChangelogDialogComponent} from '../layout/component/layout-menu/version-changelog-dialog/version-changelog-dialog.component';
import {CreateUserDialogComponent} from '../../features/settings/user-management/create-user-dialog/create-user-dialog.component';
import {CreateEmailRecipientDialogComponent} from '../../features/settings/email-v2/create-email-recipient-dialog/create-email-recipient-dialog.component';
import {CreateEmailProviderDialogComponent} from '../../features/settings/email-v2/create-email-provider-dialog/create-email-provider-dialog.component';
import {DirectoryPickerComponent} from '../components/directory-picker/directory-picker.component';
import {BookdropFinalizeResultDialogComponent} from '../../features/bookdrop/component/bookdrop-finalize-result-dialog/bookdrop-finalize-result-dialog.component';
import {BookdropFinalizeResult} from '../../features/bookdrop/service/bookdrop.service';
import {MetadataReviewDialogComponent} from '../../features/metadata/component/metadata-review-dialog/metadata-review-dialog-component';
import {MetadataRefreshType} from '../../features/metadata/model/request/metadata-refresh-type.enum';
import {MetadataFetchOptionsComponent} from '../../features/metadata/component/metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';
import {ShelfEditDialogComponent} from '../../features/book/components/shelf-edit-dialog/shelf-edit-dialog.component';
import {IconPickerComponent} from '../components/icon-picker/icon-picker-component';

@Injectable({
  providedIn: 'root',
})
export class DialogLauncherService {

  dialogService = inject(DialogService);

  private defaultDialogOptions = {
    baseZIndex: 10,
    closable: true,
    dismissableMask: true,
    draggable: false,
    modal: true,
    resizable: false,
    showHeader: true,
    maximizable: false,
  }

  openDialog(component: any, options: {}): DynamicDialogRef | null {
    return this.dialogService.open(component, {
      ...this.defaultDialogOptions,
      ...options,

    });
  }

  openDashboardSettingsDialog(): DynamicDialogRef | null {
    return this.openDialog(DashboardSettingsComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
    });
  }

  openGithubSupportDialog(): DynamicDialogRef | null {
    return this.openDialog(GithubSupportDialog, {
      header: 'Support Booklore',
    });
  }

  openLibraryCreateDialog(): DynamicDialogRef | null {
    return this.openDialog(LibraryCreatorComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
    });
  }

  openDirectoryPickerDialog(): DynamicDialogRef | null {
    return this.openDialog(DirectoryPickerComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
    });
  }

  openLibraryEditDialog(libraryId: number): DynamicDialogRef | null {
    return this.openDialog(LibraryCreatorComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
      data: {
        mode: 'edit',
        libraryId: libraryId
      }
    });
  }

  openLibraryMetadataFetchDialog(libraryId: number): DynamicDialogRef | null {
    return this.openDialog(MetadataFetchOptionsComponent, {
      header: 'Metadata Refresh Options',
      data: {
        libraryId: libraryId,
        metadataRefreshType: MetadataRefreshType.LIBRARY,
      },
    });
  }

  openShelfEditDialog(shelfId: number): DynamicDialogRef | null {
    return this.openDialog(ShelfEditDialogComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
      data: {
        shelfId: shelfId
      },
    })
  }

  openFileUploadDialog(): DynamicDialogRef | null {
    return this.openDialog(BookUploaderComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
    });
  }

  openCreateUserDialog(): DynamicDialogRef | null {
    return this.openDialog(CreateUserDialogComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
    });
  }

  openUserProfileDialog(): DynamicDialogRef | null {
    return this.openDialog(UserProfileDialogComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
    });
  }

  openMagicShelfCreateDialog(): DynamicDialogRef | null {
    return this.openDialog(MagicShelfComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
    });
  }

  openMagicShelfEditDialog(shelfId: number): DynamicDialogRef | null {
    return this.openDialog(MagicShelfComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
      data: {
        id: shelfId,
        editMode: true,
      }
    })
  }

  openVersionChangelogDialog(): DynamicDialogRef | null {
    return this.openDialog(VersionChangelogDialogComponent, {
      header: "What's New",
      styleClass: 'dialog-maximal',
    });
  }

  openEmailRecipientDialog(): DynamicDialogRef | null {
    return this.openDialog(CreateEmailRecipientDialogComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
    });
  }

  openEmailProviderDialog(): DynamicDialogRef | null {
    return this.openDialog(CreateEmailProviderDialogComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
    });
  }

  openBookdropFinalizeResultDialog(result: BookdropFinalizeResult): DynamicDialogRef | null {
    return this.openDialog(BookdropFinalizeResultDialogComponent, {
      header: 'Import Summary',
      data: {
        result: result,
      },
    });
  }

  openMetadataReviewDialog(taskId: string): DynamicDialogRef | null {
    return this.openDialog(MetadataReviewDialogComponent, {
      header: 'Review Metadata Proposal',
      data: {
        taskId,
      },
      styleClass: 'dialog-maximal',
    });
  }

  openIconPickerDialog(): DynamicDialogRef | null {
    return this.openDialog(IconPickerComponent, {
      header: 'Choose an Icon',
      styleClass: 'dialog-medium',
    });
  }

}
