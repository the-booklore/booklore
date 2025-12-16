import {inject, Injectable} from '@angular/core';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {ShelfAssignerComponent} from '../shelf-assigner/shelf-assigner.component';
import {LockUnlockMetadataDialogComponent} from './lock-unlock-metadata-dialog/lock-unlock-metadata-dialog.component';
import {MetadataRefreshType} from '../../../metadata/model/request/metadata-refresh-type.enum';
import {BulkMetadataUpdateComponent} from '../../../metadata/component/bulk-metadata-update/bulk-metadata-update-component';
import {MultiBookMetadataEditorComponent} from '../../../metadata/component/multi-book-metadata-editor/multi-book-metadata-editor-component';
import {MultiBookMetadataFetchComponent} from '../../../metadata/component/multi-book-metadata-fetch/multi-book-metadata-fetch-component';
import {FileMoverComponent} from '../../../../shared/components/file-mover/file-mover-component';
import {ShelfCreatorComponent} from '../shelf-creator/shelf-creator.component';
import {BookSenderComponent} from '../book-sender/book-sender.component';
import {MetadataFetchOptionsComponent} from '../../../metadata/component/metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';
import {BookMetadataCenterComponent} from '../../../metadata/component/book-metadata-center/book-metadata-center.component';
import {CoverSearchComponent} from '../../../metadata/component/cover-search/cover-search.component';
import {Book} from '../../model/book.model';
import {AdditionalFileUploaderComponent} from '../additional-file-uploader/additional-file-uploader.component';

@Injectable({providedIn: 'root'})
export class BookDialogHelperService {

  private dialogLauncherService = inject(DialogLauncherService);

  private openDialog(component: any, options: {}): DynamicDialogRef | null {
    return this.dialogLauncherService.openDialog(component, options);
  }

  openBookDetailsDialog(bookId: number): DynamicDialogRef | null {
    return this.openDialog(BookMetadataCenterComponent, {
      header: 'Book Details',
      styleClass: 'book-details-dialog dialog-maximal',
      data: {
        bookId: bookId,
      },
    });
  }

  openShelfAssignerDialog(book: Book | null, bookIds: Set<number> | null): DynamicDialogRef | null {
    const data: any = {};
    if (book !== null) {
      data.isMultiBooks = false;
      data.book = book;
    } else if (bookIds !== null) {
      data.isMultiBooks = true;
      data.bookIds = bookIds;
    } else {
      return null;
    }
    return this.openDialog(ShelfAssignerComponent, {
      showHeader: false,
      data: data,
      styleClass: 'dynamic-dialog-minimal',
    });
  }

  openShelfCreatorDialog(): DynamicDialogRef {
    return this.openDialog(ShelfCreatorComponent, {
      showHeader: false,
      styleClass: 'dynamic-dialog-minimal',
    })!;
  }

  openLockUnlockMetadataDialog(bookIds: Set<number>): DynamicDialogRef | null {
    const count = bookIds.size;
    return this.openDialog(LockUnlockMetadataDialogComponent, {
      header: `Lock or Unlock Metadata for ${count} Selected Book${count > 1 ? 's' : ''}`,
      data: {
        bookIds: Array.from(bookIds),
      },
    });
  }

  openMetadataRefreshDialog(bookIds: Set<number>): DynamicDialogRef | null {
    return this.openDialog(MultiBookMetadataFetchComponent, {
      header: 'Metadata Refresh Options',
      data: {
        bookIds: Array.from(bookIds),
        metadataRefreshType: MetadataRefreshType.BOOKS,
      },
      styleClass: 'dialog-maximal',
    });
  }

  openBulkMetadataEditDialog(bookIds: Set<number>): DynamicDialogRef | null {
    return this.openDialog(BulkMetadataUpdateComponent, {
      header: 'Bulk Edit Metadata',
      data: {
        bookIds: Array.from(bookIds),
      },
      styleClass: 'dialog-maximal'
    });
  }

  openMultibookMetadataEditorDialog(bookIds: Set<number>): DynamicDialogRef | null {
    return this.openDialog(MultiBookMetadataEditorComponent, {
      header: 'Multi-Book Metadata Editor',
      data: {
        bookIds: Array.from(bookIds),
      },
      styleClass: 'dialog-full'
    });
  }

  openFileMoverDialog(bookIds: Set<number>): DynamicDialogRef | null {
    const count = bookIds.size;
    return this.openDialog(FileMoverComponent, {
      header: `Organize Book Files (${count} book${count !== 1 ? 's' : ''})`,
      data: {
        bookIds: Array.from(bookIds),
      },
      styleClass: 'dialog-full',
      maximizable: true,
    });
  }

  openCustomSendDialog(bookId: number): DynamicDialogRef | null {
    return this.openDialog(BookSenderComponent, {
      header: 'Send Book to Email',
      data: {
        bookId: bookId,
      }
    });
  }

  openCoverSearchDialog(bookId: number): DynamicDialogRef | null {
    return this.openDialog(CoverSearchComponent, {
      header: "Search Cover",
      data: {
        bookId: bookId,
      },
      styleClass: 'dialog-maximal',
    });
  }

  openMetadataFetchOptionsDialog(bookId: number): DynamicDialogRef | null {
    return this.openDialog(MetadataFetchOptionsComponent, {
      header: 'Metadata Refresh Options',
      data: {
        bookIds: [bookId],
        metadataRefreshType: MetadataRefreshType.BOOKS,
      }
    });
  }

  openAdditionalFileUploaderDialog(book: Book): DynamicDialogRef | null {
    return this.openDialog(AdditionalFileUploaderComponent, {
      header: 'Upload Additional File',
      data: {
        book: book,
      }
    });
  }
}
