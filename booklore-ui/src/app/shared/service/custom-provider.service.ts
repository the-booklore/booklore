import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';
import {CustomMetadataProviderConfig, ExternalProviderCapabilities} from '../model/app-settings.model';

@Injectable({providedIn: 'root'})
export class CustomProviderService {
  private http = inject(HttpClient);
  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1/custom-providers`;

  /**
   * Validates a custom provider by testing the connection and fetching capabilities.
   * Returns the provider's capabilities on success, or errors with HTTP 502 if unreachable.
   */
  validateProvider(config: CustomMetadataProviderConfig): Observable<ExternalProviderCapabilities> {
    return this.http.post<ExternalProviderCapabilities>(`${this.apiUrl}/validate`, config);
  }

  /**
   * Triggers a refresh of the custom provider registry on the backend.
   */
  refreshRegistry(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/refresh`, null);
  }
}
