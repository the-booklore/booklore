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
import {getOidcErrorCount, isOidcBypassed, resetOidcBypass} from '../../../core/auth-initializer';
import {AppSettingsService, PublicAppSettings} from '../../service/app-settings.service';

@Component({
  selector: 'app-login',
  imports: [
    FormsModule,
    Password,
    Button,
    Message,
    InputText
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
        this.oidcBypassMessage = `${this.oidcName} authentication has been automatically disabled after ${errorCount} consecutive failures (including timeouts). You can retry or continue with local login.`;
      } else if (this.isOidcBypassed) {
        this.oidcBypassMessage = `${this.oidcName} authentication has been manually disabled. You can re-enable it or continue with local login.`;
      } else if (errorCount > 0) {
        this.oidcBypassMessage = `${this.oidcName} authentication encountered ${errorCount} error(s), possibly due to timeouts or server issues.`;
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
          this.errorMessage = 'Cannot connect to the server. Please check your connection and try again.';
        } else {
          this.errorMessage = error?.error?.message || 'An unexpected error occurred. Please try again.';
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
      this.errorMessage = 'Failed to initiate OIDC login. Please try again or use local login.';
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
