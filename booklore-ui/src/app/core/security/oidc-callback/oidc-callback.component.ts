import {Component, inject, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {OAuthService} from 'angular-oauth2-oidc';
import {AuthService} from '../../../shared/service/auth.service';
import {MessageService} from 'primeng/api';

@Component({
  selector: 'app-oidc-callback',
  templateUrl: './oidc-callback.component.html',
  styleUrls: ['./oidc-callback.component.scss']
})
export class OidcCallbackComponent implements OnInit {
  private router = inject(Router);
  private oauthService = inject(OAuthService);
  private messageService = inject(MessageService);

  async ngOnInit(): Promise<void> {
    try {
      await this.oauthService.tryLoginCodeFlow();
      if (this.oauthService.hasValidAccessToken()) {
        this.router.navigate(['/dashboard']);
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
