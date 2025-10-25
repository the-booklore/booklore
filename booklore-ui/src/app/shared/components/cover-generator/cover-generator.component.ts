
import { Component, Input } from '@angular/core';

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

  generateCover(): string {
    const maxLineLength = 10;
    const titleLines = this.wrapText(this.title, maxLineLength);
    const authorLines = this.wrapText(this.author, maxLineLength);

    const titleBoxHeight = titleLines.length * 40 + 20;
    const titleElements = titleLines.map((line, index) => {
      const y = 80 + index * 40;
      return `<text x="20" y="${y}" font-family="serif" font-size="36" fill="#000000">${line}</text>`;
    }).join('\n');

    const titleWithBackground = `
      <rect x="0" y="40" width="100%" height="${titleBoxHeight}" fill="url(#titleGradient)" />
      ${titleElements}
    `;

    const authorElements = authorLines.map((line, index) => {
      const y = 364 - (authorLines.length - index - 1) * 36;
      return `<text x="236" y="${y}" text-anchor="end" font-family="sans-serif" font-size="28" fill="#000000">${line}</text>`;
    }).join('\n');

    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="256" height="384" viewBox="0 0 256 384">
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
    return `data:image/svg+xml;base64,${btoa(svg)}`;
  }
}
