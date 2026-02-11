import {Injectable, inject} from '@angular/core';
import {API_CONFIG} from '../../../core/config/api-config';
import {HttpClient} from '@angular/common/http';
import {AuthService} from '../../../shared/service/auth.service';

export interface PdfInfoPage {
  pageNumber: number;
  displayName: string;
}

export interface PdfOutlineItem {
  title: string;
  pageNumber: number;
  children: PdfOutlineItem[] | null;
}

export interface PdfInfo {
  pageCount: number;
  outline: PdfOutlineItem[] | null;
}

@Injectable({
  providedIn: 'root'
})
export class NewPdfReaderService {

  private readonly pagesUrl = `${API_CONFIG.BASE_URL}/api/v1/pdf`;
  private readonly imageUrl = `${API_CONFIG.BASE_URL}/api/v1/media/book`;
  private authService = inject(AuthService);
  private http = inject(HttpClient);

  private getToken(): string | null {
    return this.authService.getOidcAccessToken() || this.authService.getInternalAccessToken();
  }

  private appendToken(url: string): string {
    const token = this.getToken();
    return token ? `${url}${url.includes('?') ? '&' : '?'}token=${token}` : url;
  }

  getAvailablePages(bookId: number, bookType?: string) {
    let url = `${this.pagesUrl}/${bookId}/pages`;
    if (bookType) {
      url += `?bookType=${bookType}`;
    }
    return this.http.get<number[]>(this.appendToken(url));
  }

  getPageInfo(bookId: number, bookType?: string) {
    let url = `${this.pagesUrl}/${bookId}/info`;
    if (bookType) {
      url += `?bookType=${bookType}`;
    }
    return this.http.get<PdfInfo>(this.appendToken(url));
  }

  getPageImageUrl(bookId: number, page: number, bookType?: string): string {
    let url = `${this.imageUrl}/${bookId}/pdf/pages/${page}`;
    if (bookType) {
      url += `?bookType=${bookType}`;
    }
    return this.appendToken(url);
  }
}
