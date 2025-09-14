import {Injectable} from '@angular/core';
import {ReadStatus} from '../model/book.model';

@Injectable({
  providedIn: 'root'
})
export class ReadStatusHelper {

  getReadStatusIcon(readStatus: ReadStatus | undefined): string {
    if (!readStatus) return '';
    switch (readStatus) {
      case ReadStatus.READ:
        return 'pi pi-check';
      case ReadStatus.READING:
        return 'pi pi-play';
      case ReadStatus.RE_READING:
        return 'pi pi-refresh';
      case ReadStatus.PARTIALLY_READ:
        return 'pi pi-clock';
      case ReadStatus.PAUSED:
        return 'pi pi-pause';
      case ReadStatus.ABANDONED:
        return 'pi pi-times';
      case ReadStatus.WONT_READ:
        return 'pi pi-ban';
      default:
        return '';
    }
  }

  getReadStatusClass(readStatus: ReadStatus | undefined): string {
    if (!readStatus) return '';
    switch (readStatus) {
      case ReadStatus.READ:
        return 'status-read';
      case ReadStatus.READING:
        return 'status-reading';
      case ReadStatus.RE_READING:
        return 'status-re-reading';
      case ReadStatus.PARTIALLY_READ:
        return 'status-partially-read';
      case ReadStatus.PAUSED:
        return 'status-paused';
      case ReadStatus.ABANDONED:
        return 'status-abandoned';
      case ReadStatus.WONT_READ:
        return 'status-wont-read';
      default:
        return '';
    }
  }

  getReadStatusTooltip(readStatus: ReadStatus | undefined): string {
    if (!readStatus) return '';
    switch (readStatus) {
      case ReadStatus.READ:
        return 'Read';
      case ReadStatus.READING:
        return 'Currently Reading';
      case ReadStatus.RE_READING:
        return 'Re-reading';
      case ReadStatus.PARTIALLY_READ:
        return 'Partially Read';
      case ReadStatus.PAUSED:
        return 'Paused';
      case ReadStatus.ABANDONED:
        return 'Abandoned';
      case ReadStatus.WONT_READ:
        return 'Won\'t Read';
      default:
        return '';
    }
  }

  shouldShowStatusIcon(readStatus: ReadStatus | undefined): boolean {
    return !!(readStatus && readStatus !== ReadStatus.UNREAD && readStatus !== ReadStatus.UNSET);
  }
}

