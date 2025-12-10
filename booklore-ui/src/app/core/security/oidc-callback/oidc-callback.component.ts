import {Component, inject, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {OAuthService} from 'angular-oauth2-oidc';
import {AuthService} from '../../../shared/service/auth.service';
import {MessageService} from 'primeng/api';
import {firstValueFrom} from 'rxjs';

@Component({
  selector: 'app-oidc-callback',
  templateUrl: './oidc-callback.component.html',
  styleUrls: ['./oidc-callback.component.scss']
})
export class OidcCallbackComponent implements OnInit {
  private router = inject(Router);
  private oauthService = inject(OAuthService);
  private authService = inject(AuthService);
  private messageService = inject(MessageService);

  async ngOnInit(): Promise<void> {
    try {
      await this.oauthService.tryLoginCodeFlow();
      if (this.oauthService.hasValidAccessToken()) {
        console.log('[OIDC Callback] OIDC token received, exchanging for internal JWT...');
        
        // Exchange OIDC token for internal JWT
        const tokens = await firstValueFrom(this.authService.exchangeOidcToken());
        
        if (tokens.accessToken && tokens.refreshToken) {
          console.log('[OIDC Callback] Internal JWT received, login successful');
          this.authService.initializeWebSocketConnection();
          this.router.navigate(['/dashboard']);
        } else {
          throw new Error('Token exchange failed - no tokens returned');
        }
      } else {
        this.router.navigate(['/login']);
      }
    } catch (e) {
      console.error('[OIDC Callback] Login failed', e);
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
