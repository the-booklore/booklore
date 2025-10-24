
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

    let y = 30;
    const titleElements = titleLines.map(line => {
      const element = `<text x="10" y="${y}" font-family="sans-serif" font-size="18" fill="#000000">${line}</text>`;
      y += 20;
      return element;
    }).join('');

    y += 20;
    const authorElements = authorLines.map(line => {
      const element = `<text x="10" y="${y}" font-family="sans-serif" font-size="14" fill="#000000">${line}</text>`;
      y += 18;
      return element;
    }).join('');

    const svg = `
      <svg xmlns="http://www.w3.org/2000/svg" width="128" height="192" viewBox="0 0 128 192">
        <rect width="100%" height="100%" fill="#cccccc" />
        ${titleElements}
        ${authorElements}
      </svg>
    `;
    return `data:image/svg+xml;base64,${btoa(svg)}`;
  }
}
