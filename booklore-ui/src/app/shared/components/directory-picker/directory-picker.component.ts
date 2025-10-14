import {Component, inject, OnInit} from '@angular/core';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {UtilityService} from './utility.service';
import {TableModule} from 'primeng/table';
import {InputText} from 'primeng/inputtext';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Breadcrumb} from 'primeng/breadcrumb';
import {MenuItem} from 'primeng/api';
import {CheckboxModule} from 'primeng/checkbox';
import {InputIcon} from 'primeng/inputicon';
import {Button} from 'primeng/button';
import {IconField} from 'primeng/iconfield';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-directory-picker-v2',
  standalone: true,
  templateUrl: './directory-picker.component.html',
  imports: [
    TableModule,
    InputText,
    CommonModule,
    FormsModule,
    ProgressSpinner,
    Breadcrumb,
    CheckboxModule,
    InputIcon,
    Button,
    InputIcon,
    IconField,
    Tooltip
  ],
  styleUrls: ['./directory-picker.component.scss']
})
export class DirectoryPickerComponent implements OnInit {
  value: any;
  paths: string[] = [];
  filteredPaths: string[] = [];
  selectedProductName: string = '';
  selectedFolders: string[] = [];
  selectedFoldersMap: { [key: string]: boolean } = {};
  searchQuery: string = '';
  isLoading: boolean = false;
  breadcrumbItems: MenuItem[] = [];
  home: MenuItem = {icon: 'pi pi-home', command: () => this.navigateToRoot()};

  private utilityService = inject(UtilityService);
  private dynamicDialogRef = inject(DynamicDialogRef);

  ngOnInit() {
    const initialPath = '/';
    this.getFolders(initialPath);
  }

  getFolders(path: string): void {
    this.isLoading = true;
    this.utilityService.getFolders(path).subscribe({
      next: (folders: string[]) => {
        this.paths = folders;
        this.filteredPaths = folders;
        this.isLoading = false;
        this.updateBreadcrumb(path);
        folders.forEach(folder => {
          this.selectedFoldersMap[folder] = this.selectedFolders.includes(folder);
        });
      },
      error: (error) => {
        console.error('Error fetching folders:', error);
        this.isLoading = false;
      }
    });
  }

  updateBreadcrumb(path: string): void {
    if (path === '/' || path === '') {
      this.breadcrumbItems = [];
      return;
    }

    const parts = path.split('/').filter(p => p);
    this.breadcrumbItems = parts.map((part, index) => {
      const fullPath = '/' + parts.slice(0, index + 1).join('/');
      return {
        label: part,
        command: () => this.navigateToPath(fullPath)
      };
    });
  }

  navigateToRoot(): void {
    this.selectedProductName = '/';
    this.getFolders('/');
    this.searchQuery = '';
  }

  navigateToPath(path: string): void {
    this.selectedProductName = path;
    this.getFolders(path);
    this.searchQuery = '';
  }

  onRowClick(path: string): void {
    this.selectedProductName = path;
    this.getFolders(path);
    this.searchQuery = '';
  }

  toggleFolderSelection(path: string, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }

    const index = this.selectedFolders.indexOf(path);
    if (index > -1) {
      this.selectedFolders.splice(index, 1);
      this.selectedFoldersMap[path] = false;
    } else {
      this.selectedFolders.push(path);
      this.selectedFoldersMap[path] = true;
    }
  }

  onCheckboxChange(path: string, checked: boolean): void {
    const index = this.selectedFolders.indexOf(path);
    if (checked && index === -1) {
      this.selectedFolders.push(path);
    } else if (!checked && index > -1) {
      this.selectedFolders.splice(index, 1);
    }
  }

  isFolderSelected(path: string): boolean {
    return this.selectedFolders.includes(path);
  }

  goUp(): void {
    if (this.selectedProductName === '' || this.selectedProductName === '/') {
      return;
    }
    const result = this.selectedProductName.substring(0, this.selectedProductName.lastIndexOf('/')) || '/';
    this.selectedProductName = result;
    this.getFolders(result);
    this.searchQuery = '';
  }

  onSearch(): void {
    if (!this.searchQuery.trim()) {
      this.filteredPaths = this.paths;
      return;
    }

    const query = this.searchQuery.toLowerCase();
    this.filteredPaths = this.paths.filter(path =>
      path.toLowerCase().includes(query)
    );
  }

  onSelect(): void {
    this.dynamicDialogRef.close(this.selectedFolders);
  }

  onCancel(): void {
    this.dynamicDialogRef.close(null);
  }

  getFolderName(path: string): string {
    return path.split('/').filter(p => p).pop() || path;
  }
}
