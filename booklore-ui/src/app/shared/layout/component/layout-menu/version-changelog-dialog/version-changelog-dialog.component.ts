import { Component, inject, OnInit } from '@angular/core';
import { ReleaseNote, VersionService } from '../../../../service/version.service';

import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import showdown from 'showdown';
import {DatePipe} from '@angular/common';

@Component({
  selector: 'app-version-changelog-dialog',
  standalone: true,
  imports: [
    DatePipe
  ],
  templateUrl: './version-changelog-dialog.component.html',
  styleUrl: './version-changelog-dialog.component.scss'
})
export class VersionChangelogDialogComponent implements OnInit {

  private versionService = inject(VersionService);
  private sanitizer = inject(DomSanitizer);

  changelog: ReleaseNote[] = [];
  loading = true;

  private converter = new showdown.Converter({ tables: true, emoji: true });

  ngOnInit(): void {
    this.versionService.getChangelog().subscribe({
      next: (data) => {
        this.changelog = data;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  markdownToHtml(markdown: string): SafeHtml {
    let html = this.converter.makeHtml(markdown);
    html = html.replace(/<h2\b([^>]*)>/g, '<h3$1>').replace(/<\/h2>/g, '</h3>');
    return this.sanitizer.bypassSecurityTrustHtml(html);
  }
}
