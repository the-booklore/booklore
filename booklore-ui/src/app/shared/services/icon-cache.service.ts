import {Injectable} from '@angular/core';
import {SafeHtml} from '@angular/platform-browser';
import {Observable, BehaviorSubject} from 'rxjs';

interface CachedIcon {
  content: string;
  sanitized: SafeHtml;
  timestamp: number;
}

@Injectable({
  providedIn: 'root'
})
export class IconCacheService {
  private cache = new Map<string, CachedIcon>();
  private readonly CACHE_DURATION_MS = 1000 * 60 * 60;

  private cacheUpdate$ = new BehaviorSubject<string | null>(null);

  getCachedContent(iconName: string): string | null {
    const cached = this.cache.get(iconName);

    if (!cached) {
      return null;
    }

    if (Date.now() - cached.timestamp > this.CACHE_DURATION_MS) {
      this.cache.delete(iconName);
      return null;
    }

    return cached.content;
  }

  getCachedSanitized(iconName: string): SafeHtml | null {
    const cached = this.cache.get(iconName);

    if (!cached) {
      return null;
    }

    if (Date.now() - cached.timestamp > this.CACHE_DURATION_MS) {
      this.cache.delete(iconName);
      return null;
    }

    return cached.sanitized;
  }

  cacheIcon(iconName: string, content: string, sanitized: SafeHtml): void {
    this.cache.set(iconName, {
      content,
      sanitized,
      timestamp: Date.now()
    });
    this.cacheUpdate$.next(iconName);
  }

  isCached(iconName: string): boolean {
    const cached = this.cache.get(iconName);

    if (!cached) {
      return false;
    }

    if (Date.now() - cached.timestamp > this.CACHE_DURATION_MS) {
      this.cache.delete(iconName);
      return false;
    }

    return true;
  }

  invalidate(iconName: string): void {
    this.cache.delete(iconName);
    this.cacheUpdate$.next(null);
  }

  invalidateMultiple(iconNames: string[]): void {
    iconNames.forEach(name => this.cache.delete(name));
    this.cacheUpdate$.next(null);
  }

  clearCache(): void {
    this.cache.clear();
    this.cacheUpdate$.next(null);
  }

  getCacheUpdates(): Observable<string | null> {
    return this.cacheUpdate$.asObservable();
  }
}

