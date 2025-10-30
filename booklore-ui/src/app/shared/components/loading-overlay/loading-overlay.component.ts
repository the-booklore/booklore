import {Component, OnInit, OnDestroy, inject} from '@angular/core';
import { Subscription } from 'rxjs';
import {LoadingService} from '../../service/loading.service';
import {ProgressSpinner} from 'primeng/progressspinner';


@Component({
  selector: 'app-loading-overlay',
  templateUrl: './loading-overlay.component.html',
  imports: [
    ProgressSpinner
],
  standalone: true,
  styleUrls: ['./loading-overlay.component.scss']
})
export class LoadingOverlayComponent implements OnInit, OnDestroy {
  loading: boolean = false;
  private loadingSubscription: Subscription | undefined;

  private loadingService = inject(LoadingService);

  ngOnInit(): void {
    this.loadingSubscription = this.loadingService.loading$.subscribe(
      (loading) => {
        this.loading = loading;
      }
    );
  }

  ngOnDestroy(): void {
    this.loadingSubscription?.unsubscribe();
  }
}
