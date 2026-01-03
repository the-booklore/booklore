export type CustomFieldType = 'STRING' | 'NUMBER' | 'DATE';

export interface LibraryCustomField {
  id: number;
  libraryId: number;
  name: string;
  fieldType: CustomFieldType;
  defaultValue?: string | null;
}
