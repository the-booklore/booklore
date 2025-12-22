import {inject, Injectable} from '@angular/core';
import {API_CONFIG} from '../../core/config/api-config';
import {AuthService} from './auth.service';
import {BookService} from '../../features/book/service/book.service';
import {CoverGeneratorComponent} from '../components/cover-generator/cover-generator.component';
import {Router} from '@angular/router';
import {Book} from '../../features/book/model/book.model';

@Injectable({
  providedIn: 'root'
})
export class UrlHelperService {
  private readonly baseUrl = API_CONFIG.BASE_URL;
  private readonly mediaBaseUrl = `${this.baseUrl}/api/v1/media`;
  private authService = inject(AuthService);
  private bookService = inject(BookService);
  private router = inject(Router);

  private getToken(): string | null {
    return this.authService.getOidcAccessToken() || this.authService.getInternalAccessToken();
  }

  private appendToken(url: string): string {
    const token = this.getToken();
    return token ? `${url}${url.includes('?') ? '&' : '?'}token=${token}` : url;
  }

  getThumbnailUrl(bookId: number, coverUpdatedOn?: string): string {
    if (!coverUpdatedOn) {
      const book = this.bookService.getBookByIdFromState(bookId);
      if (book && book.metadata) {
        const coverGenerator = new CoverGeneratorComponent();
        coverGenerator.title = book.metadata.title || '';
        coverGenerator.author = (book.metadata.authors || []).join(', ');
        return coverGenerator.generateCover();
      }
    }
    let url = `${this.mediaBaseUrl}/book/${bookId}/thumbnail`;
    if (coverUpdatedOn) {
      url += `?${coverUpdatedOn}`;
    }
    return this.appendToken(url);
  }

  getThumbnailUrl1(bookId: number, coverUpdatedOn?: string): string {
    let url = `${this.mediaBaseUrl}/book/${bookId}/thumbnail`;
    if (coverUpdatedOn) {
      url += `?${coverUpdatedOn}`;
    }
    return this.appendToken(url);
  }

  getCoverUrl(bookId: number, coverUpdatedOn?: string): string {
    if (!coverUpdatedOn) {
      const book = this.bookService.getBookByIdFromState(bookId);
      if (book && book.metadata) {
        const coverGenerator = new CoverGeneratorComponent();
        coverGenerator.title = book.metadata.title || '';
        coverGenerator.author = (book.metadata.authors || []).join(', ');
        return coverGenerator.generateCover();
      }
    }
    let url = `${this.mediaBaseUrl}/book/${bookId}/cover`;
    if (coverUpdatedOn) {
      url += `?${coverUpdatedOn}`;
    }
    return this.appendToken(url);
  }

  getBackupCoverUrl(bookId: number): string {
    const url = `${this.mediaBaseUrl}/book/${bookId}/backup-cover`;
    return this.appendToken(url);
  }

  getBookdropCoverUrl(bookdropId: number): string {
    const url = `${this.mediaBaseUrl}/bookdrop/${bookdropId}/cover`;
    return this.appendToken(url);
  }

  getBookUrl(book: Book) {
    return this.router.createUrlTree(['/book', book.id], {
      queryParams: {tab: 'view'}
    });
  }

  filterBooksBy(filterKey: string, filterValue: string) {
    if (filterKey === 'series') {
      return this.router.createUrlTree(['/series', encodeURIComponent(filterValue)])
    }

    return this.router.createUrlTree(['/all-books'], {
      queryParams: {
        view: 'grid',
        sort: 'title',
        direction: 'asc',
        sidebar: true,
        filter: `${filterKey}:${encodeURIComponent(filterValue)}`
      }
    });
  }
}
