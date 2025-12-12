import {environment} from '../../../environments/environment';

export const API_CONFIG = {
  ...environment.API_CONFIG,
  AUTH: {
    PUBLIC_SETTINGS: `${environment.API_CONFIG.BASE_URL}/api/v1/public-settings`,
    OIDC_DISCOVERY: `${environment.API_CONFIG.BASE_URL}/api/v1/auth/oidc/discovery`,
    OIDC_TOKEN: `${environment.API_CONFIG.BASE_URL}/api/v1/auth/oidc/token`,
    LOGIN: `${environment.API_CONFIG.BASE_URL}/api/v1/auth/login`,
    REFRESH: `${environment.API_CONFIG.BASE_URL}/api/v1/auth/refresh`,
    REMOTE: `${environment.API_CONFIG.BASE_URL}/api/v1/auth/remote`
  }
};