import {Component, inject} from '@angular/core';
import {Button} from 'primeng/button';
import {DynamicDialogRef} from 'primeng/dynamicdialog';

@Component({
  selector: 'app-github-support-dialog',
  imports: [
    Button
  ],
  templateUrl: './github-support-dialog.html',
  styleUrls: ['./github-support-dialog.scss']
})
export class GithubSupportDialog {
  private dialogRef = inject(DynamicDialogRef);

  close(): void {
    this.dialogRef.close();
  }
}
