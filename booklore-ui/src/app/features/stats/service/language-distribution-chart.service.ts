import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book} from '../../book/model/book.model';

function hasClass(cls: string): boolean {
  return document.documentElement.classList.contains(cls);
}

type ThemeMode = 'dark' | 'light';

function themeMode(): ThemeMode {
  return hasClass('p-dark') ? 'dark' : 'light';
}

function themeTokens() {
  const mode = themeMode();
  return {
    mode,
    modeColor: mode === 'dark' ? '#ffffff' : '#000000',
    modeColorBG: mode === 'dark' ? 'rgba(0, 0, 0, 0.9)' : 'rgba(255, 255, 255, 0.9)',
  };
}

interface LanguageStats {
  language: string;
  count: number;
  percentage: number;
}

const CHART_COLORS = [
  '#4e79a7', '#f28e2c', '#e15759', '#76b7b2', '#59a14f',
  '#edc949', '#af7aa1', '#ff9da7', '#9c755f', '#bab0ab',
  '#17becf', '#bcbd22', '#1f77b4', '#ff7f0e', '#2ca02c'
] as const;

type LanguageChartData = ChartData<'doughnut', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class LanguageDistributionChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();
  private themeObserver: MutationObserver | null = null;

  public readonly languageChartType = 'doughnut' as const;

  public readonly languageChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          padding: 12,
          usePointStyle: true,
          generateLabels: this.generateLegendLabels.bind(this)
        }
      },
      tooltip: {
        enabled: true,
        backgroundColor: themeTokens().modeColorBG,
        titleColor: themeTokens().modeColor,
        bodyColor: themeTokens().modeColor,
        borderColor: themeTokens().modeColor,
        borderWidth: 1,
        cornerRadius: 6,
        displayColors: true,
        padding: 12,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 12},
        position: 'nearest',
        callbacks: {
          title: (context) => context[0]?.label || '',
          label: this.formatTooltipLabel
        }
      }
    },
    interaction: {
      intersect: false,
      mode: 'point'
    },
    cutout: '45%'
  };

  private readonly languageChartDataSubject = new BehaviorSubject<LanguageChartData>({
    labels: [],
    datasets: [{
      data: [],
      backgroundColor: [...CHART_COLORS],
      borderColor: themeTokens().modeColor,
      borderWidth: 2,
      hoverBorderWidth: 3,
      hoverBorderColor: themeTokens().modeColor
    }]
  });

  public readonly languageChartData$: Observable<LanguageChartData> = this.languageChartDataSubject.asObservable();

  constructor() {
  	this.initThemeObserver();
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded),
        first(),
        switchMap(() =>
          this.libraryFilterService.selectedLibrary$.pipe(
            takeUntil(this.destroy$)
          )
        ),
        catchError((error) => {
          console.error('Error processing language stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const stats = this.calculateLanguageStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.themeObserver) {
      this.themeObserver.disconnect();
    }
  }

  private initThemeObserver(): void {
    this.themeObserver = new MutationObserver((mutations) => {
      let shouldUpdate = false;
      for (const mutation of mutations) {
        if (mutation.type === 'attributes' && mutation.attributeName === 'class') {
          shouldUpdate = true;
          break;
        }
      }
      if (shouldUpdate) {
        this.updateChartTheme();
      }
    });

    this.themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class']
    });
  }

  private updateChartTheme(): void {
    const tokens = themeTokens();
    if (this.languageChartOptions?.plugins?.tooltip) {
      const tooltip = this.languageChartOptions.plugins.tooltip;
      tooltip.backgroundColor = tokens.modeColorBG;
      tooltip.titleColor = tokens.modeColor;
      tooltip.bodyColor = tokens.modeColor;
      tooltip.borderColor = tokens.modeColor;
    }
    const currentData = this.languageChartDataSubject.getValue();
    if (currentData.datasets && currentData.datasets.length > 0) {
      const dataset = currentData.datasets[0];
      dataset.borderColor = tokens.modeColor;
      dataset.hoverBorderColor = tokens.modeColor;
      this.languageChartDataSubject.next({
        ...currentData,
        datasets: [{ ...dataset }]
      });
    }
  }

  private updateChartData(stats: LanguageStats[]): void {
    try {
      const topLanguages = stats.slice(0, 12); // Show top 12 languages
      const labels = topLanguages.map(s => s.language);
      const dataValues = topLanguages.map(s => s.count);
      const colors = this.getColorsForData(topLanguages.length);
      const tokens = themeTokens();

      this.languageChartDataSubject.next({
        labels,
        datasets: [{
          data: dataValues,
          backgroundColor: colors,
          borderColor: tokens.modeColor,
          borderWidth: 2,
          hoverBorderWidth: 3,
          hoverBorderColor: tokens.modeColor
        }]
      });
    } catch (error) {
      console.error('Error updating language chart data:', error);
    }
  }

  private getColorsForData(dataLength: number): string[] {
    const colors = [...CHART_COLORS];
    while (colors.length < dataLength) {
      colors.push(...CHART_COLORS);
    }
    return colors.slice(0, dataLength);
  }

  private calculateLanguageStats(): LanguageStats[] {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, String(selectedLibraryId));
    return this.processLanguageStats(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private processLanguageStats(books: Book[]): LanguageStats[] {
    if (books.length === 0) {
      return [];
    }

    const languageMap = this.buildLanguageMap(books);
    return this.convertMapToStats(languageMap, books.length);
  }

  private buildLanguageMap(books: Book[]): Map<string, number> {
    const languageMap = new Map<string, number>();

    for (const book of books) {
      const language = book.metadata?.language;
      if (language && language.trim()) {
        const normalizedLanguage = this.normalizeLanguage(language.trim());
        languageMap.set(normalizedLanguage, (languageMap.get(normalizedLanguage) || 0) + 1);
      } else {
        languageMap.set('Unknown', (languageMap.get('Unknown') || 0) + 1);
      }
    }

    return languageMap;
  }

  private normalizeLanguage(language: string): string {
    const languageMap: Record<string, string> = {
      'en': 'English',
      'eng': 'English',
      'english': 'English',
      'es': 'Spanish',
      'spa': 'Spanish',
      'spanish': 'Spanish',
      'fr': 'French',
      'fre': 'French',
      'fra': 'French',
      'french': 'French',
      'de': 'German',
      'ger': 'German',
      'deu': 'German',
      'german': 'German',
      'it': 'Italian',
      'ita': 'Italian',
      'italian': 'Italian',
      'pt': 'Portuguese',
      'por': 'Portuguese',
      'portuguese': 'Portuguese',
      'ru': 'Russian',
      'rus': 'Russian',
      'russian': 'Russian',
      'ja': 'Japanese',
      'jpn': 'Japanese',
      'japanese': 'Japanese',
      'zh': 'Chinese',
      'chi': 'Chinese',
      'chinese': 'Chinese',
      'ko': 'Korean',
      'kor': 'Korean',
      'korean': 'Korean',
      'ar': 'Arabic',
      'ara': 'Arabic',
      'arabic': 'Arabic'
    };

    const normalized = language.toLowerCase();
    return languageMap[normalized] || this.capitalizeFirst(language);
  }

  private capitalizeFirst(str: string): string {
    return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
  }

  private convertMapToStats(languageMap: Map<string, number>, totalBooks: number): LanguageStats[] {
    return Array.from(languageMap.entries())
      .map(([language, count]) => ({
        language,
        count,
        percentage: Number(((count / totalBooks) * 100).toFixed(1))
      }))
      .sort((a, b) => b.count - a.count);
  }

  private generateLegendLabels(chart: any) {
    const data = chart.data;
    if (!data.labels?.length || !data.datasets?.[0]?.data?.length) {
      return [];
    }

    const dataset = data.datasets[0];
    const dataValues = dataset.data as number[];
    const tokens = themeTokens();

    return data.labels.map((label: string, index: number) => {
      const isVisible = typeof chart.getDataVisibility === 'function'
        ? chart.getDataVisibility(index)
        : !((chart.getDatasetMeta && chart.getDatasetMeta(0)?.data?.[index]?.hidden) || false);

      return {
        text: `${label} (${dataValues[index]})`,
        fillStyle: (dataset.backgroundColor as string[])[index],
        strokeStyle: tokens.modeColor,
        lineWidth: 1,
        hidden: !isVisible,
        index,
        fontColor: tokens.modeColor
      };
    });
  }

  private formatTooltipLabel(context: any): string {
    const dataIndex = context.dataIndex;
    const dataset = context.dataset;
    const value = dataset.data[dataIndex] as number;
    const label = context.chart.data.labels?.[dataIndex] || 'Unknown';
    const total = (dataset.data as number[]).reduce((a: number, b: number) => a + b, 0);
    const percentage = ((value / total) * 100).toFixed(1);
    return `${label}: ${value} books (${percentage}%)`;
  }
}
