import {Component, inject, OnInit} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {Tooltip} from 'primeng/tooltip';
import {Select} from 'primeng/select';
import {Textarea} from 'primeng/textarea';
import {InputNumber} from 'primeng/inputnumber';
import {BookService} from '../../service/book.service';
import {LibraryService} from '../../service/library.service';
import {Library} from '../../model/library.model';
import {CreatePhysicalBookRequest} from '../../model/book.model';
import {Chip} from 'primeng/chip';

@Component({
  selector: 'app-add-physical-book-dialog',
  standalone: true,
  templateUrl: './add-physical-book-dialog.component.html',
  imports: [
    FormsModule,
    Button,
    InputText,
    Tooltip,
    Select,
    Textarea,
    InputNumber,
    Chip
  ],
  styleUrl: './add-physical-book-dialog.component.scss',
})
export class AddPhysicalBookDialogComponent implements OnInit {
  private dynamicDialogRef = inject(DynamicDialogRef);
  private dialogConfig = inject(DynamicDialogConfig);
  private bookService = inject(BookService);
  private libraryService = inject(LibraryService);

  libraries: Library[] = [];
  selectedLibraryId: number | null = null;
  title: string = '';
  isbn: string = '';
  authorInput: string = '';
  authors: string[] = [];
  description: string = '';
  publisher: string = '';
  publishedDate: string = '';
  language: string = '';
  pageCount: number | null = null;
  categoryInput: string = '';
  categories: string[] = [];

  isLoading: boolean = false;

  ngOnInit(): void {
    this.libraryService.libraryState$.subscribe(state => {
      if (state.loaded && state.libraries) {
        this.libraries = state.libraries;
        if (this.dialogConfig.data?.libraryId) {
          this.selectedLibraryId = this.dialogConfig.data.libraryId;
        } else if (this.libraries.length > 0 && this.libraries[0].id !== undefined) {
          this.selectedLibraryId = this.libraries[0].id;
        }
      }
    });
  }

  addAuthor(): void {
    const trimmed = this.authorInput.trim();
    if (trimmed && !this.authors.includes(trimmed)) {
      this.authors.push(trimmed);
      this.authorInput = '';
    }
  }

  removeAuthor(author: string): void {
    this.authors = this.authors.filter(a => a !== author);
  }

  addCategory(): void {
    const trimmed = this.categoryInput.trim();
    if (trimmed && !this.categories.includes(trimmed)) {
      this.categories.push(trimmed);
      this.categoryInput = '';
    }
  }

  removeCategory(category: string): void {
    this.categories = this.categories.filter(c => c !== category);
  }

  onAuthorKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.addAuthor();
    }
  }

  onCategoryKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.addCategory();
    }
  }

  cancel(): void {
    this.dynamicDialogRef.close();
  }

  canCreate(): boolean {
    return !!this.selectedLibraryId && (!!this.title.trim() || !!this.isbn.trim());
  }

  createBook(): void {
    if (!this.canCreate() || this.isLoading) return;

    this.isLoading = true;

    const request: CreatePhysicalBookRequest = {
      libraryId: this.selectedLibraryId!,
      title: this.title.trim() || undefined,
      isbn: this.isbn.trim() || undefined,
      authors: this.authors.length > 0 ? this.authors : undefined,
      description: this.description.trim() || undefined,
      publisher: this.publisher.trim() || undefined,
      publishedDate: this.publishedDate.trim() || undefined,
      language: this.language.trim() || undefined,
      pageCount: this.pageCount ?? undefined,
      categories: this.categories.length > 0 ? this.categories : undefined
    };

    this.bookService.createPhysicalBook(request).subscribe({
      next: (book) => {
        this.isLoading = false;
        this.dynamicDialogRef.close(book);
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }
}
