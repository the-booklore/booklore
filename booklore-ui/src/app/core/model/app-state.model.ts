export interface AppState {
  preset?: string;
  primary?: string;
  surface?: string;
  backgroundImage?: string;
  backgroundBlur?: number;
  showBackground?: boolean;
  lastUpdated?: number; // Not persisted, used for cache busting
  surfaceAlpha?: number;
}
