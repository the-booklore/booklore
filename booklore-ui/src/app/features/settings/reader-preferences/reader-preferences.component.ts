import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {filter, takeUntil} from 'rxjs/operators';

import {Observable, Subject} from 'rxjs';
import {TooltipModule} from 'primeng/tooltip';
import {TranslocoDirective} from '@jsverse/transloco';
import {UserService, UserSettings, UserState} from '../user-management/user.service';
import {ReaderPreferencesService} from './reader-preferences.service';
import {EpubReaderPreferencesComponent} from './epub-reader-preferences/epub-reader-preferences-component';
import {PdfReaderPreferencesComponent} from './pdf-reader-preferences/pdf-reader-preferences-component';
import {CbxReaderPreferencesComponent} from './cbx-reader-preferences/cbx-reader-preferences-component';
import {CustomFontsComponent} from '../custom-fonts/custom-fonts.component';
import {NewPdfReaderPreferencesComponent} from './new-pdf-reader-preferences/new-pdf-reader-preferences-component';
import {SettingsApplicationModeComponent} from './settings-application-mode/settings-application-mode.component';

@Component({
  selector: 'app-reader-preferences',
  templateUrl: './reader-preferences.component.html',
  standalone: true,
  styleUrls: ['./reader-preferences.component.scss'],
  imports: [FormsModule, TooltipModule, TranslocoDirective, EpubReaderPreferencesComponent, PdfReaderPreferencesComponent, CbxReaderPreferencesComponent, CustomFontsComponent, NewPdfReaderPreferencesComponent, SettingsApplicationModeComponent]
})
export class ReaderPreferences implements OnInit, OnDestroy {
  private readonly userService = inject(UserService);
  private readonly destroy$ = new Subject<void>();

  userData$: Observable<UserState> = this.userService.userState$;
  userSettings!: UserSettings;

  hasFontManagementPermission = false;

  ngOnInit(): void {
    this.userData$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      takeUntil(this.destroy$)
    ).subscribe(userState => {
      this.userSettings = userState.user!.userSettings;
      const perms = userState.user!.permissions;
      this.hasFontManagementPermission = (perms.admin || perms.canManageFonts);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
