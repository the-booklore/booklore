import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { Router } from '@angular/router';
import {AuthService} from '../shared/service/auth.service';
import {OAuthService} from 'angular-oauth2-oidc';

export const AuthGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);
  const authService = inject(AuthService);
  const oauthService = inject(OAuthService);

  const internalAccessToken = authService.getInternalAccessToken();

  if (internalAccessToken) {
    try {
      const payload = JSON.parse(atob(internalAccessToken.split('.')[1]));
      if (payload.isDefaultPassword) {
        router.navigate(['/change-password']);
        return false;
      }
      return true;
    } catch (e) {
      localStorage.removeItem('accessToken_Internal');
      router.navigate(['/login']);
      return false;
    }
  }

  if (oauthService.hasValidAccessToken()) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};
