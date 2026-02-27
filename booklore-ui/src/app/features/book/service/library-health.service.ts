import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable} from 'rxjs';
import {distinctUntilChanged, map} from 'rxjs/operators';
import {RxStompService} from '../../../shared/websocket/rx-stomp.service';
import {API_CONFIG} from '../../../core/config/api-config';

@Injectable({providedIn: 'root'})
export class LibraryHealthService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/libraries/health`;
  private http = inject(HttpClient);
  private rxStompService = inject(RxStompService);

  private healthSubject = new BehaviorSubject<Record<number, boolean>>({});

  initialize(): void {
    this.http.get<Record<number, boolean>>(this.url).subscribe(health => {
      this.healthSubject.next(health);
    });

    this.rxStompService.watch('/topic/library-health').subscribe(msg => {
      const payload = JSON.parse(msg.body);
      this.healthSubject.next(payload.libraryHealth);
    });
  }

  isUnhealthy$(libraryId: number): Observable<boolean> {
    return this.healthSubject.pipe(
      map(health => health[libraryId] === false),
      distinctUntilChanged()
    );
  }
}
