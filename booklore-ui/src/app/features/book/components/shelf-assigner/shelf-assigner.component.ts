import {Component, inject, OnInit} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Book} from '../../model/book.model';
import {MessageService} from 'primeng/api';
import {ShelfService} from '../../service/shelf.service';
import {finalize, Observable} from 'rxjs';
import {BookService} from '../../service/book.service';
import {map, tap} from 'rxjs/operators';
import {Shelf} from '../../model/shelf.model';
import {ShelfState} from '../../model/state/shelf-state.model';
import {Button} from 'primeng/button';
import {AsyncPipe} from '@angular/common';
import {Checkbox} from 'primeng/checkbox';
import {FormsModule} from '@angular/forms';
import {BookDialogHelperService} from '../book-browser/BookDialogHelperService';
import {LoadingService} from '../../../../core/services/loading.service';

@Component({
  selector: 'app-shelf-assigner',
  standalone: true,
  templateUrl: './shelf-assigner.component.html',
  styleUrl: './shelf-assigner.component.scss',
  imports: [
    Button,
    Checkbox,
    AsyncPipe,
    FormsModule
  ]
})
export class ShelfAssignerComponent implements OnInit {

  private shelfService = inject(ShelfService);
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  private dynamicDialogRef = inject(DynamicDialogRef);
  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  private bookDialogHelper = inject(BookDialogHelperService);
  private loadingService = inject(LoadingService);

  shelfState$: Observable<ShelfState> = this.shelfService.shelfState$;
  book: Book = this.dynamicDialogConfig.data.book;
  selectedShelves: Shelf[] = [];
  bookIds: Set<number> = this.dynamicDialogConfig.data.bookIds;
  isMultiBooks: boolean = this.dynamicDialogConfig.data.isMultiBooks;

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

  updateBooksShelves(): void {
    const idsToAssign = new Set<number | undefined>(this.selectedShelves.map(shelf => shelf.id));
    const idsToUnassign: Set<number> = this.isMultiBooks ? new Set() : this.getIdsToUnAssign(this.book, idsToAssign);
    const bookIds = this.isMultiBooks ? this.bookIds : new Set([this.book.id]);
    this.updateBookShelves(bookIds, idsToAssign, idsToUnassign);
  }

  private updateBookShelves(bookIds: Set<number>, idsToAssign: Set<number | undefined>, idsToUnassign: Set<number>): void {
    const loader = this.loadingService.show(`Updating shelves for ${bookIds.size} book(s)...`);

    this.bookService.updateBookShelves(bookIds, idsToAssign, idsToUnassign)
      .pipe(finalize(() => this.loadingService.hide(loader)))
      .subscribe({
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
    const dialogRef = this.bookDialogHelper.openShelfCreatorDialog();

    dialogRef.onClose.subscribe((created: boolean) => {
      if (created) {
        this.shelfService.reloadShelves();
      }
    });
  }

  closeDialog(): void {
    this.dynamicDialogRef.close();
  }

  isShelfSelected(shelf: Shelf): boolean {
    return this.selectedShelves.some(s => s.id === shelf.id);
  }
}
