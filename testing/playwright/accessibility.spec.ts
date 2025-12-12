import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

interface AxeViolation {
  id: string;
  impact?: string | null;
  description: string;
  nodes: { html: string }[];
}

// Shared AxeBuilder configuration for consistent testing
function createAxeBuilder(page: any) {
  return new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']) // Complete WCAG A/AA coverage
    .exclude('#__next-prerender-indicator') // Common Next.js exclusions
    .exclude('[aria-hidden="true"]'); // Skip hidden elements
}

// Helper function for consistent accessibility assertions
async function expectNoA11yViolations(builder: AxeBuilder, options?: { rules?: string[] }) {
  const results = options?.rules
    ? await builder.withRules(options.rules).analyze()
    : await builder.analyze();

  if (results.violations.length > 0) {
    console.log('Accessibility violations found:');
    results.violations.forEach((violation: AxeViolation) => {
      console.log(`  - ${violation.id} (${violation.impact}): ${violation.description}`);
      violation.nodes.slice(0, 3).forEach(node => {
        console.log(`    Element: ${node.html.substring(0, 100)}`);
      });
    });
  }

  expect(results.violations).toEqual([]);
}

test.describe('WCAG Accessibility Compliance', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    // Wait for a stable UI state instead of networkidle
    await page.locator('input[type="text"], input[type="email"], input[name="username"]').first().waitFor();
  });

  test('login page meets WCAG 2.1 AA standards', async ({ page }) => {
    await expectNoA11yViolations(createAxeBuilder(page));
  });

  test('color contrast meets WCAG AA (4.5:1 ratio)', async ({ page }) => {
    // Use explicit rules for precise control - WCAG AA standard contrast
    await expectNoA11yViolations(createAxeBuilder(page), {
      rules: ['color-contrast'] // Only AA contrast, not AAA
    });
  });

  test('enhanced contrast for e-ink readability (WCAG AAA)', async ({ page }) => {
    // Separate test for enhanced contrast requirements for e-ink devices
    await expectNoA11yViolations(createAxeBuilder(page), {
      rules: ['color-contrast-enhanced'] // AAA contrast for better e-ink readability
    });
  });

  test('text alternatives exist for non-text content', async ({ page }) => {
    // Now actually asserts failures instead of just logging
    await expectNoA11yViolations(createAxeBuilder(page), {
      rules: ['image-alt', 'input-image-alt', 'object-alt', 'area-alt']
    });
  });

  test('form elements have proper labels', async ({ page }) => {
    await expectNoA11yViolations(createAxeBuilder(page), {
      rules: ['label', 'label-title-only', 'aria-label', 'aria-labelledby']
    });
  });

  test('keyboard navigation is functional', async ({ page }) => {
    await expectNoA11yViolations(createAxeBuilder(page), {
      rules: ['keyboard', 'tabindex', 'focusable-content', 'focusable-disabled']
    });
  });

  test('focus indicators are visible', async ({ page }) => {
    // Tab through focusable elements and check for visible focus
    const focusableElements = await page.locator('button, a, input, [tabindex]:not([tabindex="-1"])').count();

    let visibleFocusCount = 0;
    for (let i = 0; i < Math.min(focusableElements, 10); i++) {
      await page.keyboard.press('Tab');

      // Check if focused element has visible focus indicator
      const hasFocusStyle = await page.evaluate(() => {
        const focused = document.activeElement;
        if (!focused) return false;

        const style = window.getComputedStyle(focused);
        const outline = style.outline;
        const boxShadow = style.boxShadow;

        // Check if any focus indicator is present and visible
        return (
          (outline && outline !== 'none' && !outline.includes('0px')) ||
          (boxShadow && boxShadow !== 'none' && !boxShadow.includes('0px')) ||
          focused.matches(':focus-visible')
        );
      });

      if (hasFocusStyle) visibleFocusCount++;
    }

    // At least 70% of elements should have visible focus indicators
    const focusRatio = visibleFocusCount / Math.min(focusableElements, 10);
    console.log(`Focus visibility: ${visibleFocusCount}/${Math.min(focusableElements, 10)} (${(focusRatio * 100).toFixed(0)}%)`);

    expect(focusRatio).toBeGreaterThan(0.7); // Stricter requirement
  });
});

test.describe('E-Ink Specific Accessibility', () => {

  test('reduced motion preference disables animations', async ({ page }) => {
    // Set reduced motion preference
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/');
    await page.locator('input[type="text"], input[type="email"], input[name="username"]').first().waitFor();

    // Check that animations are actually disabled when prefers-reduced-motion is set
    const hasActiveAnimations = await page.evaluate(() => {
      const elements = document.querySelectorAll('*');
      let activeAnimations = 0;

      elements.forEach((el) => {
        const style = window.getComputedStyle(el);
        const animationName = style.animationName;
        const animationDuration = style.animationDuration;

        // Count elements with active animations (non-zero duration and non-'none' name)
        if (animationName && animationName !== 'none' &&
            animationDuration && animationDuration !== '0s') {
          activeAnimations++;
        }
      });

      return activeAnimations;
    });

    expect(hasActiveAnimations).toBe(0);
  });

  test('light color scheme provides adequate contrast', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/');
    await page.locator('input[type="text"], input[type="email"], input[name="username"]').first().waitFor();

    // Check that background provides sufficient contrast for e-ink readability
    const backgroundLuminance = await page.evaluate(() => {
      const body = document.body;
      const style = window.getComputedStyle(body);
      const bgColor = style.backgroundColor;

      // Convert RGB to relative luminance
      const rgbMatch = bgColor.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/);
      if (!rgbMatch) return null;

      const [, r, g, b] = rgbMatch.map(Number);
      const toLinear = (c: number) => {
        c = c / 255;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
      };

      return 0.2126 * toLinear(r) + 0.7152 * toLinear(g) + 0.0722 * toLinear(b);
    });

    expect(backgroundLuminance).not.toBeNull();
    // For e-ink readability, background should be light (luminance > 0.8)
    expect(backgroundLuminance!).toBeGreaterThan(0.8);
  });
});
