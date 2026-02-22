import {Component, ElementRef, inject, OnDestroy, OnInit, ViewChild, AfterViewInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Tooltip} from 'primeng/tooltip';
import {Subject} from 'rxjs';
import {filter, first, takeUntil} from 'rxjs/operators';
import {BookService} from '../../../../../book/service/book.service';
import {Book} from '../../../../../book/model/book.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface FormatNode {
  id: string;
  label: string;
  x: number;
  color: string;
  count: number;
}

interface FormatArc {
  source: FormatNode;
  target: FormatNode;
  count: number;
}

@Component({
  selector: 'app-format-chord-chart',
  standalone: true,
  imports: [CommonModule, Tooltip, TranslocoDirective],
  templateUrl: './format-chord-chart.component.html',
  styleUrls: ['./format-chord-chart.component.scss']
})
export class FormatChordChartComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild('chordCanvas', {static: false}) canvasRef!: ElementRef<HTMLCanvasElement>;

  private readonly bookService = inject(BookService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  public hasData = false;
  public formatCount = 0;
  public connectionCount = 0;
  public mostConnected = '';

  private nodes: FormatNode[] = [];
  private arcs: FormatArc[] = [];
  private canvasReady = false;
  private dataReady = false;

  ngOnInit(): void {
    this.bookService.bookState$
      .pipe(filter(state => state.loaded), first(), takeUntil(this.destroy$))
      .subscribe(() => {
        this.processData();
        this.dataReady = true;
        this.tryRender();
      });
  }

  ngAfterViewInit(): void {
    this.canvasReady = true;
    this.tryRender();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private tryRender(): void {
    if (this.canvasReady && this.dataReady && this.hasData) {
      this.draw();
    }
  }

  private processData(): void {
    const state = this.bookService.getCurrentBookState();
    const books = state.books;
    if (!books || books.length === 0) return;

    const formatDefs: {id: string; label: string; color: string; check: (b: Book) => boolean}[] = [
      {id: 'epub', label: 'EPUB', color: '#42a5f5', check: b => (b.epubProgress?.percentage || 0) > 0},
      {id: 'pdf', label: 'PDF', color: '#ef5350', check: b => (b.pdfProgress?.percentage || 0) > 0},
      {id: 'cbx', label: 'CBX', color: '#66bb6a', check: b => (b.cbxProgress?.percentage || 0) > 0},
      {id: 'audiobook', label: 'Audiobook', color: '#ff9800', check: b => (b.audiobookProgress?.percentage || 0) > 0},
      {id: 'koreader', label: 'KoReader', color: '#ab47bc', check: b => (b.koreaderProgress?.percentage || 0) > 0},
      {id: 'kobo', label: 'Kobo', color: '#26c6da', check: b => (b.koboProgress?.percentage || 0) > 0}
    ];

    const formatCounts = new Map<string, number>();
    const pairCounts = new Map<string, number>();

    for (const book of books) {
      const activeFormats = formatDefs.filter(f => f.check(book)).map(f => f.id);
      for (const f of activeFormats) {
        formatCounts.set(f, (formatCounts.get(f) || 0) + 1);
      }
      for (let i = 0; i < activeFormats.length; i++) {
        for (let j = i + 1; j < activeFormats.length; j++) {
          const key = [activeFormats[i], activeFormats[j]].sort().join('|');
          pairCounts.set(key, (pairCounts.get(key) || 0) + 1);
        }
      }
    }

    const activeFormats = formatDefs.filter(f => (formatCounts.get(f.id) || 0) > 0);
    if (activeFormats.length < 2) return;

    this.hasData = true;
    this.formatCount = activeFormats.length;

    const spacing = 120;
    const startX = 60;
    this.nodes = activeFormats.map((f, i) => ({
      id: f.id,
      label: f.label,
      x: startX + i * spacing,
      color: f.color,
      count: formatCounts.get(f.id) || 0
    }));

    this.arcs = [];
    const nodeMap = new Map(this.nodes.map(n => [n.id, n]));

    for (const [key, count] of pairCounts) {
      const [srcId, tgtId] = key.split('|');
      const src = nodeMap.get(srcId);
      const tgt = nodeMap.get(tgtId);
      if (src && tgt) {
        this.arcs.push({source: src, target: tgt, count});
      }
    }

    this.connectionCount = this.arcs.length;

    const connCounts = new Map<string, number>();
    for (const arc of this.arcs) {
      connCounts.set(arc.source.id, (connCounts.get(arc.source.id) || 0) + arc.count);
      connCounts.set(arc.target.id, (connCounts.get(arc.target.id) || 0) + arc.count);
    }
    const mostConn = [...connCounts.entries()].sort((a, b) => b[1] - a[1])[0];
    this.mostConnected = mostConn ? (nodeMap.get(mostConn[0])?.label || '') : '';
  }

  private draw(): void {
    const canvas = this.canvasRef.nativeElement;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const rect = canvas.parentElement!.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const width = rect.width;
    const height = 280;
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';
    ctx.scale(dpr, dpr);

    const nodeY = height * 0.75;
    const totalWidth = (this.nodes.length - 1) * 100;
    const offsetX = (width - totalWidth) / 2;

    for (let i = 0; i < this.nodes.length; i++) {
      this.nodes[i].x = offsetX + i * 100;
    }

    const maxArcCount = Math.max(...this.arcs.map(a => a.count), 1);

    for (const arc of this.arcs) {
      const x1 = arc.source.x;
      const x2 = arc.target.x;
      const midX = (x1 + x2) / 2;
      const radius = Math.abs(x2 - x1) / 2;
      const thickness = Math.max(1, (arc.count / maxArcCount) * 8);

      ctx.beginPath();
      ctx.arc(midX, nodeY, radius, Math.PI, 0);
      ctx.strokeStyle = arc.source.color + '60';
      ctx.lineWidth = thickness;
      ctx.stroke();

      ctx.fillStyle = 'rgba(255, 255, 255, 0.7)';
      ctx.font = '10px Inter, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(String(arc.count), midX, nodeY - radius - 5);
    }

    for (const node of this.nodes) {
      const radius = Math.max(12, Math.min(22, node.count * 2));

      ctx.beginPath();
      ctx.arc(node.x, nodeY, radius, 0, Math.PI * 2);
      ctx.fillStyle = node.color;
      ctx.globalAlpha = 0.9;
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.3)';
      ctx.lineWidth = 1.5;
      ctx.stroke();

      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 10px Inter, sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(String(node.count), node.x, nodeY);

      ctx.font = '11px Inter, sans-serif';
      ctx.fillText(node.label, node.x, nodeY + radius + 16);
    }
  }
}
