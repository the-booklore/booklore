import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_CONFIG } from '../../core/config/api-config';

@Injectable({
  providedIn: 'root'
})
export class ConfigurationExportService {
  private apiUrl = `${API_CONFIG.BASE_URL}/api/v1/configuration`;

  constructor(private http: HttpClient) {}

  exportConfiguration(): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/export`, {
      responseType: 'blob'
    });
  }

  importConfiguration(
    jsonConfig: string,
    options: ImportOptions = {}
  ): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/import`, jsonConfig, {
      params: {
        skipExisting: options.skipExisting !== false,
        overwrite: options.overwrite === true,
        importShelves: options.importShelves !== false,
        importMagicShelves: options.importMagicShelves !== false,
        importSettings: options.importSettings !== false
      }
    });
  }

  getTemplate(): Observable<string> {
    return this.http.get(`${this.apiUrl}/template`, {
      responseType: 'text'
    });
  }
}

export interface ImportOptions {
  skipExisting?: boolean;
  overwrite?: boolean;
  importShelves?: boolean;
  importMagicShelves?: boolean;
  importSettings?: boolean;
}
