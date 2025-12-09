import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, of} from 'rxjs';
import {tap} from 'rxjs/operators';
import {API_CONFIG} from '../../core/config/api-config';
import {IconCacheService} from './icon-cache.service';
import {DomSanitizer, SafeHtml} from '@angular/platform-browser';

interface PageResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

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

@Injectable({
  providedIn: 'root'
})
export class IconService {

  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/icons`;

  private http = inject(HttpClient);
  private iconCache = inject(IconCacheService);
  private sanitizer = inject(DomSanitizer);

  saveSvgIcon(svgContent: string, svgName: string): Observable<any> {
    return this.http.post(this.baseUrl, {
      svgData: svgContent,
      svgName: svgName
    });
  }

  getIconNames(page: number = 0, size: number = 50): Observable<PageResponse<string>> {
    return this.http.get<PageResponse<string>>(this.baseUrl, {
      params: {page: page.toString(), size: size.toString()}
    });
  }

  getSvgIconContent(iconName: string): Observable<string> {
    const cached = this.iconCache.getCachedContent(iconName);
    if (cached) {
      return of(cached);
    }

    return this.http.get(`${this.baseUrl}/${encodeURIComponent(iconName)}/content`, {
      responseType: 'text'
    }).pipe(
      tap(content => {
        const sanitized = this.sanitizer.bypassSecurityTrustHtml(content);
        this.iconCache.cacheIcon(iconName, content, sanitized);
      })
    );
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
        this.iconCache.invalidate(svgName);
      })
    );
  }

  saveBatchSvgIcons(icons: SvgIconData[]): Observable<SvgIconBatchResponse> {
    return this.http.post<SvgIconBatchResponse>(`${this.baseUrl}/batch`, {icons}).pipe(
      tap((response) => {
        const successfulIcons = response.results
          .filter(result => result.success)
          .map(result => result.iconName);

        this.iconCache.invalidateMultiple(successfulIcons);
      })
    );
  }
}
