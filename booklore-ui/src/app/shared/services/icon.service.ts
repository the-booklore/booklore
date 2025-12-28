import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, of} from 'rxjs';
import {finalize, map, shareReplay, tap} from 'rxjs/operators';
import {API_CONFIG} from '../../core/config/api-config';
import {IconCacheService} from './icon-cache.service';
import {DomSanitizer, SafeHtml} from '@angular/platform-browser';

interface SvgIconData {
  svgName: string;
  svgData: string;
}

interface IconSaveResult {
  iconName: string;
  success: boolean;
  errorMessage: string;
}

interface SvgIconBatchResponse {
  totalRequested: number;
  successCount: number;
  failureCount: number;
  results: IconSaveResult[];
}

interface IconContentMap {
  [iconName: string]: string;
}

@Injectable({
  providedIn: 'root'
})
export class IconService {

  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/icons`;
  private requestCache = new Map<string, Observable<string>>();
  private preloadCache$: Observable<void> | null = null;

  private http = inject(HttpClient);
  private iconCache = inject(IconCacheService);
  private sanitizer = inject(DomSanitizer);

  preloadAllIcons(): Observable<void> {
    if (this.preloadCache$) {
      return this.preloadCache$;
    }

    this.preloadCache$ = this.http.get<IconContentMap>(`${this.baseUrl}/all/content`).pipe(
      tap((iconsMap) => {
        Object.entries(iconsMap).forEach(([iconName, content]) => {
          const sanitized = this.sanitizer.bypassSecurityTrustHtml(content);
          this.iconCache.cacheIcon(iconName, content, sanitized);
        });
      }),
      map(() => void 0),
      shareReplay({bufferSize: 1, refCount: false}),
      finalize(() => this.preloadCache$ = null)
    );

    return this.preloadCache$;
  }

  getSvgIconContent(iconName: string): Observable<string> {
    const cached = this.iconCache.getCachedSanitized(iconName);
    if (cached) {
      return of('');
    }

    if (!this.requestCache.has(iconName)) {
      const request$ = this.http.get(`${this.baseUrl}/${encodeURIComponent(iconName)}/content`, {
        responseType: 'text'
      }).pipe(
        tap(content => {
          const sanitized = this.sanitizer.bypassSecurityTrustHtml(content);
          this.iconCache.cacheIcon(iconName, content, sanitized);
        }),
        shareReplay({bufferSize: 1, refCount: true}),
        finalize(() => this.requestCache.delete(iconName))
      );

      this.requestCache.set(iconName, request$);
    }

    return this.requestCache.get(iconName)!;
  }

  getSanitizedSvgContent(iconName: string): Observable<SafeHtml> {
    const cached = this.iconCache.getCachedSanitized(iconName);
    if (cached) {
      return of(cached);
    }

    return new Observable<SafeHtml>(observer => {
      this.getSvgIconContent(iconName).subscribe({
        next: () => {
          const sanitized = this.iconCache.getCachedSanitized(iconName);
          if (sanitized) {
            observer.next(sanitized);
            observer.complete();
          }
        },
        error: (err) => observer.error(err)
      });
    });
  }

  deleteSvgIcon(svgName: string): Observable<any> {
    return this.http.delete(`${this.baseUrl}/${encodeURIComponent(svgName)}`).pipe(
      tap(() => {
        this.iconCache.removeIcon(svgName);
        this.requestCache.delete(svgName);
      })
    );
  }

  saveBatchSvgIcons(icons: SvgIconData[]): Observable<SvgIconBatchResponse> {
    return this.http.post<SvgIconBatchResponse>(`${this.baseUrl}/batch`, {icons}).pipe(
      tap((response) => {
        response.results.forEach(result => {
          if (result.success) {
            const iconData = icons.find(icon => icon.svgName === result.iconName);
            if (iconData) {
              const sanitized = this.sanitizer.bypassSecurityTrustHtml(iconData.svgData);
              this.iconCache.cacheIcon(iconData.svgName, iconData.svgData, sanitized);
            }
          }
        });
      })
    );
  }
}
