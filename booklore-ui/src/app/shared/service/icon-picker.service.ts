import {inject, Injectable} from '@angular/core';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {IconPickerComponent} from '../components/icon-picker/icon-picker-component';
import {Observable} from 'rxjs';

export interface IconSelection {
  type: 'PRIME_NG' | 'CUSTOM_SVG';
  value: string;
}

@Injectable({providedIn: 'root'})
export class IconPickerService {
  private dialog = inject(DialogService);

  open(): Observable<IconSelection> {
    const isMobile = window.innerWidth <= 768;
    const ref: DynamicDialogRef | null = this.dialog.open(IconPickerComponent, {
      header: 'Choose an Icon',
      modal: true,
      closable: true,
      style: {
        position: 'absolute',
        top: '10%',
        bottom: '10%',
        width: isMobile ? '90vw' : '800px',
        maxWidth: isMobile ? '90vw' : '800px',
        minWidth: isMobile ? '90vw' : '800px',
      }
    });
    return ref!.onClose as Observable<IconSelection>;
  }
}
