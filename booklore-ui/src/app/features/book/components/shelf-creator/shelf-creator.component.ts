import {Component, inject} from '@angular/core';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {ShelfService} from '../../service/shelf.service';
import {IconPickerService, IconSelection} from '../../../../shared/service/icon-picker.service';
import {Shelf} from '../../model/shelf.model';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {Tooltip} from 'primeng/tooltip';
import {IconDisplayComponent} from '../../../../shared/components/icon-display/icon-display.component';
import {UserService} from '../../../settings/user-management/user.service';
import {CheckboxModule} from 'primeng/checkbox';

@Component({
  selector: 'app-shelf-creator',
  standalone: true,
  templateUrl: './shelf-creator.component.html',
  imports: [
    FormsModule,
    Button,
    InputText,
    Tooltip,
    IconDisplayComponent,
    CheckboxModule
  ],
  styleUrl: './shelf-creator.component.scss',
})
export class ShelfCreatorComponent {
  private shelfService = inject(ShelfService);
  private dynamicDialogRef = inject(DynamicDialogRef);
  private messageService = inject(MessageService);
  private iconPickerService = inject(IconPickerService);
  private userService = inject(UserService);

  shelfName: string = '';
  selectedIcon: IconSelection | null = null;
  isPublic: boolean = false;
  isAdmin: boolean = this.userService.getCurrentUser()?.permissions.admin ?? false;

  openIconPicker(): void {
    this.iconPickerService.open().subscribe(icon => {
      if (icon) {
        this.selectedIcon = icon;
      }
    });
  }

  clearSelectedIcon(): void {
    this.selectedIcon = null;
  }

  cancel(): void {
    this.dynamicDialogRef.close();
  }

  createShelf(): void {
    const iconValue = this.selectedIcon?.value || 'bookmark';
    const iconType = this.selectedIcon?.type || 'PRIME_NG';

    const newShelf: Partial<Shelf> = {
      name: this.shelfName,
      icon: iconValue,
      iconType: iconType,
      publicShelf: this.isPublic
    };

    this.shelfService.createShelf(newShelf as Shelf).subscribe({
      next: () => {
        this.messageService.add({severity: 'info', summary: 'Success', detail: `Shelf created: ${this.shelfName}`});
        this.dynamicDialogRef.close(true);
      },
      error: (e) => {
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to create shelf'});
        console.error('Error creating shelf:', e);
      }
    });
  }
}
