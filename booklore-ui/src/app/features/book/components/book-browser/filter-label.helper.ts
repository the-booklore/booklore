import {
  fileSizeRanges,
  matchScoreRanges,
  pageCountRanges,
  ratingOptions10,
  ratingRanges
} from './book-filter/book-filter.component';

export class FilterLabelHelper {
  private static readonly FILTER_TYPE_MAP: Record<string, string> = {
    author: 'Author',
    category: 'Genre',
    series: 'Series',
    publisher: 'Publisher',
    readStatus: 'Read Status',
    personalRating: 'Personal Rating',
    publishedDate: 'Year Published',
    matchScore: 'Metadata Match Score',
    language: 'Language',
    bookType: 'Book Type',
    shelfStatus: 'Shelf Status',
    fileSize: 'File Size',
    pageCount: 'Page Count',
    amazonRating: 'Amazon Rating',
    goodreadsRating: 'Goodreads Rating',
    hardcoverRating: 'Hardcover Rating',
    mood: 'Mood',
    tag: 'Tag',
  };

  static getFilterTypeName(filterType: string): string {
    return this.FILTER_TYPE_MAP[filterType] || this.capitalize(filterType);
  }

  static getFilterDisplayValue(filterType: string, value: string): string {
    switch (filterType.toLowerCase()) {
      case 'filesize':
        // Try both lower and original case for id match
        {
          const fileSizeRange = fileSizeRanges.find(r => r.id === value);
          if (fileSizeRange) return fileSizeRange.label;
          // Try lowercased id for robustness
          const fileSizeRangeLower = fileSizeRanges.find(r => r.id === value.toLowerCase());
          if (fileSizeRangeLower) return fileSizeRangeLower.label;
          return value;
        }
      case 'pagecount':
        {
          const pageCountRange = pageCountRanges.find(r => r.id === value);
          if (pageCountRange) return pageCountRange.label;
          const pageCountRangeLower = pageCountRanges.find(r => r.id === value.toLowerCase());
          if (pageCountRangeLower) return pageCountRangeLower.label;
          return value;
        }
      case 'matchscore':
        {
          const matchScoreRange = matchScoreRanges.find(r => r.id === value);
          if (matchScoreRange) return matchScoreRange.label;
          const matchScoreRangeLower = matchScoreRanges.find(r => r.id === value.toLowerCase());
          if (matchScoreRangeLower) return matchScoreRangeLower.label;
          return value;
        }
      case 'personalrating':
        {
          const personalRating = ratingOptions10.find(r => r.id === value);
          if (personalRating) return personalRating.label;
          const personalRatingLower = ratingOptions10.find(r => r.id === value.toLowerCase());
          if (personalRatingLower) return personalRatingLower.label;
          return value;
        }
      case 'amazonrating':
      case 'goodreadsrating':
      case 'hardcoverrating':
        {
          const ratingRange = ratingRanges.find(r => r.id === value);
          if (ratingRange) return ratingRange.label;
          const ratingRangeLower = ratingRanges.find(r => r.id === value.toLowerCase());
          if (ratingRangeLower) return ratingRangeLower.label;
          return value;
        }
      default:
        return value;
    }
  }

  private static capitalize(str: string): string {
    return str.charAt(0).toUpperCase() + str.slice(1);
  }
}
