import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {BehaviorSubject, Observable} from 'rxjs';
import {map, tap} from 'rxjs/operators';
import {SseClient} from 'ngx-sse-client';
import {API_CONFIG} from '../../../core/config/api-config';
import {AuthorSummary, AuthorDetails, AuthorSearchResult, AuthorMatchRequest, AuthorUpdateRequest, AuthorPhotoResult} from '../model/author.model';
import {AuthService} from '../../../shared/service/auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthorService {

  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private sseClient = inject(SseClient);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/authors`;
  private readonly mediaBaseUrl = `${API_CONFIG.BASE_URL}/api/v1/media`;

  private allAuthorsSubject = new BehaviorSubject<AuthorSummary[] | null>(null);
  allAuthors$ = this.allAuthorsSubject.asObservable();

  getAllAuthors(): Observable<AuthorSummary[]> {
    return this.http.get<AuthorSummary[]>(this.baseUrl).pipe(
      tap(authors => this.allAuthorsSubject.next(authors))
    );
  }

  getAuthorDetails(authorId: number): Observable<AuthorDetails> {
    return this.http.get<AuthorDetails>(`${this.baseUrl}/${authorId}`);
  }

  getAuthorByName(name: string): Observable<AuthorDetails> {
    return this.http.get<AuthorDetails>(`${this.baseUrl}/by-name`, {params: {name}});
  }

  searchAuthorMetadata(authorId: number, query: string, region: string): Observable<AuthorSearchResult[]> {
    return this.http.get<AuthorSearchResult[]>(`${this.baseUrl}/${authorId}/search-metadata`, {
      params: {q: query, region}
    });
  }

  matchAuthor(authorId: number, request: AuthorMatchRequest): Observable<AuthorDetails> {
    return this.http.post<AuthorDetails>(`${this.baseUrl}/${authorId}/match`, request);
  }

  quickMatchAuthor(authorId: number, region: string = 'us'): Observable<AuthorDetails> {
    return this.http.post<AuthorDetails>(`${this.baseUrl}/${authorId}/quick-match`, null, {
      params: {region}
    });
  }

  autoMatchAuthors(authorIds: number[]): Observable<AuthorSummary> {
    const token = this.authService.getOidcAccessToken() || this.authService.getInternalAccessToken();
    const headers = new HttpHeaders()
      .set('Content-Type', 'application/json')
      .set('Authorization', `Bearer ${token}`);

    return this.sseClient.stream(
      `${this.baseUrl}/auto-match`,
      {keepAlive: false, reconnectionDelay: 1000, responseType: 'event'},
      {headers, body: authorIds, withCredentials: true},
      'POST'
    ).pipe(
      map(event => {
        if (event.type === 'error') {
          throw new Error((event as ErrorEvent).message);
        }
        return JSON.parse((event as MessageEvent).data) as AuthorSummary;
      })
    );
  }

  updateAuthor(authorId: number, request: AuthorUpdateRequest): Observable<AuthorDetails> {
    return this.http.put<AuthorDetails>(`${this.baseUrl}/${authorId}`, request);
  }

  unmatchAuthors(authorIds: number[]): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/unmatch`, authorIds);
  }

  deleteAuthors(authorIds: number[]): Observable<void> {
    return this.http.delete<void>(this.baseUrl, {body: authorIds});
  }

  searchAuthorPhotos(authorId: number, query: string): Observable<AuthorPhotoResult[]> {
    return this.http.get<AuthorPhotoResult[]>(`${this.baseUrl}/${authorId}/search-photos`, {
      params: {q: query}
    });
  }

  uploadAuthorPhotoFromUrl(authorId: number, imageUrl: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${authorId}/photo/url`, null, {
      params: {url: imageUrl}
    });
  }

  getUploadAuthorPhotoUrl(authorId: number): string {
    return `${this.baseUrl}/${authorId}/photo/upload`;
  }

  getAuthorPhotoUrl(authorId: number): string {
    const token = this.authService.getOidcAccessToken() || this.authService.getInternalAccessToken();
    let url = `${this.mediaBaseUrl}/author/${authorId}/photo`;
    if (token) {
      url += `?token=${token}`;
    }
    return url;
  }

  getAuthorThumbnailUrl(authorId: number, cacheBuster?: number): string {
    const token = this.authService.getOidcAccessToken() || this.authService.getInternalAccessToken();
    let url = `${this.mediaBaseUrl}/author/${authorId}/thumbnail`;
    if (token) {
      url += `?token=${token}`;
    }
    if (cacheBuster) {
      url += (token ? '&' : '?') + 't=' + cacheBuster;
    }
    return url;
  }
}
