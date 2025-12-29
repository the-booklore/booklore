const base_path = document.querySelector('base')?.getAttribute('href')?.replace(/\/$/, '') || '';
export const environment = {
  production: true,
  API_CONFIG: {
    BASE_URL: window.location.origin + base_path,
    BROKER_URL:
      window.location.protocol === 'https:'
        ? `wss://${window.location.host}${base_path}/ws`
        : `ws://${window.location.host}${base_path}/ws`,
  },
};
