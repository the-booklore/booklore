import {Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {ButtonModule} from 'primeng/button';
import {CheckboxModule} from 'primeng/checkbox';
import {InputTextModule} from 'primeng/inputtext';
import {SelectModule} from 'primeng/select';
import {InputNumberModule} from 'primeng/inputnumber';
import {DashboardConfig, ScrollerConfig, ScrollerType} from '../../models/dashboard-config.model';
import {DashboardConfigService} from '../../services/dashboard-config.service';
import {MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {map} from 'rxjs/operators';

export const MAX_SCROLLERS = 5;
export const DEFAULT_MAX_ITEMS = 20;
export const MIN_ITEMS = 10;
export const MAX_ITEMS = 20;

@Component({
  selector: 'app-dashboard-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    CheckboxModule,
    InputTextModule,
    SelectModule,
    InputNumberModule
  ],
  templateUrl: './dashboard-settings.component.html',
  styleUrls: ['./dashboard-settings.component.scss']
})
export class DashboardSettingsComponent implements OnInit {
  private configService = inject(DashboardConfigService);
  private dialogRef = inject(DynamicDialogRef);
  private magicShelfService = inject(MagicShelfService);

  config!: DashboardConfig;

  availableScrollerTypes = [
    {label: 'Continue Reading', value: ScrollerType.LAST_READ},
    {label: 'Recently Added', value: ScrollerType.LATEST_ADDED},
    {label: 'Discover Something New', value: ScrollerType.RANDOM},
    {label: 'Magic Shelf', value: ScrollerType.MAGIC_SHELF}
  ];

  magicShelves$ = this.magicShelfService.shelvesState$.pipe(
    map(state => (state.shelves || []).map(shelf => ({
      label: shelf.name,
      value: shelf.id!
    })))
  );

  sortFieldOptions = [
    {label: 'Title', value: 'title'},
    {label: 'Title + Series', value: 'titleSeries'},
    {label: 'Date Added', value: 'addedOn'},
    {label: 'Author', value: 'author'},
    {label: 'Personal Rating', value: 'personalRating'},
    {label: 'Publisher', value: 'publisher'},
    {label: 'Published Date', value: 'publishedDate'},
    {label: 'Last Read', value: 'lastReadTime'},
    {label: 'Pages', value: 'pageCount'}
  ];

  sortDirectionOptions = [
    {label: 'Ascending', value: 'asc'},
    {label: 'Descending', value: 'desc'}
  ];

  private magicShelvesMap = new Map<number, string>();

  readonly MIN_ITEMS = MIN_ITEMS;
  readonly MAX_ITEMS = MAX_ITEMS;

  ngOnInit(): void {
    this.configService.config$.subscribe(config => {
      this.config = JSON.parse(JSON.stringify(config));
    });

    this.magicShelfService.shelvesState$.subscribe(state => {
      this.magicShelvesMap.clear();
      (state.shelves || []).forEach(shelf => {
        if (shelf.id) {
          this.magicShelvesMap.set(shelf.id, shelf.name);
        }
      });
    });
  }

  getScrollerTitle(scroller: ScrollerConfig): string {
    if (scroller.type === ScrollerType.MAGIC_SHELF && scroller.magicShelfId) {
      return this.magicShelvesMap.get(scroller.magicShelfId) || 'Magic Shelf';
    }

    switch (scroller.type) {
      case ScrollerType.LAST_READ:
        return 'Continue Reading';
      case ScrollerType.LATEST_ADDED:
        return 'Recently Added';
      case ScrollerType.RANDOM:
        return 'Discover Something New';
      default:
        return 'Scroller';
    }
  }

  addScroller(): void {
    if (this.config.scrollers.length >= MAX_SCROLLERS) {
      return;
    }
    const newId = (Math.max(...this.config.scrollers.map((s: ScrollerConfig) => parseInt(s.id)), 0) + 1).toString();
    this.config.scrollers.push({
      id: newId,
      type: ScrollerType.LATEST_ADDED,
      title: '',
      enabled: true,
      order: this.config.scrollers.length + 1,
      maxItems: DEFAULT_MAX_ITEMS
    });
  }

  removeScroller(index: number): void {
    if (this.config.scrollers.length <= 1) {
      return;
    }
    this.config.scrollers.splice(index, 1);
    this.updateOrder();
  }

  onScrollerTypeChange(scroller: ScrollerConfig): void {
    if (scroller.type === ScrollerType.MAGIC_SHELF) {
      scroller.magicShelfId = undefined;
    } else {
      delete scroller.magicShelfId;
    }
  }

  moveUp(index: number): void {
    if (index > 0) {
      [this.config.scrollers[index], this.config.scrollers[index - 1]] =
        [this.config.scrollers[index - 1], this.config.scrollers[index]];
      this.updateOrder();
    }
  }

  moveDown(index: number): void {
    if (index < this.config.scrollers.length - 1) {
      [this.config.scrollers[index], this.config.scrollers[index + 1]] =
        [this.config.scrollers[index + 1], this.config.scrollers[index]];
      this.updateOrder();
    }
  }

  private updateOrder(): void {
    this.config.scrollers.forEach((scroller, index) => {
      scroller.order = index + 1;
    });
  }

  save(): void {
    this.config.scrollers.forEach(scroller => {
      scroller.title = this.getScrollerTitle(scroller);
    });
    this.configService.saveConfig(this.config);
    this.dialogRef.close();
  }

  cancel(): void {
    this.dialogRef.close();
  }

  resetToDefault(): void {
    this.configService.resetToDefault();
    this.dialogRef.close();
  }
}
