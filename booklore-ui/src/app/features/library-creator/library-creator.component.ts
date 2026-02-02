import {Component, inject, OnInit} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {Router} from '@angular/router';
import {LibraryService} from '../book/service/library.service';
import {FormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {Library} from '../book/model/library.model';
import {BookType} from '../book/model/book.model';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Tooltip} from 'primeng/tooltip';
import {IconPickerService, IconSelection} from '../../shared/service/icon-picker.service';
import {Button} from 'primeng/button';
import {IconDisplayComponent} from '../../shared/components/icon-display/icon-display.component';
import {DialogLauncherService} from '../../shared/services/dialog-launcher.service';
import {switchMap} from 'rxjs/operators';
import {map} from 'rxjs';
import {CdkDragDrop, DragDropModule, moveItemInArray} from '@angular/cdk/drag-drop';

@Component({
  selector: 'app-library-creator',
  standalone: true,
  templateUrl: './library-creator.component.html',
  imports: [FormsModule, InputText, ToggleSwitch, Tooltip, Button, IconDisplayComponent, DragDropModule],
  styleUrl: './library-creator.component.scss'
})
export class LibraryCreatorComponent implements OnInit {
  chosenLibraryName: string = '';
  folders: string[] = [];
  selectedIcon: IconSelection | null = null;

  mode!: string;
  library!: Library | undefined;
  editModeLibraryName: string = '';
  watch: boolean = false;
  formatPriority: {type: BookType, label: string}[] = [];

  readonly allBookFormats: {type: BookType, label: string}[] = [
    {type: 'EPUB', label: 'EPUB'},
    {type: 'PDF', label: 'PDF'},
    {type: 'CBX', label: 'CBX (CBZ/CBR/CB7)'},
    {type: 'MOBI', label: 'MOBI'},
    {type: 'AZW3', label: 'AZW3'},
    {type: 'FB2', label: 'FB2'},
    {type: 'AUDIOBOOK', label: 'Audiobook'}
  ];

  private dialogLauncherService = inject(DialogLauncherService);
  private dynamicDialogRef = inject(DynamicDialogRef);
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  private libraryService = inject(LibraryService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private iconPicker = inject(IconPickerService);

  ngOnInit(): void {
    this.initializeFormatPriority();

    const data = this.dynamicDialogConfig?.data;
    if (data?.mode === 'edit') {
      this.mode = data.mode;
      this.library = this.libraryService.findLibraryById(data.libraryId);
      if (this.library) {
        const {name, icon, iconType, paths, watch, formatPriority} = this.library;
        this.chosenLibraryName = name;
        this.editModeLibraryName = name;

        if (iconType === 'CUSTOM_SVG') {
          this.selectedIcon = {type: 'CUSTOM_SVG', value: icon};
        } else {
          const value = icon.slice(0, 6) === 'pi pi-' ? icon : `pi pi-${icon}`;
          this.selectedIcon = {type: 'PRIME_NG', value: value};
        }

        this.watch = watch;
        if (formatPriority && formatPriority.length > 0) {
          this.formatPriority = formatPriority.map(type =>
            this.allBookFormats.find(f => f.type === type)!
          ).filter(f => f !== undefined);
          const existingTypes = new Set(formatPriority);
          this.allBookFormats.forEach(f => {
            if (!existingTypes.has(f.type)) {
              this.formatPriority.push(f);
            }
          });
        }
        this.folders = paths.map(path => path.path);
      }
    }
  }

  private initializeFormatPriority(): void {
    this.formatPriority = [...this.allBookFormats];
  }

  closeDialog(): void {
    this.dynamicDialogRef.close();
  }

  openDirectoryPicker(): void {
    const ref = this.dialogLauncherService.openDirectoryPickerDialog();
    ref?.onClose.subscribe((selectedFolders: string[] | null) => {
      if (selectedFolders && selectedFolders.length > 0) {
        selectedFolders.forEach(folder => {
          if (!this.folders.includes(folder)) {
            this.addFolder(folder);
          }
        });
      }
    });
  }

  openIconPicker(): void {
    this.iconPicker.open().subscribe(icon => {
      if (icon) {
        this.selectedIcon = icon;
      }
    });
  }

  addFolder(folder: string): void {
    this.folders.push(folder);
  }

  removeFolder(index: number): void {
    this.folders.splice(index, 1);
  }

  clearSelectedIcon(): void {
    this.selectedIcon = null;
  }

  isLibraryDetailsValid(): boolean {
    return !!this.chosenLibraryName.trim() && !!this.selectedIcon;
  }

  isDirectorySelectionValid(): boolean {
    return this.folders.length > 0;
  }

  createOrUpdateLibrary(): void {
    const trimmedLibraryName = this.chosenLibraryName.trim();
    if (trimmedLibraryName && trimmedLibraryName !== this.editModeLibraryName) {
      const exists = this.libraryService.doesLibraryExistByName(trimmedLibraryName);
      if (exists) {
        this.messageService.add({
          severity: 'error',
          summary: 'Library Name Exists',
          detail: 'This library name is already taken.',
        });
        return;
      }
    }

    const iconValue = this.selectedIcon?.value || 'heart';
    const iconType = this.selectedIcon?.type || 'PRIME_NG';

    const library: Library = {
      name: this.chosenLibraryName,
      icon: iconValue,
      iconType: iconType,
      paths: this.folders.map(folder => ({path: folder})),
      watch: this.watch,
      formatPriority: this.formatPriority.map(f => f.type)
    };

    if (this.mode === 'edit') {
      this.libraryService.updateLibrary(library, this.library?.id).subscribe({
        next: () => {
          this.messageService.add({severity: 'success', summary: 'Library Updated', detail: 'The library was updated successfully.'});
          this.dynamicDialogRef.close();
        },
        error: (e) => {
          this.messageService.add({severity: 'error', summary: 'Update Failed', detail: 'An error occurred while updating the library. Please try again.'});
          console.error(e);
        }
      });
    } else {
      this.libraryService.scanLibraryPaths(library).pipe(
        switchMap(count => {
          if (count < 500) {
            return this.libraryService.createLibrary(library).pipe(
              map(createdLibrary => ({ createdLibrary, count }))
            );
          } else {
            console.warn(`Library has ${count} processable files (>500). Will use buffered loading.`);
            this.libraryService.setLargeLibraryLoading(true, count);
            return this.libraryService.createLibrary(library).pipe(
              map(createdLibrary => ({ createdLibrary, count }))
            );
          }
        })
      ).subscribe({
        next: ({ createdLibrary, count }) => {
          if (createdLibrary) {
            this.router.navigate(['/library', createdLibrary.id, 'books']);
            this.messageService.add({
              severity: 'success',
              summary: 'Library Created',
              detail: count >= 500
                ? `Library created with ${count} files. Loading in progress...`
                : 'The library was created successfully.'
            });
            this.dynamicDialogRef.close();
          }
        },
        error: (e) => {
          this.libraryService.setLargeLibraryLoading(false, 0);
          this.messageService.add({severity: 'error', summary: 'Creation Failed', detail: 'An error occurred while creating the library. Please try again.'});
          console.error(e);
        }
      });
    }
  }

  onFormatPriorityDrop(event: CdkDragDrop<{type: BookType, label: string}[]>): void {
    moveItemInArray(this.formatPriority, event.previousIndex, event.currentIndex);
  }
}
