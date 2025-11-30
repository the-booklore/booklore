import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';

export interface OpdsUserV2CreateRequest {
  username: string;
  password: string;
  sortOption?: OpdsSortOption;
}

export interface OpdsUserV2UpdateRequest {
  sortOption: OpdsSortOption;
}

export interface OpdsUserV2 {
  id: number;
  userId: number;
  username: string;
  sortOption: OpdsSortOption;
}

export enum OpdsSortOption {
  ADDED_ON_DESC = 'ADDED_ON_DESC',
  ADDED_ON_ASC = 'ADDED_ON_ASC',
  AUTHOR_ASC = 'AUTHOR_ASC',
  AUTHOR_DESC = 'AUTHOR_DESC'
}

@Injectable({
  providedIn: 'root'
})
export class OpdsService {

  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v2/opds-users`;
  private http = inject(HttpClient);

  getUser(): Observable<OpdsUserV2[]> {
    return this.http.get<OpdsUserV2[]>(this.baseUrl);
  }

  createUser(user: OpdsUserV2CreateRequest): Observable<OpdsUserV2> {
    return this.http.post<OpdsUserV2>(this.baseUrl, user);
  }

  deleteCredential(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  updateUser(id: number, update: OpdsUserV2UpdateRequest): Observable<OpdsUserV2> {
    return this.http.patch<OpdsUserV2>(`${this.baseUrl}/${id}`, update);
  }
}
