import {Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';

@Injectable({providedIn: 'root'})
export class AuthInitializationService {
  private initialized = new BehaviorSubject<boolean>(false);
  initialized$ = this.initialized.asObservable();

  markAsInitialized() {
    this.initialized.next(true);
  }
}
