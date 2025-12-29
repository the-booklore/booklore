const basePath = document.querySelector('base')?.getAttribute('href')?.replace(/\/$/, '') || '';
export const environment = {
  production: true,
  API_CONFIG: {
    BASE_URL: window.location.origin + basePath,
    BROKER_URL:
      window.location.protocol === 'https:'
        ? `wss://${window.location.host}${basePath}/ws`
        : `ws://${window.location.host}${basePath}/ws`,
  },
};
