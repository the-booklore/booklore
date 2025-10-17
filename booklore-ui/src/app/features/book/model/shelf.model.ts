import {SortOption} from './sort.model';

export interface Shelf {
  id?: number;
  name: string;
  icon: string;
  sort?: SortOption;
}
