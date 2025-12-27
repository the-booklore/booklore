export const environment = {
  production: true,
  API_CONFIG: {
    BASE_URL: window.location.origin + (document.querySelector('base')?.getAttribute('href')?.replace(/\/$/, '') || ''),
    BROKER_URL:
      window.location.protocol === 'https:'
        ? `wss://${window.location.host}${(document.querySelector('base')?.getAttribute('href')?.replace(/\/$/, '') || '')}/ws`
        : `ws://${window.location.host}${(document.querySelector('base')?.getAttribute('href')?.replace(/\/$/, '') || '')}/ws`,
  },
};
