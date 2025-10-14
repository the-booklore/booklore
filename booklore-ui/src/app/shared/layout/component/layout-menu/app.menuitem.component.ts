import {Component, ElementRef, HostBinding, Input, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {NavigationEnd, Router, RouterLink, RouterLinkActive} from '@angular/router';
import {animate, state, style, transition, trigger} from '@angular/animations';
import {Subscription} from 'rxjs';
import {filter} from 'rxjs/operators';
import {MenuService} from './service/app.menu.service';
import {AsyncPipe, NgClass} from '@angular/common';
import {Ripple} from 'primeng/ripple';
import {Button} from 'primeng/button';
import {Menu} from 'primeng/menu';
import {UserService} from '../../../../features/settings/user-management/user.service';
import {DialogLauncherService} from '../../../services/dialog-launcher.service';

@Component({
  selector: '[app-menuitem]',
  templateUrl: './app.menuitem.component.html',
  styleUrls: ['./app.menuitem.component.scss'],
  imports: [
    RouterLink,
    RouterLinkActive,
    NgClass,
    Ripple,
    AsyncPipe,
    Button,
    Menu
  ],
  animations: [
    trigger('children', [
      state('collapsed', style({
        height: '0'
      })),
      state('expanded', style({
        height: '*'
      })),
      transition('collapsed <=> expanded', animate('400ms cubic-bezier(0.86, 0, 0.07, 1)'))
    ])
  ]
})
export class AppMenuitemComponent implements OnInit, OnDestroy {
  @Input() item: any;
  @Input() index!: number;
  @Input() @HostBinding('class.layout-root-menuitem') root!: boolean;
  @Input() parentKey!: string;
  @ViewChild('linkRef') linkRef!: ElementRef<HTMLAnchorElement>;

  hovered = false;
  active = false;
  key: string = "";
  canManipulateLibrary: boolean = false;
  admin: boolean = false;
  expandedItems = new Set<string>();
  menuSourceSubscription: Subscription;
  menuResetSubscription: Subscription;

  constructor(
    public router: Router,
    private menuService: MenuService,
    private userService: UserService,
    private dialogLauncher: DialogLauncherService
  ) {
    this.userService.userState$.subscribe(userState => {
      if (userState?.user) {
        this.canManipulateLibrary = userState.user.permissions.canManipulateLibrary;
        this.admin = userState.user.permissions.admin;
      }
    });

    this.menuSourceSubscription = this.menuService.menuSource$.subscribe(value => {
      Promise.resolve(null).then(() => {
        if (value.routeEvent) {
          this.active = (value.key === this.key || value.key.startsWith(this.key + '-')) ? true : false;
        } else {
          if (value.key !== this.key && !value.key.startsWith(this.key + '-')) {
            this.active = false;
          }
        }
      });
    });

    this.menuResetSubscription = this.menuService.resetSource$.subscribe(() => {
      this.active = false;
    });

    this.router.events.pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
        if (this.item.routerLink) {
          this.updateActiveStateFromRoute();
        }
      });
  }

  ngOnInit() {
    this.key = this.parentKey ? this.parentKey + '-' + this.index : String(this.index);
    this.expandedItems.add(this.key);
    if (this.item.routerLink) {
      this.updateActiveStateFromRoute();
    }
  }

  ngOnDestroy() {
    if (this.menuSourceSubscription) {
      this.menuSourceSubscription.unsubscribe();
    }
    if (this.menuResetSubscription) {
      this.menuResetSubscription.unsubscribe();
    }
  }

  toggleExpand(key: string) {
    if (this.expandedItems.has(key)) {
      this.expandedItems.delete(key);
    } else {
      this.expandedItems.add(key);
    }
  }

  isExpanded(key: string): boolean {
    return this.expandedItems.has(key);
  }

  updateActiveStateFromRoute() {
    const activeRoute = this.router.isActive(this.item.routerLink[0], {
      paths: 'exact',
      queryParams: 'ignored',
      matrixParams: 'ignored',
      fragment: 'ignored'
    });
    if (activeRoute) {
      this.menuService.onMenuStateChange({key: this.key, routeEvent: true});
    }
  }

  itemClick(event: Event) {
    if (this.item.disabled) {
      event.preventDefault();
      return;
    }
    if (this.item.command) {
      this.item.command({originalEvent: event, item: this.item});
    }
    if (this.item.items) {
      this.active = !this.active;
    }
    this.menuService.onMenuStateChange({key: this.key});
  }

  openDialog(item: any) {
    if (item.type === 'library' && this.canManipulateLibrary) {
      this.dialogLauncher.openLibraryCreatorDialog();
    }
    if (item.type === 'magicShelf') {
      this.dialogLauncher.openMagicShelfDialog();
    }
  }

  triggerLink() {
    if (this.item.routerLink && !this.item.items && this.linkRef) {
      this.linkRef.nativeElement.click();
    }
  }

  @HostBinding('class.active-menuitem')
  get activeClass() {
    return this.active && !this.root;
  }
}
