export enum EpubTheme {
  WHITE = 'white',
  BLACK = 'black',
  GREY = 'grey',
  SEPIA = 'sepia',
  GREEN = 'green',
  LAVENDER = 'lavender',
  CREAM = 'cream',
  LIGHT_BLUE = 'light-blue',
  PEACH = 'peach',
  MINT = 'mint',
  DARK_SLATE = 'dark-slate',
  DARK_OLIVE = 'dark-olive',
  DARK_PURPLE = 'dark-purple',
  DARK_TEAL = 'dark-teal',
  DARK_BROWN = 'dark-brown'
}

export class EpubThemeUtil {
  static readonly themesMap = new Map<string, any>([
    [EpubTheme.BLACK, {
      "body": {"background-color": "#000000", "color": "#f9f9f9"},
      "p": {"color": "#f9f9f9"},
      "h1, h2, h3, h4, h5, h6": {"color": "#f9f9f9"},
      "a": {"color": "#f9f9f9"},
      "img": {"-webkit-filter": "none", "filter": "none"},
      "code": {"color": "#00ff00", "background-color": "black"}
    }],
    [EpubTheme.SEPIA, {
      "body": {"background-color": "#f4ecd8", "color": "#6e4b3a"},
      "p": {"color": "#6e4b3a"},
      "h1, h2, h3, h4, h5, h6": {"color": "#6e4b3a"},
      "a": {"color": "#8b4513"},
      "img": {"-webkit-filter": "none", "filter": "none"},
      "code": {"color": "#8b0000", "background-color": "#f4ecd8"}
    }],
    [EpubTheme.WHITE, {
      "body": {"background-color": "#ffffff", "color": "#000000"},
      "p": {"color": "#000000"},
      "h1, h2, h3, h4, h5, h6": {"color": "#000000"},
      "a": {"color": "#000000"},
      "img": {"-webkit-filter": "none", "filter": "none"},
      "code": {"color": "#d14", "background-color": "#f5f5f5"}
    }],
    [EpubTheme.GREY, {
      "body": {"background-color": "#404040", "color": "#d3d3d3"},
      "p": {"color": "#d3d3d3"},
      "h1, h2, h3, h4, h5, h6": {"color": "#d3d3d3"},
      "a": {"color": "#1e90ff"},
      "img": {"filter": "none"},
      "code": {"color": "#d14", "background-color": "#585858"}
    }],
    [EpubTheme.GREEN, {
      "body": {"background-color": "rgb(232, 245, 233)", "color": "#1b5e20"},
      "p": {"color": "#1b5e20"},
      "h1, h2, h3, h4, h5, h6": {"color": "#1b5e20"},
      "a": {"color": "#2e7d32"},
      "img": {"filter": "none"},
      "code": {"color": "#c62828", "background-color": "#e8f5e9"}
    }],
    [EpubTheme.LAVENDER, {
      "body": {"background-color": "rgb(243, 237, 247)", "color": "#4a148c"},
      "p": {"color": "#4a148c"},
      "h1, h2, h3, h4, h5, h6": {"color": "#4a148c"},
      "a": {"color": "#6a1b9a"},
      "img": {"filter": "none"},
      "code": {"color": "#c62828", "background-color": "#f3e5f5"}
    }],
    [EpubTheme.CREAM, {
      "body": {"background-color": "rgb(255, 253, 245)", "color": "#3e2723"},
      "p": {"color": "#3e2723"},
      "h1, h2, h3, h4, h5, h6": {"color": "#3e2723"},
      "a": {"color": "#5d4037"},
      "img": {"filter": "none"},
      "code": {"color": "#d84315", "background-color": "#fff8e1"}
    }],
    [EpubTheme.LIGHT_BLUE, {
      "body": {"background-color": "rgb(232, 244, 253)", "color": "#01579b"},
      "p": {"color": "#01579b"},
      "h1, h2, h3, h4, h5, h6": {"color": "#01579b"},
      "a": {"color": "#0277bd"},
      "img": {"filter": "none"},
      "code": {"color": "#c62828", "background-color": "#e1f5fe"}
    }],
    [EpubTheme.PEACH, {
      "body": {"background-color": "rgb(255, 243, 238)", "color": "#bf360c"},
      "p": {"color": "#bf360c"},
      "h1, h2, h3, h4, h5, h6": {"color": "#bf360c"},
      "a": {"color": "#d84315"},
      "img": {"filter": "none"},
      "code": {"color": "#c62828", "background-color": "#fbe9e7"}
    }],
    [EpubTheme.MINT, {
      "body": {"background-color": "rgb(224, 247, 250)", "color": "#004d40"},
      "p": {"color": "#004d40"},
      "h1, h2, h3, h4, h5, h6": {"color": "#004d40"},
      "a": {"color": "#00695c"},
      "img": {"filter": "none"},
      "code": {"color": "#c62828", "background-color": "#e0f2f1"}
    }],
    [EpubTheme.DARK_SLATE, {
      "body": {"background-color": "rgb(47, 55, 66)", "color": "#e4e7eb"},
      "p": {"color": "#e4e7eb"},
      "h1, h2, h3, h4, h5, h6": {"color": "#f0f3f7"},
      "a": {"color": "#7dd3fc"},
      "img": {"filter": "brightness(0.9)"},
      "code": {"color": "#fbbf24", "background-color": "#374151"}
    }],
    [EpubTheme.DARK_OLIVE, {
      "body": {"background-color": "rgb(56, 61, 47)", "color": "#e8ecd7"},
      "p": {"color": "#e8ecd7"},
      "h1, h2, h3, h4, h5, h6": {"color": "#f4f7e3"},
      "a": {"color": "#bef264"},
      "img": {"filter": "brightness(0.9)"},
      "code": {"color": "#fde047", "background-color": "#3f4536"}
    }],
    [EpubTheme.DARK_PURPLE, {
      "body": {"background-color": "rgb(49, 39, 67)", "color": "#e9d5ff"},
      "p": {"color": "#e9d5ff"},
      "h1, h2, h3, h4, h5, h6": {"color": "#f3e8ff"},
      "a": {"color": "#d8b4fe"},
      "img": {"filter": "brightness(0.9)"},
      "code": {"color": "#fde047", "background-color": "#3b2d4f"}
    }],
    [EpubTheme.DARK_TEAL, {
      "body": {"background-color": "rgb(29, 53, 56)", "color": "#ccfbf1"},
      "p": {"color": "#ccfbf1"},
      "h1, h2, h3, h4, h5, h6": {"color": "#e0fdf8"},
      "a": {"color": "#5eead4"},
      "img": {"filter": "brightness(0.9)"},
      "code": {"color": "#fde047", "background-color": "#204145"}
    }],
    [EpubTheme.DARK_BROWN, {
      "body": {"background-color": "rgb(54, 42, 36)", "color": "#f5e6d3"},
      "p": {"color": "#f5e6d3"},
      "h1, h2, h3, h4, h5, h6": {"color": "#fef3e2"},
      "a": {"color": "#fcd34d"},
      "img": {"filter": "brightness(0.9)"},
      "code": {"color": "#fde047", "background-color": "#42332d"}
    }]
  ]);

