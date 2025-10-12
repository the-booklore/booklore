import { Component } from '@angular/core';
import {Button} from 'primeng/button';

@Component({
  selector: 'app-github-support-dialog',
  imports: [
    Button
  ],
  templateUrl: './github-support-dialog.html',
  styleUrl: './github-support-dialog.scss'
})
export class GithubSupportDialog {
  openGithub(): void {
    window.open('https://github.com/adityachandelgit/booklore', '_blank');
  }
}
