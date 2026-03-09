import {Component, inject} from '@angular/core';
import {Button} from 'primeng/button';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {TranslocoDirective} from '@jsverse/transloco';

@Component({
  selector: 'app-github-support-dialog',
  imports: [
    Button,
    TranslocoDirective
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
