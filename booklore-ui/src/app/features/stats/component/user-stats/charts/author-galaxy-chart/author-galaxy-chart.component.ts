import {Component, ElementRef, inject, OnDestroy, OnInit, ViewChild, AfterViewInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Tooltip} from 'primeng/tooltip';
import {Subject} from 'rxjs';
import {filter, first, takeUntil} from 'rxjs/operators';
import {BookService} from '../../../../../book/service/book.service';
import {Book} from '../../../../../book/model/book.model';
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
      .pipe(
        filter(state => state.loaded),
        first(),
        takeUntil(this.destroy$)
      )
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
    if (this.animationFrame) {
      cancelAnimationFrame(this.animationFrame);
    }
  }

  private tryRender(): void {
    if (this.canvasReady && this.dataReady && this.hasData) {
      this.runSimulation();
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

    this.nodes = sorted.map(([name, data], i) => {
      const avgRating = data.ratedCount > 0 ? data.totalRating / data.ratedCount : 5;
      const colorIdx = Math.min(10, Math.max(0, Math.round(avgRating)));
      return {
        id: name,
        name: sorted[i][0].split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' '),
        bookCount: data.bookCount,
        avgRating: Math.round(avgRating * 10) / 10,
        genres: data.genres,
        x: Math.random() * 600 + 100,
        y: Math.random() * 400 + 50,
        vx: 0,
        vy: 0,
        radius: Math.max(8, Math.min(30, data.bookCount * 4)),
        color: ratingColors[colorIdx]
      };
    });

    this.edges = [];
    for (let i = 0; i < this.nodes.length; i++) {
      for (let j = i + 1; j < this.nodes.length; j++) {
        const shared = [...this.nodes[i].genres].filter(g => this.nodes[j].genres.has(g)).length;
        if (shared > 0) {
          this.edges.push({source: this.nodes[i], target: this.nodes[j], sharedGenres: shared});
        }
      }
    }

    this.topAuthorName = this.nodes[0]?.name || '';
    this.authorCount = this.nodes.length;
    this.connectionCount = this.edges.length;
  }

  private runSimulation(): void {
    const canvas = this.canvasRef.nativeElement;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const rect = canvas.parentElement!.getBoundingClientRect();
    canvas.width = rect.width * (window.devicePixelRatio || 1);
    canvas.height = 500 * (window.devicePixelRatio || 1);
    canvas.style.width = rect.width + 'px';
    canvas.style.height = '500px';
    ctx.scale(window.devicePixelRatio || 1, window.devicePixelRatio || 1);

    const width = rect.width;
    const height = 500;
    let iteration = 0;

    const simulate = () => {
      if (iteration >= 120) {
        this.draw(ctx, width, height);
        return;
      }

      const alpha = 1 - iteration / 120;

      // Repulsion between nodes
      for (let i = 0; i < this.nodes.length; i++) {
        for (let j = i + 1; j < this.nodes.length; j++) {
          const dx = this.nodes[j].x - this.nodes[i].x;
          const dy = this.nodes[j].y - this.nodes[i].y;
          const dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
          const force = (200 * alpha) / dist;
          const fx = (dx / dist) * force;
          const fy = (dy / dist) * force;
          this.nodes[i].vx -= fx;
          this.nodes[i].vy -= fy;
          this.nodes[j].vx += fx;
          this.nodes[j].vy += fy;
        }
      }

      // Attraction along edges
      for (const edge of this.edges) {
        const dx = edge.target.x - edge.source.x;
        const dy = edge.target.y - edge.source.y;
        const dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
        const force = (dist - 120) * 0.01 * alpha * edge.sharedGenres;
        const fx = (dx / dist) * force;
        const fy = (dy / dist) * force;
        edge.source.vx += fx;
        edge.source.vy += fy;
        edge.target.vx -= fx;
        edge.target.vy -= fy;
      }

      // Center gravity
      for (const node of this.nodes) {
        node.vx += (width / 2 - node.x) * 0.005 * alpha;
        node.vy += (height / 2 - node.y) * 0.005 * alpha;
        node.vx *= 0.8;
        node.vy *= 0.8;
        node.x += node.vx;
        node.y += node.vy;
        node.x = Math.max(node.radius + 5, Math.min(width - node.radius - 5, node.x));
        node.y = Math.max(node.radius + 5, Math.min(height - node.radius - 5, node.y));
      }

      iteration++;
      this.draw(ctx, width, height);
      this.animationFrame = requestAnimationFrame(simulate);
    };

    simulate();
  }

  private draw(ctx: CanvasRenderingContext2D, width: number, height: number): void {
    ctx.clearRect(0, 0, width, height);

    // Draw edges
    for (const edge of this.edges) {
      ctx.beginPath();
      ctx.moveTo(edge.source.x, edge.source.y);
      ctx.lineTo(edge.target.x, edge.target.y);
      ctx.strokeStyle = `rgba(255, 255, 255, ${Math.min(0.3, edge.sharedGenres * 0.08)})`;
      ctx.lineWidth = Math.min(3, edge.sharedGenres * 0.8);
      ctx.stroke();
    }

    // Draw nodes
    for (const node of this.nodes) {
      ctx.beginPath();
      ctx.arc(node.x, node.y, node.radius, 0, Math.PI * 2);
      ctx.fillStyle = node.color;
      ctx.globalAlpha = 0.85;
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.3)';
      ctx.lineWidth = 1.5;
      ctx.stroke();

      // Label for larger nodes
      if (node.radius >= 12) {
        ctx.fillStyle = '#ffffff';
        ctx.font = `${Math.max(9, Math.min(12, node.radius * 0.6))}px Inter, sans-serif`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        const displayName = node.name.length > 14 ? node.name.substring(0, 12) + '...' : node.name;
        ctx.fillText(displayName, node.x, node.y + node.radius + 14);
      }
    }
  }
}
