import {SortOption} from './sort.model';
import {BookType} from './book.model';

export type MetadataSource = 'EMBEDDED' | 'SIDECAR' | 'PREFER_SIDECAR' | 'PREFER_EMBEDDED' | 'NONE';

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
  metadataSource?: MetadataSource;
}

export interface LibraryPath {
  id?: number;
  path: string;
}
