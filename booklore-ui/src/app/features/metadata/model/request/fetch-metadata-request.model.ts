export interface FetchMetadataRequest {
  bookId: number,
  providers: string[],
  customProviderIds?: string[],
  title: string,
  author: string,
  isbn: string
}
