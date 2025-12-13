import {Component, inject, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {OAuthService} from 'angular-oauth2-oidc';
import {AuthService} from '../../../shared/service/auth.service';
import {MessageService} from 'primeng/api';
import {firstValueFrom} from 'rxjs';
import {API_CONFIG} from '../../config/api-config';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-oidc-callback',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './oidc-callback.component.html',
  styleUrls: ['./oidc-callback.component.scss']
})
export class OidcCallbackComponent implements OnInit {
  private router = inject(Router);
  private oauthService = inject(OAuthService);
  private authService = inject(AuthService);
  private messageService = inject(MessageService);
  
  loading = true;
  loadingMessage = 'Authenticating...';

  async ngOnInit(): Promise<void> {
    try {
      // Ensure discovery document is loaded (idempotent)
      // We do this here to avoid blocking app startup in auth-initializer
      this.loadingMessage = 'Loading configuration...';
      await this.oauthService.loadDiscoveryDocument(API_CONFIG.AUTH.OIDC_DISCOVERY);

      this.loadingMessage = 'Verifying credentials...';
      await this.oauthService.tryLoginCodeFlow();
      
      if (this.oauthService.hasValidAccessToken()) {
        this.loadingMessage = 'Finalizing login...';
        this.oauthService.setupAutomaticSilentRefresh();
        // Exchange OIDC token for internal JWT
        const tokens = await firstValueFrom(this.authService.exchangeOidcToken());

        if (tokens.accessToken && tokens.refreshToken) {
          this.authService.initializeWebSocketConnection();
          // Backend might return boolean true or string "true", handle both safely
          if (String(tokens.isDefaultPassword) === 'true') {
            this.messageService.add({
              severity: 'info',
              summary: 'Set Password',
              detail: 'Please set a local password for your account.',
              life: 5000
            });
            this.router.navigate(['/change-password']);
          } else {
            this.router.navigate(['/dashboard']);
          }
        } else {
          throw new Error('Token exchange failed - no tokens returned');
        }
      } else {
        this.router.navigate(['/login']);
      }
    } catch (e) {
      console.error('[OIDC Callback] Login failed', e);
      this.loadingMessage = 'Login failed. Redirecting...';
      this.messageService.add({
        severity: 'error',
        summary: 'OIDC Login Failed',
        detail: 'Redirecting to local login...',
        life: 3000
      });
      setTimeout(() => {
        this.router.navigate(['/login']);
      }, 3000);
    }
  }
}
