import {SortDirection, SortOption} from '../../../model/sort.model';

export class BookSorter {
  selectedSort: SortOption | undefined = undefined;

  sortOptions: SortOption[] = [
    { label: 'Title', field: 'title', direction: SortDirection.ASCENDING },
    { label: 'Title + Series', field: 'titleSeries', direction: SortDirection.ASCENDING },
    { label: 'Author', field: 'author', direction: SortDirection.ASCENDING },
    { label: 'Last Read', field: 'lastReadTime', direction: SortDirection.ASCENDING },
    { label: 'Added On', field: 'addedOn', direction: SortDirection.ASCENDING },
    { label: 'File Size', field: 'fileSizeKb', direction: SortDirection.ASCENDING },
    { label: 'Locked', field: 'locked', direction: SortDirection.ASCENDING },
    { label: 'Publisher', field: 'publisher', direction: SortDirection.ASCENDING },
    { label: 'Published Date', field: 'publishedDate', direction: SortDirection.ASCENDING },
    { label: 'Amazon Rating', field: 'amazonRating', direction: SortDirection.ASCENDING },
    { label: 'Amazon #', field: 'amazonReviewCount', direction: SortDirection.ASCENDING },
    { label: 'Goodreads Rating', field: 'goodreadsRating', direction: SortDirection.ASCENDING },
    { label: 'Goodreads #', field: 'goodreadsReviewCount', direction: SortDirection.ASCENDING },
    { label: 'Hardcover Rating', field: 'hardcoverRating', direction: SortDirection.ASCENDING },
    { label: 'Hardcover #', field: 'hardcoverReviewCount', direction: SortDirection.ASCENDING },
    { label: 'Pages', field: 'pageCount', direction: SortDirection.ASCENDING },
  ];

  constructor(private applySortOption: (sort: SortOption) => void) {}

  sortBooks(field: string): void {
    const existingSort = this.sortOptions.find(opt => opt.field === field);
    if (!existingSort) return;

    if (this.selectedSort?.field === field) {
      this.selectedSort = {
        ...this.selectedSort,
        direction: this.selectedSort.direction === SortDirection.ASCENDING
          ? SortDirection.DESCENDING
          : SortDirection.ASCENDING
      };
    } else {
      this.selectedSort = {
        label: existingSort.label,
        field: existingSort.field,
        direction: SortDirection.ASCENDING
      };
    }

    this.updateSortOptions();
    this.applySortOption(this.selectedSort);
  }

  updateSortOptions() {
    const directionIcon = this.selectedSort!.direction === SortDirection.ASCENDING ? 'pi pi-arrow-up' : 'pi pi-arrow-down';
    this.sortOptions = this.sortOptions.map((option) => ({
      ...option,
      icon: option.field === this.selectedSort!.field ? directionIcon : '',
    }));
  }
}
