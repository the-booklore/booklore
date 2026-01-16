import {describe, expect, it, vi} from 'vitest';
import {FilterLabelHelper} from './filter-label.helper';

vi.mock('./book-filter/book-filter.component', () => ({
  fileSizeRanges: [
    {id: 'small', label: 'Small Files'},
    {id: 'large', label: 'Large Files'}
  ],
  pageCountRanges: [
    {id: 'short', label: 'Short Books'},
    {id: 'long', label: 'Long Books'}
  ],
  matchScoreRanges: [
    {id: 'high', label: 'High Match'},
    {id: 'low', label: 'Low Match'}
  ],
  ratingOptions10: [
    {id: '5', label: 'Five Stars'},
    {id: '10', label: 'Ten Stars'}
  ],
  ratingRanges: [
    {id: 'A', label: 'A Range'},
    {id: 'B', label: 'B Range'}
  ]
}));

describe('FilterLabelHelper', () => {
  describe('getFilterTypeName', () => {
    it('should return mapped label for known filter type', () => {
      expect(FilterLabelHelper.getFilterTypeName('author')).toBe('Author');
      expect(FilterLabelHelper.getFilterTypeName('category')).toBe('Genre');
      expect(FilterLabelHelper.getFilterTypeName('series')).toBe('Series');
    });

    it('should capitalize and return unknown filter type', () => {
      expect(FilterLabelHelper.getFilterTypeName('unknownType')).toBe('UnknownType');
      expect(FilterLabelHelper.getFilterTypeName('custom')).toBe('Custom');
    });
  });

  describe('getFilterDisplayValue', () => {
    it('should return file size label for known id', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('fileSize', 'small')).toBe('small');
      expect(FilterLabelHelper.getFilterDisplayValue('filesize', 'large')).toBe('large');
    });

    it('should return value if file size id not found', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('fileSize', 'unknown')).toBe('unknown');
    });

    it('should return page count label for known id', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('pageCount', 'short')).toBe('short');
      expect(FilterLabelHelper.getFilterDisplayValue('pagecount', 'long')).toBe('long');
    });

    it('should return value if page count id not found', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('pageCount', 'unknown')).toBe('unknown');
    });

    it('should return match score label for known id', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('matchScore', 'high')).toBe('high');
      expect(FilterLabelHelper.getFilterDisplayValue('matchscore', 'low')).toBe('low');
    });

    it('should return value if match score id not found', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('matchScore', 'unknown')).toBe('unknown');
    });

    it('should return personal rating label for known id', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('personalRating', '5')).toBe('5');
      expect(FilterLabelHelper.getFilterDisplayValue('personalrating', '10')).toBe('10');
    });

    it('should return value if personal rating id not found', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('personalRating', 'unknown')).toBe('unknown');
    });

    it('should return rating range label for amazon/goodreads/hardcover', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('amazonRating', 'A')).toBe('A');
      expect(FilterLabelHelper.getFilterDisplayValue('goodreadsRating', 'B')).toBe('B');
      expect(FilterLabelHelper.getFilterDisplayValue('hardcoverRating', 'A')).toBe('A');
    });

    it('should return value if rating range id not found', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('amazonRating', 'unknown')).toBe('unknown');
      expect(FilterLabelHelper.getFilterDisplayValue('goodreadsRating', 'unknown')).toBe('unknown');
      expect(FilterLabelHelper.getFilterDisplayValue('hardcoverRating', 'unknown')).toBe('unknown');
    });

    it('should return value for unknown filter type', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('unknownType', 'someValue')).toBe('someValue');
    });
  });

  describe('capitalize', () => {
    it('should capitalize the first letter', () => {
      // @ts-expect-private
      // @ts-ignore
      expect(FilterLabelHelper.capitalize('test')).toBe('Test');
      // @ts-ignore
      expect(FilterLabelHelper.capitalize('T')).toBe('T');
      // @ts-ignore
      expect(FilterLabelHelper.capitalize('')).toBe('');
    });
  });
});