  static getThemeColor(themeKey: string | undefined): string {
    switch (themeKey) {
      case EpubTheme.WHITE:
        return '#ffffff';
      case EpubTheme.BLACK:
        return '#000000';
      case EpubTheme.GREY:
        return '#808080';
      case EpubTheme.SEPIA:
        return '#704214';
      case EpubTheme.GREEN:
        return 'rgb(232, 245, 233)';
      case EpubTheme.LAVENDER:
        return 'rgb(243, 237, 247)';
      case EpubTheme.CREAM:
        return 'rgb(255, 253, 245)';
      case EpubTheme.LIGHT_BLUE:
        return 'rgb(232, 244, 253)';
      case EpubTheme.PEACH:
        return 'rgb(255, 243, 238)';
      case EpubTheme.MINT:
        return 'rgb(224, 247, 250)';
      case EpubTheme.DARK_SLATE:
        return 'rgb(47, 55, 66)';
      case EpubTheme.DARK_OLIVE:
        return 'rgb(56, 61, 47)';
      case EpubTheme.DARK_PURPLE:
        return 'rgb(49, 39, 67)';
      case EpubTheme.DARK_TEAL:
        return 'rgb(29, 53, 56)';
      case EpubTheme.DARK_BROWN:
        return 'rgb(54, 42, 36)';
      default:
        return '#ffffff';
    }
  }

  static applyTheme(rendition: any, themeKey: string, fontFamily?: string, fontSize?: number, lineHeight?: number, letterSpacing?: number): void {
    if (!rendition) return;

    const baseTheme = this.themesMap.get(themeKey ?? 'black') ?? {};
    const combined = {
      ...baseTheme,
      body: {
        ...baseTheme.body,
        'font-family': fontFamily,
        'font-size': `${fontSize ?? 100}%`,
        'line-height': lineHeight,
        'letter-spacing': `${letterSpacing}em`
      },
      '*': {
        ...baseTheme['*'],
        'line-height': lineHeight,
        'letter-spacing': `${letterSpacing}em`
      }
    };

    rendition.themes.register('custom', combined);
    rendition.themes.select('custom');
  }
}
