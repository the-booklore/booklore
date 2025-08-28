import {inject, Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';
import {MessageService} from 'primeng/api';
import {LocalStorageService} from '../../../../core/service/local-storage-service';

@Injectable({
  providedIn: 'root'
})
export class SidebarFilterTogglePrefService {

  private readonly STORAGE_KEY = 'showSidebarFilter';
  private readonly messageService = inject(MessageService);
  private readonly localStorageService = inject(LocalStorageService);

  private readonly showFilterSubject = new BehaviorSubject<boolean>(true);
  readonly showFilter$ = this.showFilterSubject.asObservable();

  constructor() {
    this.loadFromStorage();
  }

  get selectedShowFilter(): boolean {
    return this.showFilterSubject.value;
  }

  set selectedShowFilter(value: boolean) {
    if (this.showFilterSubject.value !== value) {
      this.showFilterSubject.next(value);
      this.savePreference(value);
    }
  }

  toggle(): void {
    this.selectedShowFilter = !this.selectedShowFilter;
  }

  private savePreference(value: boolean): void {
    try {
      this.localStorageService.set(this.STORAGE_KEY, value);
    } catch (e) {
      this.messageService.add({
        severity: 'error',
        summary: 'Save Failed',
        detail: 'Could not save sidebar filter preference locally.',
        life: 3000
      });
    }
  }

  private loadFromStorage(): void {
    const saved = this.localStorageService.get<boolean>(this.STORAGE_KEY);
    if (saved !== null) {
      this.showFilterSubject.next(saved);
    }
  }
}
