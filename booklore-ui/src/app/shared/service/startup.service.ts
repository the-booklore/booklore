import {Injectable, inject} from '@angular/core';
import {AuthService} from './auth.service';
import {UserService} from '../../features/settings/user-management/user.service';
import {filter, catchError} from 'rxjs/operators';
import {of} from 'rxjs';
import {OAuthService} from 'angular-oauth2-oidc';

@Injectable({providedIn: 'root'})
export class StartupService {
  private authService = inject(AuthService);
  private userService = inject(UserService);

  load(): Promise<void> {
    this.authService.token$
      .pipe(filter(t => !!t))
      .subscribe(() => {
        this.userService.getMyself()
          .pipe(catchError(() => of(null)))
          .subscribe(user => {
            if (user) {
              this.userService.setInitialUser(user);
            }
          });
      });

    return Promise.resolve();
  }
}
