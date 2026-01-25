import {SortOption} from './sort.model';

export type BookFileType = 'PDF' | 'EPUB' | 'CBX' | 'FB2' | 'MOBI' | 'AZW3' | 'AUDIOBOOK';

export interface Library {
  id?: number;
  name: string;
  icon: string;
  iconType?: 'PRIME_NG' | 'CUSTOM_SVG';
  watch: boolean;
  fileNamingPattern?: string;
  sort?: SortOption;
  paths: LibraryPath[];
  formatPriority?: BookFileType[];
}

export interface LibraryPath {
  id?: number;
  path: string;
}
