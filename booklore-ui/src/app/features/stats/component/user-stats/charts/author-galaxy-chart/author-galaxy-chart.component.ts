import {Component, ElementRef, inject, OnDestroy, OnInit, ViewChild, AfterViewInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Tooltip} from 'primeng/tooltip';
import {Subject} from 'rxjs';
import {filter, first, takeUntil} from 'rxjs/operators';
import {BookService} from '../../../../../book/service/book.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface AuthorNode {
  id: string;
  name: string;
  bookCount: number;
  avgRating: number;
  genres: Set<string>;
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  color: string;
}

interface GenreEdge {
  source: AuthorNode;
  target: AuthorNode;
  sharedGenres: number;
}

@Component({
  selector: 'app-author-galaxy-chart',
  standalone: true,
  imports: [CommonModule, Tooltip, TranslocoDirective],
  templateUrl: './author-galaxy-chart.component.html',
  styleUrls: ['./author-galaxy-chart.component.scss']
})
export class AuthorGalaxyChartComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild('galaxyCanvas', {static: false}) canvasRef!: ElementRef<HTMLCanvasElement>;

  private readonly bookService = inject(BookService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  public hasData = false;
  public topAuthorName = '';
  public authorCount = 0;
  public connectionCount = 0;

  private nodes: AuthorNode[] = [];
  private edges: GenreEdge[] = [];
  private animationFrame = 0;
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
    if (this.animationFrame) cancelAnimationFrame(this.animationFrame);
  }

  private tryRender(): void {
    if (this.canvasReady && this.dataReady && this.hasData) {
      requestAnimationFrame(() => this.runSimulation());
    }
  }

  private processData(): void {
    const currentState = this.bookService.getCurrentBookState();
    const books = currentState.books;
    if (!books || books.length === 0) return;

    const authorMap = new Map<string, {bookCount: number; totalRating: number; ratedCount: number; genres: Set<string>}>();

    for (const book of books) {
      const authors = book.metadata?.authors;
      const categories = book.metadata?.categories || [];
      const rating = book.personalRating;
      if (!authors || authors.length === 0) continue;

      for (const author of authors) {
        const key = author.toLowerCase().trim();
        if (!authorMap.has(key)) {
          authorMap.set(key, {bookCount: 0, totalRating: 0, ratedCount: 0, genres: new Set()});
        }
        const entry = authorMap.get(key)!;
        entry.bookCount++;
        if (rating && rating > 0) {
          entry.totalRating += rating;
          entry.ratedCount++;
        }
        categories.forEach(cat => entry.genres.add(cat.toLowerCase()));
      }
    }

    const sorted = [...authorMap.entries()]
      .sort((a, b) => b[1].bookCount - a[1].bookCount)
      .slice(0, 30);

    if (sorted.length < 2) return;

    this.hasData = true;

    const ratingColors = [
      '#ef5350', '#ef5350', '#ff7043', '#ff9800', '#ffc107',
      '#cddc39', '#8bc34a', '#66bb6a', '#26a69a', '#42a5f5', '#42a5f5'
    ];

    this.nodes = sorted.map(([name, data]) => {
      const avgRating = data.ratedCount > 0 ? data.totalRating / data.ratedCount : 5;
      const colorIdx = Math.min(10, Math.max(0, Math.round(avgRating)));
      const displayName = name.split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
      return {
        id: name,
        name: displayName,
        bookCount: data.bookCount,
        avgRating: Math.round(avgRating * 10) / 10,
        genres: data.genres,
        x: 0, y: 0,
        vx: 0, vy: 0,
        radius: Math.max(6, Math.min(28, 4 + data.bookCount * 3)),
        color: ratingColors[colorIdx]
      };
    });

    // Place nodes in a circle initially for better starting positions
    const n = this.nodes.length;
    this.nodes.forEach((node, i) => {
      const angle = (i / n) * Math.PI * 2;
      const r = 150 + Math.random() * 50;
      node.x = 400 + Math.cos(angle) * r;
      node.y = 250 + Math.sin(angle) * r;
    });

    // Only create edges for 2+ shared genres to reduce clutter
    this.edges = [];
    for (let i = 0; i < this.nodes.length; i++) {
      for (let j = i + 1; j < this.nodes.length; j++) {
        const shared = [...this.nodes[i].genres].filter(g => this.nodes[j].genres.has(g)).length;
        if (shared >= 2) {
          this.edges.push({source: this.nodes[i], target: this.nodes[j], sharedGenres: shared});
        }
      }
    }

    this.topAuthorName = this.nodes[0]?.name || '';
    this.authorCount = this.nodes.length;
    this.connectionCount = this.edges.length;
  }

  private runSimulation(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const rect = canvas.parentElement!.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    canvas.width = rect.width * dpr;
    canvas.height = 500 * dpr;
    canvas.style.width = rect.width + 'px';
    canvas.style.height = '500px';
    ctx.scale(dpr, dpr);

    const width = rect.width;
    const height = 500;
    const cx = width / 2;
    const cy = height / 2;
    let iteration = 0;
    const maxIterations = 200;

    // Re-center initial positions
    this.nodes.forEach((node, i) => {
      const angle = (i / this.nodes.length) * Math.PI * 2;
      const r = Math.min(width, height) * 0.3 + Math.random() * 30;
      node.x = cx + Math.cos(angle) * r;
      node.y = cy + Math.sin(angle) * r;
      node.vx = 0;
      node.vy = 0;
    });

    const simulate = () => {
      if (iteration >= maxIterations) {
        this.draw(ctx, width, height);
        return;
      }

      const alpha = Math.max(0.01, 1 - iteration / maxIterations);
      const margin = 40;

      // Strong repulsion between all nodes
      for (let i = 0; i < this.nodes.length; i++) {
        for (let j = i + 1; j < this.nodes.length; j++) {
          const dx = this.nodes[j].x - this.nodes[i].x;
          const dy = this.nodes[j].y - this.nodes[i].y;
          const dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
          const minDist = this.nodes[i].radius + this.nodes[j].radius + 30;
          const force = (500 * alpha) / (dist * dist) * Math.max(1, minDist / dist);
          const fx = (dx / dist) * force;
          const fy = (dy / dist) * force;
          this.nodes[i].vx -= fx;
          this.nodes[i].vy -= fy;
          this.nodes[j].vx += fx;
          this.nodes[j].vy += fy;
        }
      }

      // Gentle attraction along edges
      for (const edge of this.edges) {
        const dx = edge.target.x - edge.source.x;
        const dy = edge.target.y - edge.source.y;
        const dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
        const idealDist = 100 + 50 / edge.sharedGenres;
        const force = (dist - idealDist) * 0.003 * alpha;
        const fx = (dx / dist) * force;
        const fy = (dy / dist) * force;
        edge.source.vx += fx;
        edge.source.vy += fy;
        edge.target.vx -= fx;
        edge.target.vy -= fy;
      }

      // Gentle center gravity
      for (const node of this.nodes) {
        node.vx += (cx - node.x) * 0.002 * alpha;
        node.vy += (cy - node.y) * 0.002 * alpha;
        node.vx *= 0.75;
        node.vy *= 0.75;
        node.x += node.vx;
        node.y += node.vy;
        node.x = Math.max(node.radius + margin, Math.min(width - node.radius - margin, node.x));
        node.y = Math.max(node.radius + margin, Math.min(height - node.radius - margin, node.y));
      }

      iteration++;
      if (iteration % 3 === 0 || iteration >= maxIterations - 1) {
        this.draw(ctx, width, height);
      }
      this.animationFrame = requestAnimationFrame(simulate);
    };

    simulate();
  }

  private draw(ctx: CanvasRenderingContext2D, width: number, height: number): void {
    ctx.clearRect(0, 0, width, height);

    // Draw edges as subtle curves
    for (const edge of this.edges) {
      const opacity = Math.min(0.25, 0.06 + edge.sharedGenres * 0.04);
      const lineWidth = Math.min(2.5, 0.5 + edge.sharedGenres * 0.5);
      ctx.beginPath();
      ctx.moveTo(edge.source.x, edge.source.y);
      const mx = (edge.source.x + edge.target.x) / 2;
      const my = (edge.source.y + edge.target.y) / 2 - 15;
      ctx.quadraticCurveTo(mx, my, edge.target.x, edge.target.y);
      ctx.strokeStyle = `rgba(255, 255, 255, ${opacity})`;
      ctx.lineWidth = lineWidth;
      ctx.stroke();
    }

    // Draw nodes with glow for larger ones
    for (const node of this.nodes) {
      if (node.radius > 14) {
        ctx.beginPath();
        ctx.arc(node.x, node.y, node.radius + 4, 0, Math.PI * 2);
        ctx.fillStyle = node.color + '18';
        ctx.fill();
      }

      ctx.beginPath();
      ctx.arc(node.x, node.y, node.radius, 0, Math.PI * 2);
      ctx.fillStyle = node.color;
      ctx.globalAlpha = 0.88;
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.25)';
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }

    // Draw labels only for top authors (by book count, i.e. larger nodes)
    const labelNodes = this.nodes
      .filter(n => n.radius >= 10)
      .sort((a, b) => b.bookCount - a.bookCount)
      .slice(0, 15);

    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    for (const node of labelNodes) {
      const fontSize = Math.max(9, Math.min(12, node.radius * 0.5 + 3));
      ctx.font = `${fontSize}px Inter, sans-serif`;
      const displayName = node.name.length > 16 ? node.name.substring(0, 14) + '..' : node.name;

      ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
      ctx.fillText(displayName, node.x + 1, node.y + node.radius + 5);
      ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
      ctx.fillText(displayName, node.x, node.y + node.radius + 4);
    }
  }
}
