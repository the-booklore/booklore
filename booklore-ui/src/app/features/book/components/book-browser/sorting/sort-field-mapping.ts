export const SORT_FIELD_TO_API: Record<string, string> = {
  authors: 'author',
  seriesNumber: 'metadata.seriesNumber',
  seriesName: 'metadata.seriesName',
  isbn: 'metadata.isbn13',
  language: 'metadata.language',
  readStatus: 'readStatus'
};

export function mapSortFieldToApi(field: string): string {
  return SORT_FIELD_TO_API[field] ?? field;
}
