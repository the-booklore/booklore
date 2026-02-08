import {SortOption} from './sort.model';
import {BookType} from './book.model';

export interface Library {
  id?: number;
  name: string;
  icon?: string | null;
  iconType?: 'PRIME_NG' | 'CUSTOM_SVG' | null;
  watch: boolean;
  fileNamingPattern?: string;
  sort?: SortOption;
  paths: LibraryPath[];
  formatPriority?: BookType[];
  allowedFormats?: BookType[];
}

export interface LibraryPath {
  id?: number;
  path: string;
}
