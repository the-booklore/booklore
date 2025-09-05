import {inject, Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, EMPTY, Observable, Subject, of} from 'rxjs';
import {map, takeUntil, catchError, filter, first, switchMap} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../book/service/book.service';
import {Book, ReadStatus} from '../../book/model/book.model';

interface ReadingDNAProfile {
  adventurous: number; // Genre diversity and experimental reading
  perfectionist: number; // Completion rate and quality focus
  intellectual: number; // Non-fiction preference and complex books
  emotional: number; // Fiction preference and rating engagement
  patient: number; // Long book preference and series completion
  social: number; // Popular books and mainstream choices
  nostalgic: number; // Older books and classic literature
  ambitious: number; // Reading volume and challenging material
}

interface PersonalityInsight {
  trait: string;
  score: number;
  description: string;
  color: string;
}

type ReadingDNAChartData = ChartData<'radar', number[], string>;

@Injectable({
  providedIn: 'root'
})
export class ReadingDNAChartService implements OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly readingDNAChartType = 'radar' as const;

  public readonly readingDNAChartOptions: ChartConfiguration<'radar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      r: {
        beginAtZero: true,
        min: 0,
        max: 100,
        ticks: {
          stepSize: 20,
          color: 'rgba(255, 255, 255, 0.6)',
          font: {
            family: "'Inter', sans-serif",
            size: 12
          },
          backdropColor: 'transparent',
          showLabelBackdrop: false
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.2)',
          circular: true
        },
        angleLines: {
          color: 'rgba(255, 255, 255, 0.3)'
        },
        pointLabels: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 12
          },
          padding: 25,
          callback: function (label: string) {
            const icons: Record<string, string> = {
              'Adventurous': '🌟',
              'Perfectionist': '💎',
              'Intellectual': '🧠',
              'Emotional': '💖',
              'Patient': '🕰️',
              'Social': '👥',
              'Nostalgic': '📚',
              'Ambitious': '🚀'
            };
            return [icons[label] || '', label];
          }
        }
      }
    },
    plugins: {
      legend: {
        display: false
      },
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.95)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#4fc3f7',
        borderWidth: 2,
        cornerRadius: 8,
        padding: 16,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 12},
        callbacks: {
          title: (context) => {
            const label = context[0]?.label || '';
            return `${label} Personality`;
          },
          label: (context) => {
            const score = context.parsed.r;
            const insights = this.getPersonalityInsights();
            const insight = insights.find(i => i.trait === context.label);

            return [
              `Score: ${score.toFixed(1)}/100`,
              '',
              insight ? insight.description : 'Your reading personality trait'
            ];
          }
        }
      }
    },
    interaction: {
      intersect: false,
      mode: 'point'
    },
    elements: {
      line: {
        borderWidth: 3,
        tension: 0.1
      },
      point: {
        radius: 5,
        hoverRadius: 8,
        borderWidth: 3,
        backgroundColor: 'rgba(255, 255, 255, 0.8)'
      }
    }
  };

  private readonly readingDNAChartDataSubject = new BehaviorSubject<ReadingDNAChartData>({
    labels: [
      'Adventurous', 'Perfectionist', 'Intellectual', 'Emotional',
      'Patient', 'Social', 'Nostalgic', 'Ambitious'
    ],
    datasets: []
  });

  public readonly readingDNAChartData$: Observable<ReadingDNAChartData> = this.readingDNAChartDataSubject.asObservable();
  private lastCalculatedInsights: PersonalityInsight[] = [];

  constructor() {
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
          console.error('Error processing reading DNA data:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        const profile = this.calculateReadingDNAData();
        this.updateChartData(profile);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(profile: ReadingDNAProfile | null): void {
    try {
      if (!profile) {
        this.readingDNAChartDataSubject.next({
          labels: [],
          datasets: []
        });
        return;
      }

      const data = [
        profile.adventurous,
        profile.perfectionist,
        profile.intellectual,
        profile.emotional,
        profile.patient,
        profile.social,
        profile.nostalgic,
        profile.ambitious
      ];

      // Create gradient effect colors
      const gradientColors = [
        '#ff6b9d', '#45aaf2', '#96f7d2', '#feca57',
        '#ff9ff3', '#54a0ff', '#5f27cd', '#00d2d3'
      ];

      this.readingDNAChartDataSubject.next({
        labels: [
          'Adventurous', 'Perfectionist', 'Intellectual', 'Emotional',
          'Patient', 'Social', 'Nostalgic', 'Ambitious'
        ],
        datasets: [{
          label: 'Reading DNA Profile',
          data,
          backgroundColor: 'rgba(79, 195, 247, 0.2)',
          borderColor: '#4fc3f7',
          borderWidth: 3,
          pointBackgroundColor: gradientColors,
          pointBorderColor: '#ffffff',
          pointBorderWidth: 3,
          pointRadius: 5,
          pointHoverRadius: 8,
          fill: true
        }]
      });

      // Store insights for tooltip
      this.lastCalculatedInsights = this.convertToPersonalityInsights(profile);
    } catch (error) {
      console.error('Error updating reading DNA chart data:', error);
    }
  }

  private calculateReadingDNAData(): ReadingDNAProfile | null {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      return null;
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, String(selectedLibraryId));
    return this.analyzeReadingDNA(filteredBooks);
  }

  private isValidBookState(state: any): boolean {
    return state?.loaded && state?.books && Array.isArray(state.books) && state.books.length > 0;
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | null): Book[] {
    return selectedLibraryId && selectedLibraryId !== 'null'
      ? books.filter(book => String(book.libraryId) === selectedLibraryId)
      : books;
  }

  private analyzeReadingDNA(books: Book[]): ReadingDNAProfile {
    if (books.length === 0) {
      return this.getDefaultProfile();
    }

    return {
      adventurous: this.calculateAdventurousScore(books),
      perfectionist: this.calculatePerfectionistScore(books),
      intellectual: this.calculateIntellectualScore(books),
      emotional: this.calculateEmotionalScore(books),
      patient: this.calculatePatienceScore(books),
      social: this.calculateSocialScore(books),
      nostalgic: this.calculateNostalgicScore(books),
      ambitious: this.calculateAmbitiousScore(books)
    };
  }

  private calculateAdventurousScore(books: Book[]): number {
    // Genre diversity + experimental choices + different formats
    const genres = new Set<string>();
    const languages = new Set<string>();
    const formats = new Set<string>();

    books.forEach(book => {
      book.metadata?.categories?.forEach(cat => genres.add(cat.toLowerCase()));
      if (book.metadata?.language) languages.add(book.metadata.language);
      formats.add(book.bookType);
    });

    const genreScore = Math.min(60, genres.size * 4); // Max 60 for 15+ genres
    const languageScore = Math.min(20, languages.size * 10); // Max 20 for 2+ languages
    const formatScore = Math.min(20, formats.size * 7); // Max 20 for 3+ formats

    return genreScore + languageScore + formatScore;
  }

  private calculatePerfectionistScore(books: Book[]): number {
    const completedBooks = books.filter(b => b.readStatus === ReadStatus.READ);
    const completionRate = completedBooks.length / books.length;

    // High-quality book preference
    const qualityBooks = books.filter(book => {
      const metadata = book.metadata;
      if (!metadata) return false;
      return (metadata.goodreadsRating && metadata.goodreadsRating >= 4.0) ||
        (metadata.amazonRating && metadata.amazonRating >= 4.0) ||
        (metadata.personalRating && metadata.personalRating >= 4);
    });

    const qualityRate = qualityBooks.length / books.length;
    const completionScore = completionRate * 60; // Max 60
    const qualityScore = qualityRate * 40; // Max 40

    return Math.min(100, completionScore + qualityScore);
  }

  private calculateIntellectualScore(books: Book[]): number {
    const intellectualGenres = [
      'philosophy', 'science', 'history', 'biography', 'politics',
      'psychology', 'sociology', 'economics', 'technology', 'mathematics',
      'physics', 'chemistry', 'medicine', 'law', 'education',
      'anthropology', 'archaeology', 'astronomy', 'biology', 'geology',
      'linguistics', 'neuroscience', 'quantum physics', 'engineering',
      'computer science', 'artificial intelligence', 'data science',
      'research', 'academic', 'scholarly', 'theoretical', 'scientific',
      'analytical', 'critical thinking', 'logic', 'rhetoric',
      'cultural studies', 'international relations', 'diplomacy',
      'public policy', 'governance', 'constitutional law', 'ethics',
      'moral philosophy', 'epistemology', 'metaphysics', 'theology',
      'religious studies', 'comparative religion', 'apologetics'
    ];

    const intellectualBooks = books.filter(book => {
      if (!book.metadata?.categories) return false;
      return book.metadata.categories.some(cat =>
        intellectualGenres.some(genre => cat.toLowerCase().includes(genre))
      );
    });

    // Long books indicate patience for complex material
    const longBooks = books.filter(book =>
      book.metadata?.pageCount && book.metadata.pageCount > 400
    );

    const intellectualRate = intellectualBooks.length / books.length;
    const longBookRate = longBooks.length / books.length;

    return Math.min(100, (intellectualRate * 70) + (longBookRate * 30));
  }

  private calculateEmotionalScore(books: Book[]): number {
    const emotionalGenres = [
      'fiction', 'romance', 'drama', 'literary', 'contemporary',
      'memoir', 'poetry', 'young adult', 'coming of age', 'family',
      'love story', 'relationships', 'emotional', 'heartbreak',
      'healing', 'self-help', 'personal development', 'inspirational',
      'motivational', 'spiritual', 'mindfulness', 'meditation',
      'grief', 'loss', 'trauma', 'recovery', 'therapy',
      'women\'s fiction', 'chick lit', 'new adult', 'teen',
      'childhood', 'parenting', 'motherhood', 'fatherhood',
      'friendship', 'betrayal', 'forgiveness', 'redemption',
      'slice of life', 'domestic fiction', 'family saga',
      'generational saga', 'multicultural', 'immigrant stories',
      'lgbtq+', 'queer fiction', 'feminist', 'gender studies',
      'social issues', 'mental health', 'addiction', 'wellness',
      'autobiography', 'personal narrative', 'diary', 'journal'
    ];

    const emotionalBooks = books.filter(book => {
      if (!book.metadata?.categories) return false;
      return book.metadata.categories.some(cat =>
        emotionalGenres.some(genre => cat.toLowerCase().includes(genre))
      );
    });

    // Personal rating engagement shows emotional connection
    const personallyRatedBooks = books.filter(book => book.metadata?.personalRating);

    const emotionalRate = emotionalBooks.length / books.length;
    const ratingEngagement = personallyRatedBooks.length / books.length;

    return Math.min(100, (emotionalRate * 60) + (ratingEngagement * 40));
  }

  private calculatePatienceScore(books: Book[]): number {
    // Long books + series completion + slow reading pace
    const longBooks = books.filter(book =>
      book.metadata?.pageCount && book.metadata.pageCount > 500
    );

    const seriesBooks = books.filter(book =>
      book.metadata?.seriesName && book.metadata?.seriesNumber
    );

    // Books with high progress indicate patience to work through them
    const progressBooks = books.filter(book => {
      const progress = Math.max(
        book.epubProgress?.percentage || 0,
        book.pdfProgress?.percentage || 0,
        book.cbxProgress?.percentage || 0,
        book.koreaderProgress?.percentage || 0
      );
      return progress > 50;
    });

    const longBookRate = longBooks.length / books.length;
    const seriesRate = seriesBooks.length / books.length;
    const progressRate = progressBooks.length / books.length;

    return Math.min(100, (longBookRate * 40) + (seriesRate * 35) + (progressRate * 25));
  }

  private calculateSocialScore(books: Book[]): number {
    // Popular books (high ratings, many reviews) + mainstream genres
    const popularBooks = books.filter(book => {
      const metadata = book.metadata;
      if (!metadata) return false;
      return (metadata.goodreadsReviewCount && metadata.goodreadsReviewCount > 1000) ||
        (metadata.amazonReviewCount && metadata.amazonReviewCount > 500);
    });

    const mainstreamGenres = [
      'thriller', 'mystery', 'romance', 'fantasy', 'science fiction',
      'horror', 'adventure', 'bestseller', 'contemporary', 'popular',
      'crime', 'detective', 'suspense', 'action', 'espionage',
      'spy', 'police procedural', 'cozy mystery', 'psychological thriller',
      'domestic thriller', 'legal thriller', 'medical thriller',
      'urban fantasy', 'paranormal', 'supernatural', 'magic',
      'dystopian', 'post-apocalyptic', 'cyberpunk', 'space opera',
      'military science fiction', 'hard science fiction', 'steampunk',
      'alternate history', 'time travel', 'vampire', 'werewolf',
      'zombie', 'ghost', 'gothic', 'dark fantasy', 'epic fantasy',
      'sword and sorcery', 'high fantasy', 'historical romance',
      'regency romance', 'western', 'sports', 'celebrity',
      'entertainment', 'pop culture', 'reality tv', 'social media',
      'true crime', 'celebrity biography', 'gossip', 'lifestyle',
      'fashion', 'beauty', 'cooking', 'travel', 'humor',
      'comedy', 'satire', 'graphic novel', 'manga', 'comic'
    ];

    const mainstreamBooks = books.filter(book => {
      if (!book.metadata?.categories) return false;
      return book.metadata.categories.some(cat =>
        mainstreamGenres.some(genre => cat.toLowerCase().includes(genre))
      );
    });

    const popularRate = popularBooks.length / books.length;
    const mainstreamRate = mainstreamBooks.length / books.length;

    return Math.min(100, (popularRate * 50) + (mainstreamRate * 50));
  }

  private calculateNostalgicScore(books: Book[]): number {
    const currentYear = new Date().getFullYear();
    const classicThreshold = currentYear - 30; // Books older than 30 years

    const oldBooks = books.filter(book => {
      if (!book.metadata?.publishedDate) return false;
      const pubYear = new Date(book.metadata.publishedDate).getFullYear();
      return pubYear < classicThreshold;
    });

    const classicGenres = [
      'classic', 'literature', 'historical', 'vintage', 'traditional',
      'heritage', 'timeless', 'canonical', 'masterpiece', 'landmark',
      'seminal', 'influential', 'groundbreaking', 'pioneering',
      'classical literature', 'world literature', 'nobel prize',
      'pulitzer prize', 'booker prize', 'national book award',
      'literary fiction', 'modernist', 'post-modernist', 'realist',
      'naturalist', 'romantic', 'victorian', 'edwardian',
      'renaissance', 'enlightenment', 'ancient', 'medieval',
      'colonial', 'antebellum', 'gilded age', 'jazz age',
      'lost generation', 'beat generation', 'harlem renaissance',
      'golden age', 'silver age', 'folk tales', 'fairy tales',
      'mythology', 'legends', 'folklore', 'oral tradition',
      'epic poetry', 'sonnets', 'ballads', 'odes',
      'dramatic works', 'shakespearean', 'greek tragedy',
      'roman literature', 'biblical', 'religious classics',
      'philosophical classics', 'historical classics'
    ];

    const oldBookRate = oldBooks.length / books.length;
    const classicRate = classicGenres.length / books.length;

    return Math.min(100, (oldBookRate * 60) + (classicRate * 40));
  }

  private calculateAmbitiousScore(books: Book[]): number {
    // Volume + challenging material + completion of difficult books
    const totalBooks = books.length;
    const volumeScore = Math.min(40, totalBooks * 2); // Max 40 for 20+ books

    const challengingBooks = books.filter(book =>
      book.metadata?.pageCount && book.metadata.pageCount > 600
    );

    const completedChallenging = challengingBooks.filter(book =>
      book.readStatus === ReadStatus.READ
    );

    const challengingRate = challengingBooks.length / books.length;
    const completionRate = challengingBooks.length > 0 ?
      completedChallenging.length / challengingBooks.length : 0;

    const challengingScore = challengingRate * 35; // Max 35
    const completionBonus = completionRate * 25; // Max 25

    return Math.min(100, volumeScore + challengingScore + completionBonus);
  }

  private getDefaultProfile(): ReadingDNAProfile {
    return {
      adventurous: 50,
      perfectionist: 50,
      intellectual: 50,
      emotional: 50,
      patient: 50,
      social: 50,
      nostalgic: 50,
      ambitious: 50
    };
  }

  private convertToPersonalityInsights(profile: ReadingDNAProfile): PersonalityInsight[] {
    return [
      {
        trait: 'Adventurous',
        score: profile.adventurous,
        description: 'You explore diverse genres and experimental content',
        color: '#ff6b9d'
      },
      {
        trait: 'Perfectionist',
        score: profile.perfectionist,
        description: 'You prefer high-quality books and finish what you start',
        color: '#45aaf2'
      },
      {
        trait: 'Intellectual',
        score: profile.intellectual,
        description: 'You gravitate toward complex, educational material',
        color: '#96f7d2'
      },
      {
        trait: 'Emotional',
        score: profile.emotional,
        description: 'You connect emotionally with fiction and personal stories',
        color: '#feca57'
      },
      {
        trait: 'Patient',
        score: profile.patient,
        description: 'You tackle long books and complete series',
        color: '#ff9ff3'
      },
      {
        trait: 'Social',
        score: profile.social,
        description: 'You enjoy popular, widely-discussed books',
        color: '#54a0ff'
      },
      {
        trait: 'Nostalgic',
        score: profile.nostalgic,
        description: 'You appreciate classic literature and older works',
        color: '#5f27cd'
      },
      {
        trait: 'Ambitious',
        score: profile.ambitious,
        description: 'You challenge yourself with volume and difficulty',
        color: '#00d2d3'
      }
    ];
  }

  public getPersonalityInsights(): PersonalityInsight[] {
    return this.lastCalculatedInsights;
  }
}
