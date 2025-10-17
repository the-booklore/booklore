import {Component, inject, OnInit} from '@angular/core';
import {DialogService, DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Book} from '../../model/book.model';
import {MessageService, PrimeTemplate} from 'primeng/api';
import {ShelfService} from '../../service/shelf.service';
import {Observable} from 'rxjs';
import {BookService} from '../../service/book.service';
import {map, tap} from 'rxjs/operators';
import {Shelf} from '../../model/shelf.model';
import {ShelfState} from '../../model/state/shelf-state.model';
import {Button} from 'primeng/button';
import {AsyncPipe} from '@angular/common';
import {Checkbox} from 'primeng/checkbox';
import {FormsModule} from '@angular/forms';
import {Dialog} from 'primeng/dialog';
import {InputText} from 'primeng/inputtext';
import {IconPickerService} from '../../../../shared/service/icon-picker.service';

@Component({
  selector: 'app-shelf-assigner',
  standalone: true,
  templateUrl: './shelf-assigner.component.html',
  imports: [
    Button,
    Checkbox,
    AsyncPipe,
    FormsModule,
    Dialog,
    InputText,
    PrimeTemplate
],
  styleUrls: ['./shelf-assigner.component.scss']
})
export class ShelfAssignerComponent implements OnInit {

  private shelfService = inject(ShelfService);
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  private dynamicDialogRef = inject(DynamicDialogRef);
  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  private iconPickerService = inject(IconPickerService);

  shelfState$: Observable<ShelfState> = this.shelfService.shelfState$;
  book: Book = this.dynamicDialogConfig.data.book;
  selectedShelves: Shelf[] = [];
  displayShelfDialog: boolean = false;
  shelfName: string = '';
  bookIds: Set<number> = this.dynamicDialogConfig.data.bookIds;
  isMultiBooks: boolean = this.dynamicDialogConfig.data.isMultiBooks;
  selectedIcon: string | null = null;

  ngOnInit(): void {
    if (!this.isMultiBooks && this.book.shelves) {
      this.shelfState$.pipe(
        map(state => state.shelves || []),
        tap(shelves => {
          this.selectedShelves = shelves.filter(shelf =>
            this.book.shelves?.some(bShelf => bShelf.id === shelf.id)
          );
        })
      ).subscribe();
    }
  }

  saveNewShelf(): void {
    const newShelf: Partial<Shelf> = {
      name: this.shelfName,
      icon: this.selectedIcon ? this.selectedIcon.replace('pi pi-', '') : 'heart'
    };
    this.shelfService.createShelf(newShelf as Shelf).subscribe({
      next: () => {
        this.messageService.add({severity: 'info', summary: 'Success', detail: `Shelf created: ${this.shelfName}`});
        this.displayShelfDialog = false;
      },
      error: (e) => {
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to create shelf'});
        console.error('Error creating shelf:', e);
      }
    });
  }

  updateBooksShelves(): void {
    const idsToAssign = new Set<number | undefined>(this.selectedShelves.map(shelf => shelf.id));
    const idsToUnassign: Set<number> = this.isMultiBooks ? new Set() : this.getIdsToUnAssign(this.book, idsToAssign);
    const bookIds = this.isMultiBooks ? this.bookIds : new Set([this.book.id]);
    this.updateBookShelves(bookIds, idsToAssign, idsToUnassign);
  }

  private updateBookShelves(bookIds: Set<number>, idsToAssign: Set<number | undefined>, idsToUnassign: Set<number>): void {
    this.bookService.updateBookShelves(bookIds, idsToAssign, idsToUnassign).subscribe({
      next: () => {
        this.messageService.add({severity: 'info', summary: 'Success', detail: 'Book shelves updated'});
        this.dynamicDialogRef.close();
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to update book shelves'});
        this.dynamicDialogRef.close();
      }
    });
  }

  private getIdsToUnAssign(book: Book, idsToAssign: Set<number | undefined>): Set<number> {
    const idsToUnassign = new Set<number>();
    book.shelves?.forEach(shelf => {
      if (!idsToAssign.has(shelf.id)) {
        idsToUnassign.add(shelf.id!);
      }
    });
    return idsToUnassign;
  }

  createShelfDialog(): void {
    this.displayShelfDialog = true;
  }

  closeShelfDialog(): void {
    this.displayShelfDialog = false;
  }

  closeDialog(): void {
    this.dynamicDialogRef.close();
  }

  openIconPicker() {
    this.iconPickerService.open().subscribe(icon => {
      if (icon) {
        this.selectedIcon = icon;
      }
    })
  }

  clearSelectedIcon() {
    this.selectedIcon = null;
  }
}
