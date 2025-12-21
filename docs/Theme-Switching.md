# Theme Switching

BookLore now supports light, dark, and system theme modes to match your preference.

## Available Theme Modes

### Dark Mode (Default)
The classic dark theme that BookLore has always used. Perfect for reading in low-light environments.

### Light Mode
A bright, clean light theme suitable for well-lit environments and daytime reading.

### System Mode
Automatically follows your operating system's theme preference. If your OS is set to dark mode, BookLore will use dark mode. If your OS switches to light mode, BookLore will automatically follow.

## How to Change Theme

1. Click the **palette icon** (🎨) in the top-right corner of the navigation bar
2. In the theme configurator panel, you'll see three theme mode buttons at the top:
   - **Light** (☀️): Switch to light mode
   - **Dark** (🌙): Switch to dark mode  
   - **System** (💻): Follow system preference

3. Click any button to switch to that theme mode
4. Your preference is automatically saved and will persist across sessions

## Color Customization

The theme system works seamlessly with BookLore's existing color customization options:

- **Primary Colors**: Choose from 37+ color options that work in both light and dark modes
- **Surface Colors**: Select from 17 surface color palettes optimized for both themes

All color combinations are tested to ensure proper contrast and readability in both light and dark modes.

## Technical Details

- Theme preference is stored in browser localStorage
- System mode uses the CSS `prefers-color-scheme` media query
- Theme changes are applied instantly without page reload
- All PrimeNG components automatically adapt to the selected theme

## Browser Support

System theme detection works on all modern browsers that support the `prefers-color-scheme` media query:
- Chrome 76+
- Firefox 67+
- Safari 12.1+
- Edge 79+

## Notes

- Existing users will continue to see the dark theme by default
- The theme setting is per-browser, not per-user account
- Some custom reader themes (EPUB, PDF) have their own separate theme settings
