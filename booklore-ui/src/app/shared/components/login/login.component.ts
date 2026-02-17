import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {AuthService} from '../../service/auth.service';
import {Router} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {Password} from 'primeng/password';
import {Button} from 'primeng/button';
import {Message} from 'primeng/message';
import {InputText} from 'primeng/inputtext';
import {OAuthService} from 'angular-oauth2-oidc';
import {Observable, Subject} from 'rxjs';
import {filter, take} from 'rxjs/operators';
import {getOidcErrorCount, isOidcBypassed, resetOidcBypass} from '../../../core/security/auth-initializer';
import {AppSettingsService, PublicAppSettings} from '../../service/app-settings.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-login',
  imports: [
    FormsModule,
    Password,
    Button,
    Message,
    InputText,
    TranslocoDirective
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit, OnDestroy {
  username = '';
  password = '';
  errorMessage = '';
  oidcEnabled = false;
  oidcName = 'OIDC';
  isOidcBypassed = false;
  showOidcBypassInfo = false;
  oidcBypassMessage = '';
  isOidcLoginInProgress = false;

  private authService = inject(AuthService);
  private oAuthService = inject(OAuthService);
  private appSettingsService = inject(AppSettingsService);
  private router = inject(Router);
  private translocoService = inject(TranslocoService);

  publicAppSettings$: Observable<PublicAppSettings | null> = this.appSettingsService.publicAppSettings$;

  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.publicAppSettings$
      .pipe(
        filter(settings => settings != null),
        take(1)
      )
      .subscribe(publicSettings => {
        this.oidcEnabled = publicSettings!.oidcEnabled;
        this.oidcName = publicSettings!.oidcProviderDetails?.providerName || 'OIDC';
        this.checkOidcBypassStatus();
      });
  }

  private checkOidcBypassStatus(): void {
    this.isOidcBypassed = isOidcBypassed();
    const errorCount = getOidcErrorCount();

    if (this.oidcEnabled && (this.isOidcBypassed || errorCount > 0)) {
      this.showOidcBypassInfo = true;

      if (this.isOidcBypassed && errorCount >= 3) {
        this.oidcBypassMessage = this.translocoService.translate('auth.login.oidcAutoDisabled', {provider: this.oidcName, count: errorCount});
      } else if (this.isOidcBypassed) {
        this.oidcBypassMessage = this.translocoService.translate('auth.login.oidcManuallyDisabled', {provider: this.oidcName});
      } else if (errorCount > 0) {
        this.oidcBypassMessage = this.translocoService.translate('auth.login.oidcErrors', {provider: this.oidcName, count: errorCount});
      }
    }
  }

  login(): void {
    this.authService.internalLogin({username: this.username, password: this.password}).subscribe({
      next: (response) => {
        if (response.isDefaultPassword === 'true') {
          this.router.navigate(['/change-password']);
        } else {
          this.router.navigate(['/dashboard']);
        }
      },
      error: (error) => {
        if (error.status === 0) {
          this.errorMessage = this.translocoService.translate('auth.login.connectionError');
        } else if (error.status === 429) {
          this.errorMessage = this.translocoService.translate('auth.login.rateLimited');
        } else {
          this.errorMessage = error?.error?.message || this.translocoService.translate('auth.login.unexpectedError');
        }
      }
    });
  }

  loginWithOidc(): void {
    if (this.isOidcLoginInProgress) {
      return;
    }

    this.isOidcLoginInProgress = true;
    this.errorMessage = '';

    try {
      setTimeout(() => {
        this.isOidcLoginInProgress = false;
      }, 5000);
      this.oAuthService.initCodeFlow();
    } catch (error) {
      console.error('OIDC login initiation failed:', error);
      this.errorMessage = this.translocoService.translate('auth.login.oidcInitError');
      this.isOidcLoginInProgress = false;
    }
  }

  bypassOidc(): void {
    localStorage.setItem('booklore-oidc-bypass', 'true');
    this.isOidcBypassed = true;
    this.showOidcBypassInfo = false;
  }

  enableOidc(): void {
    resetOidcBypass();
    this.isOidcBypassed = false;
    this.showOidcBypassInfo = false;
    this.isOidcLoginInProgress = false;
    window.location.reload();
  }

  retryOidc(): void {
    resetOidcBypass();
    this.isOidcBypassed = false;
    this.showOidcBypassInfo = false;
    this.isOidcLoginInProgress = false;
    window.location.reload();
  }

  dismissOidcWarning(): void {
    this.showOidcBypassInfo = false;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
