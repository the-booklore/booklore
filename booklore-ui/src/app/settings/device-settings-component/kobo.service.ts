import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../config/api-config';

export interface KoboSyncSettings {
  token: string;
}

@Injectable({
  providedIn: 'root'
})
export class KoboService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/kobo-settings`;
  private readonly http = inject(HttpClient);

  createOrUpdateToken(): Observable<KoboSyncSettings> {
    return this.http.put<KoboSyncSettings>(`${this.baseUrl}`, null);
  }

  getUser(): Observable<KoboSyncSettings> {
    return this.http.get<KoboSyncSettings>(`${this.baseUrl}`);
  }
}
