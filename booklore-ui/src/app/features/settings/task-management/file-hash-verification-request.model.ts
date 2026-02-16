export interface FileHashVerificationRequest {
  verificationType: FileHashVerificationType;
  libraryId?: number | null;
  bookIds?: number[];
  verificationOptions?: FileHashVerificationOptions;
}

export enum FileHashVerificationType {
  LIBRARY = 'LIBRARY',
  BOOKS = 'BOOKS'
}

export interface FileHashVerificationOptions {
  dryRun?: boolean;
  overwriteInitialHash?: boolean;
}
