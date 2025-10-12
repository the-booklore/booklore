import {Component, inject, OnInit} from '@angular/core';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {UtilityService} from './utility.service';
import {Button} from 'primeng/button';
import {TableModule} from 'primeng/table';

import {InputText} from 'primeng/inputtext';

@Component({
  selector: 'app-directory-picker-v2',
  standalone: true,
  templateUrl: './directory-picker.component.html',
  imports: [
    Button,
    TableModule,
    InputText
],
  styleUrls: ['./directory-picker.component.scss']
})
export class DirectoryPickerComponent implements OnInit {
  value: any;
  paths: string[] = ['...'];
  selectedProductName: string = '';

  private utilityService = inject(UtilityService);
  private dynamicDialogRef = inject(DynamicDialogRef);

  ngOnInit() {
    const initialPath = '/';
    this.getFolders(initialPath);
  }

  getFolders(path: string): void {
    this.utilityService.getFolders(path).subscribe(
      (folders: string[]) => {
        this.paths = ['...', ...folders];
      },
      (error) => {
        console.error('Error fetching folders:', error);
      }
    );
  }

  onRowClick(path: string): void {
    if (path === '...') {
      if (this.selectedProductName === '' || this.selectedProductName === '/') {
        this.getFolders('/');
      } else {
        const result = this.selectedProductName.substring(0, this.selectedProductName.lastIndexOf('/')) || '/';
        this.selectedProductName = result;
        this.getFolders(result);
      }
    } else {
      this.selectedProductName = path;
      this.getFolders(path);
    }
  }

  onSelect(): void {
    this.dynamicDialogRef.close(this.selectedProductName);
  }
}
