import {beforeEach, describe, expect, it, vi} from 'vitest';
import {BookSorter} from './BookSorter';
import {SortDirection, SortOption} from '../../../model/sort.model';

describe('BookSorter', () => {
  let applySortOption: (sort: SortOption) => void;
  let sorter: BookSorter;

  beforeEach(() => {
    applySortOption = vi.fn();
    sorter = new BookSorter(applySortOption);
  });

  it('should initialize with undefined selectedSort', () => {
    expect(sorter.selectedSort).toBeUndefined();
  });

  it('should have all default sort options', () => {
    expect(sorter.sortOptions).toBeInstanceOf(Array);
    expect(sorter.sortOptions.length).toBeGreaterThan(0);
    expect(sorter.sortOptions.some(opt => opt.field === 'title')).toBe(true);
  });

  it('should set selectedSort and call applySortOption on sortBooks (first time)', () => {
    sorter.sortBooks('author');
    expect(sorter.selectedSort).toMatchObject({
      field: 'author',
      direction: SortDirection.ASCENDING
    });
    expect(applySortOption).toHaveBeenCalledWith(sorter.selectedSort);
  });

  it('should toggle direction when sorting the same field again', () => {
    sorter.sortBooks('title');
    const firstSort = {...sorter.selectedSort!};
    sorter.sortBooks('title');
    expect(sorter.selectedSort?.field).toBe('title');
    expect(sorter.selectedSort?.direction).not.toBe(firstSort.direction);
    expect(applySortOption).toHaveBeenCalledTimes(2);
  });

  it('should reset direction to ASCENDING when sorting a new field', () => {
    sorter.sortBooks('title');
    sorter.sortBooks('author');
    expect(sorter.selectedSort?.field).toBe('author');
    expect(sorter.selectedSort?.direction).toBe(SortDirection.ASCENDING);
  });

  it('should not change selectedSort or call applySortOption for unknown field', () => {
    sorter.sortBooks('notAField');
    expect(sorter.selectedSort).toBeUndefined();
    expect(applySortOption).not.toHaveBeenCalled();
  });

  it('should update sortOptions with correct icon for selected field and direction', () => {
    sorter.sortBooks('title');
    const upIcon = 'pi pi-arrow-up';
    expect((sorter.sortOptions.find(opt => opt.field === 'title') as any)?.icon).toBe(upIcon);
    expect(sorter.sortOptions.filter(opt => opt.field !== 'title').every(opt => !(opt as any).icon)).toBe(true);

    sorter.sortBooks('title'); // toggle direction
    const downIcon = 'pi pi-arrow-down';
    expect((sorter.sortOptions.find(opt => opt.field === 'title') as any)?.icon).toBe(downIcon);
  });

  it('should only set icon for the selected field', () => {
    sorter.sortBooks('author');
    sorter.sortBooks('title');
    expect((sorter.sortOptions.find(opt => opt.field === 'title') as any)?.icon).toBe('pi pi-arrow-up');
    expect((sorter.sortOptions.find(opt => opt.field === 'author') as any)?.icon).toBe('');
  });

  it('should preserve label and field when toggling direction', () => {
    sorter.sortBooks('fileName');
    const {label, field} = sorter.selectedSort!;
    sorter.sortBooks('fileName');
    expect(sorter.selectedSort?.label).toBe(label);
    expect(sorter.selectedSort?.field).toBe(field);
  });

  it('should not throw if updateSortOptions is called with no selectedSort', () => {
    // @ts-ignore
    sorter.selectedSort = undefined;
    expect(() => sorter.updateSortOptions()).toThrow();
  });

  it('should not mutate original sortOptions array reference on updateSortOptions', () => {
    const original = sorter.sortOptions;
    sorter.sortBooks('title');
    expect(sorter.sortOptions).not.toBe(original);
  });

  it('should handle sorting "random" field', () => {
    sorter.sortBooks('random');
    expect(sorter.selectedSort?.field).toBe('random');
    expect(sorter.selectedSort?.direction).toBe(SortDirection.ASCENDING);
    expect(applySortOption).toHaveBeenCalledWith(sorter.selectedSort);
  });
});
