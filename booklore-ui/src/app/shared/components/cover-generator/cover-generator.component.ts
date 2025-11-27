import {Component, Input} from '@angular/core';

@Component({
  standalone: true,
  template: ''
})
export class CoverGeneratorComponent {
  @Input() title: string = '';
  @Input() author: string = '';

  private wrapText(text: string, maxLineLength: number): string[] {
    const words = text.split(' ');
    const lines: string[] = [];
    let currentLine = '';

    words.forEach(word => {
      if (word.length > maxLineLength) {
        if (currentLine.length > 0) {
          lines.push(currentLine);
          currentLine = '';
        }
        lines.push(word);
      } else if (currentLine.length + word.length + 1 > maxLineLength) {
        lines.push(currentLine);
        currentLine = word;
      } else {
        currentLine += (currentLine.length > 0 ? ' ' : '') + word;
      }
    });

    if (currentLine.length > 0) {
      lines.push(currentLine);
    }

    return lines;
  }

  private truncateText(text: string, maxLength: number): string {
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength - 3) + '...';
  }

  private calculateTitleFontSize(lineCount: number): number {
    if (lineCount <= 2) return 36;
    if (lineCount === 3) return 30;
    return 24;
  }

  private calculateAuthorFontSize(lineCount: number): number {
    if (lineCount <= 2) return 28;
    return 22;
  }

  generateCover(): string {
    const maxTitleLength = 60;
    const maxAuthorLength = 40;
    const truncatedTitle = this.truncateText(this.title, maxTitleLength);
    const truncatedAuthor = this.truncateText(this.author, maxAuthorLength);

    const maxLineLength = 12;
    const maxTitleLines = 4;
    const maxAuthorLines = 3;

    let titleLines = this.wrapText(truncatedTitle, maxLineLength);
    let authorLines = this.wrapText(truncatedAuthor, maxLineLength);

    if (titleLines.length > maxTitleLines) {
      titleLines = titleLines.slice(0, maxTitleLines);
      titleLines[maxTitleLines - 1] = this.truncateText(titleLines[maxTitleLines - 1], maxLineLength);
    }

    if (authorLines.length > maxAuthorLines) {
      authorLines = authorLines.slice(0, maxAuthorLines);
      authorLines[maxAuthorLines - 1] = this.truncateText(authorLines[maxAuthorLines - 1], maxLineLength);
    }

    const titleFontSize = this.calculateTitleFontSize(titleLines.length);
    const authorFontSize = this.calculateAuthorFontSize(authorLines.length);

    const titleLineHeight = titleFontSize * 1.2;
    const titlePadding = 15;
    const titleBoxHeight = titleLines.length * titleLineHeight + (titlePadding * 2);

    const titleElements = titleLines.map((line, index) => {
      const y = 40 + titlePadding + titleFontSize + (index * titleLineHeight);
      return `<text x="20" y="${y}" font-family="serif" font-size="${titleFontSize}" fill="#000000">${line}</text>`;
    }).join('\n');

    const titleWithBackground = `
      <rect x="0" y="40" width="100%" height="${titleBoxHeight}" fill="url(#titleGradient)" />
      ${titleElements}
    `;

    const authorLineHeight = authorFontSize * 1.2;
    const authorStartY = 330 - (authorLines.length - 1) * authorLineHeight;

    const authorElements = authorLines.map((line, index) => {
      const y = authorStartY + index * authorLineHeight;
      return `<text x="230" y="${y}" text-anchor="end" font-family="sans-serif" font-size="${authorFontSize}" fill="#000000">${line}</text>`;
    }).join('\n');

    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="250" height="350" viewBox="0 0 250 350">
        <defs>
        <linearGradient id="titleGradient" >
          <stop style="stop-color:#dddddd;stop-opacity:1;" offset="0" id="stop1" />
          <stop style="stop-color:#dfdfdf;stop-opacity:0.6;" offset="1" id="stop2" />
        </linearGradient>
        <linearGradient id="pageGradient">
          <stop style="stop-color:#557766;stop-opacity:1;" offset="0" id="stop8" />
          <stop style="stop-color:#669988;stop-opacity:1;" offset="1" id="stop9" />
        </linearGradient>
        </defs>
        <rect width="100%" height="100%" fill="url(#pageGradient)" />
        ${titleWithBackground}
        ${authorElements}
      </svg>
    `;

    const base64 = btoa(encodeURIComponent(svg).replace(/%([0-9A-F]{2})/g, (match, p1) => {
      return String.fromCharCode(parseInt(p1, 16));
    }));

    return `data:image/svg+xml;base64,${base64}`;
  }
}
