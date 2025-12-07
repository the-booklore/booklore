import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';

interface PageResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({
  providedIn: 'root'
})
export class IconService {

  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/icons`;

  private http = inject(HttpClient);

  saveSvgIcon(svgContent: string, svgName: string): Observable<any> {
    return this.http.post(this.baseUrl, {
      svgData: svgContent,
      svgName: svgName
    });
  }

  getIconNames(page: number = 0, size: number = 50): Observable<PageResponse<string>> {
    return this.http.get<PageResponse<string>>(this.baseUrl, {
      params: { page: page.toString(), size: size.toString() }
    });
  }

  deleteSvgIcon(svgName: string): Observable<any> {
    return this.http.delete(`${this.baseUrl}/${encodeURIComponent(svgName)}`);
  }
}
