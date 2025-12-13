import { defineConfig, devices } from '@playwright/test';

// ============================================
// E-Reader Device Specifications
// ============================================

// Kobo Libra 2 specifications
const KOBO_LIBRA_2 = {
  viewport: { width: 1264, height: 1680 },  // 7" display at 300 PPI
  userAgent: 'Mozilla/5.0 (Linux; Android 6.0.1; Kobo Libra 2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.136 Safari/537.36',
  colorScheme: 'light' as const,
  reducedMotion: 'reduce' as const,
};

// Kobo Elipsa specifications
const KOBO_ELIPSA = {
  viewport: { width: 1404, height: 1872 },  // 10.3" display
  userAgent: 'Mozilla/5.0 (Linux; Android 6.0.1; Kobo Elipsa) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.136 Safari/537.36',
  colorScheme: 'light' as const,
  reducedMotion: 'reduce' as const,
};

// PocketBook Era specifications
const POCKETBOOK_ERA = {
  viewport: { width: 1264, height: 1680 },
  userAgent: 'Mozilla/5.0 (Linux; U; Android 4.4.2; en-us; PocketBook Era) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/30.0.0.0 Safari/537.36',
  colorScheme: 'light' as const,
  reducedMotion: 'reduce' as const,
};

// Kindle Paperwhite 5 specifications
const KINDLE_PAPERWHITE_5 = {
  viewport: { width: 1236, height: 1648 },  // 6.8" display at 300 PPI
  userAgent: 'Mozilla/5.0 (Linux; Android 5.1.1; Kindle) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.136 Safari/537.36',
  colorScheme: 'light' as const,
  reducedMotion: 'reduce' as const,
};

// reMarkable 2 specifications (large e-ink tablet)
const REMARKABLE_2 = {
  viewport: { width: 1404, height: 1872 },  // 10.3" display at 226 PPI
  userAgent: 'Mozilla/5.0 (Linux; U; Android 4.4.2; reMarkable) AppleWebKit/537.36',
  colorScheme: 'light' as const,
  reducedMotion: 'reduce' as const,
};

export default defineConfig({
  testDir: '.',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['json', { outputFile: 'test-results.json' }],
  ],
  // Snapshot settings for E-Ink visual regression
  expect: {
    toHaveScreenshot: {
      // E-Ink has limited grayscale levels, allow for some variation
      maxDiffPixelRatio: 0.05,
      threshold: 0.3,
    },
  },
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:6060',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    // Primary E-Ink test project
    {
      name: 'kobo-libra-2',
      use: { ...devices['Desktop Chrome'], ...KOBO_LIBRA_2 },
    },
    // E-Ink Visual Regression (grayscale simulation)
    {
      name: 'eink-visual-regression',
      testMatch: '**/eink-visual-regression.spec.ts',
      use: { 
        ...devices['Desktop Chrome'], 
        ...KOBO_LIBRA_2,
        // Force light mode for consistent E-Ink testing
        colorScheme: 'light',
      },
    },
    // Additional devices in non-CI mode
    ...(process.env.CI ? [] : [
      {
        name: 'kobo-elipsa',
        use: { ...devices['Desktop Chrome'], ...KOBO_ELIPSA },
      },
      {
        name: 'pocketbook-era',
        use: { ...devices['Desktop Chrome'], ...POCKETBOOK_ERA },
      },
      {
        name: 'kindle-paperwhite-5',
        use: { ...devices['Desktop Chrome'], ...KINDLE_PAPERWHITE_5 },
      },
      {
        name: 'remarkable-2',
        use: { ...devices['Desktop Chrome'], ...REMARKABLE_2 },
      },
    ]),
  ],
});


