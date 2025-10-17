import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';

export interface MetadataMatchWeights {
  title: number;
  subtitle: number;
  description: number;
  publisher: number;
  publishedDate: number;
  authors: number;
  categories: number;
  seriesName: number;
  seriesNumber: number;
  isbn13: number;
  isbn10: number;
  pageCount: number;
  language: number;
  amazonRating: number;
  amazonReviewCount: number;
  goodreadsRating: number;
  goodreadsReviewCount: number;
  hardcoverRating: number;
  hardcoverReviewCount: number;
  coverImage: number;
}

@Injectable({providedIn: 'root'})
export class MetadataMatchWeightsService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1`;

  private http = inject(HttpClient);

  recalculateAll(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/books/metadata/recalculate-match-scores`, {});
  }
}
