import {Component, ElementRef, Input, ViewChild} from '@angular/core';
import {BookCardComponent} from '../../../book/components/book-browser/book-card/book-card.component';
import {InfiniteScrollDirective} from 'ngx-infinite-scroll';

import {ProgressSpinnerModule} from 'primeng/progressspinner';
import {Book} from '../../../book/model/book.model';
import {ScrollerType} from '../../models/dashboard-config.model';

@Component({
  selector: 'app-dashboard-scroller',
  templateUrl: './dashboard-scroller.component.html',
  styleUrls: ['./dashboard-scroller.component.scss'],
  imports: [
    InfiniteScrollDirective,
    BookCardComponent,
    ProgressSpinnerModule
  ],
  standalone: true
})
export class DashboardScrollerComponent {

  @Input() bookListType: ScrollerType | null = null;
  @Input() title!: string;
  @Input() books!: Book[] | null;
  @Input() isMagicShelf: boolean = false;

  @ViewChild('scrollContainer') scrollContainer!: ElementRef;
}
