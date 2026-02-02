import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

interface DeviceProfile {
  value: string;
  displayName: string;
  width: number;
  height: number;
  supportsCustom: boolean;
}

interface ConversionRequest {
  bookId: number;
  deviceProfile: string;
  customWidth?: number;
  customHeight?: number;
  compressionPercentage?: number;
}

interface ConversionResponse {
  newBookId: number;
  fileName: string;
  fileSizeKb: number;
  targetWidth: number;
  targetHeight: number;
  pageCount?: number;
  message: string;
}

@Injectable({
  providedIn: 'root'
})
export class BookConversionService {
  private apiUrl = `${environment.apiUrl}/conversion`;

  constructor(private http: HttpClient) {}

  /**
   * Get available e-reader device profiles
   */
  getDeviceProfiles(): Observable<DeviceProfile[]> {
    return this.http.get<DeviceProfile[]>(`${this.apiUrl}/profiles`);
  }

  /**
   * Convert CBZ to EPUB with specified resolution settings
   */
  convertCbzToEpub(request: ConversionRequest): Observable<ConversionResponse> {
    return this.http.post<ConversionResponse>(`${this.apiUrl}/cbz-to-epub`, request);
  }
}
