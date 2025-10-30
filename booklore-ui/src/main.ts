import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, MessageService} from 'primeng/api';
import {RxStompService} from './app/shared/websocket/rx-stomp.service';
import {rxStompServiceFactory} from './app/shared/websocket/rx-stomp-service-factory';
import {provideRouter, RouteReuseStrategy} from '@angular/router';
import {CustomReuseStrategy} from './app/core/custom-reuse-strategy';
import {provideAnimationsAsync} from '@angular/platform-browser/animations/async';
import {providePrimeNG} from 'primeng/config';
import {bootstrapApplication} from '@angular/platform-browser';
import {AppComponent} from './app/app.component';
import Aura from '@primeng/themes/aura';
import {routes} from './app/app.routes';
import {AuthInterceptorService} from './app/core/security/auth-interceptor.service';
import {AuthService, websocketInitializer} from './app/shared/service/auth.service';
import {OAuthStorage, provideOAuthClient} from 'angular-oauth2-oidc';
import {APP_INITIALIZER, provideAppInitializer} from '@angular/core';
import {initializeAuthFactory} from './app/core/security/auth-initializer';

export function storageFactory(): OAuthStorage {
  return localStorage;
}

import {StartupService} from './app/shared/service/startup.service';
import {provideCharts, withDefaultRegisterables} from 'ng2-charts';
import ChartDataLabels from 'chartjs-plugin-datalabels';

bootstrapApplication(AppComponent, {
  providers: [
    provideCharts(withDefaultRegisterables(), ChartDataLabels),
    {
      provide: APP_INITIALIZER,
      useFactory: websocketInitializer,
      deps: [AuthService],
      multi: true
    },
    {
      provide: APP_INITIALIZER,
      useFactory: (startup: StartupService) => () => startup.load(),
      deps: [StartupService],
      multi: true
    },
    provideHttpClient(withInterceptors([AuthInterceptorService])),
    provideOAuthClient(),
    // Configure OAuth to use localStorage instead of sessionStorage
    {
      provide: OAuthStorage,
      useFactory: storageFactory
    },
    provideAppInitializer(initializeAuthFactory()),
    provideRouter(routes),
    DialogService,
    MessageService,
    ConfirmationService,
    {
      provide: RxStompService,
      useFactory: rxStompServiceFactory,
      deps: [AuthService],
    },
    {
      provide: RouteReuseStrategy,
      useClass: CustomReuseStrategy
    },
    provideAnimationsAsync(),
    providePrimeNG({
      theme: {
        preset: Aura,
        options: {
          darkModeSelector: '.p-dark'
        }
      }
    })
  ]
}).catch(err => console.error(err));
