import {Component, inject, OnInit} from '@angular/core';
import {AbstractControl, FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {Button} from 'primeng/button';
import {NgTemplateOutlet} from '@angular/common';
import {InputText} from 'primeng/inputtext';
import {Select} from 'primeng/select';
import {DatePicker} from 'primeng/datepicker';
import {InputNumber} from 'primeng/inputnumber';
import {ReadStatus} from '../book/model/book.model';
import {LibraryService} from '../book/service/library.service';
import {Library} from '../book/model/library.model';
import {MagicShelfService} from '../magic-shelf.service';
import {MessageService} from 'primeng/api';
import {DynamicDialogConfig} from 'primeng/dynamicdialog';
import {MultiSelect} from 'primeng/multiselect';
import {AutoComplete} from 'primeng/autocomplete';
import {EMPTY_CHECK_OPERATORS, MULTI_VALUE_OPERATORS, parseValue, removeNulls, serializeDateRules} from '../magic-shelf-utils';
import {IconPickerService} from '../utilities/services/icon-picker.service';

export type RuleOperator =
  | 'equals'
  | 'not_equals'
  | 'contains'
  | 'does_not_contain'
  | 'starts_with'
  | 'ends_with'
  | 'greater_than'
  | 'greater_than_equal_to'
  | 'less_than'
  | 'less_than_equal_to'
  | 'in_between'
  | 'is_empty'
  | 'is_not_empty'
  | 'includes_any'
  | 'excludes_all'
  | 'includes_all'

export type RuleField =
  | 'library'
  | 'title'
  | 'subtitle'
  | 'authors'
  | 'categories'
  | 'publisher'
  | 'publishedDate'
  | 'seriesName'
  | 'seriesNumber'
  | 'seriesTotal'
  | 'pageCount'
  | 'language'
  | 'amazonRating'
  | 'amazonReviewCount'
  | 'goodreadsRating'
  | 'goodreadsReviewCount'
  | 'hardcoverRating'
  | 'hardcoverReviewCount'
  | 'personalRating'
  | 'fileType'
  | 'fileSize'
  | 'readStatus'
  | 'dateFinished'
  | 'metadataScore'
  | 'moods'
  | 'tags';


interface FullFieldConfig {
  label: string;
  type?: FieldType;
  max?: number;
}

type FieldType = 'number' | 'decimal' | 'date' | undefined;

export interface Rule {
  field: RuleField;
  operator: RuleOperator;
  value: any;
  valueStart?: any;
  valueEnd?: any;
}

export interface FieldConfig {
  type: FieldType;
  max?: number;
}

export interface GroupRule {
  name: string;
  type: 'group';
  join: 'and' | 'or';
  rules: (Rule | GroupRule)[];
}

export type RuleFormGroup = FormGroup<{
  field: FormControl<"" | RuleField | null>;
  operator: FormControl<"" | RuleOperator | null>;
  value: FormControl<string | null>;
  valueStart: FormControl<string | null>;
  valueEnd: FormControl<string | null>;
}>;

export type GroupFormGroup = FormGroup<{
  type: FormControl<'group'>;
  join: FormControl<'and' | 'or'>;
  rules: FormArray<GroupFormGroup | RuleFormGroup>;
}>;

const FIELD_CONFIGS: Record<RuleField, FullFieldConfig> = {
  library: {label: 'Library'},
  readStatus: {label: 'Read Status'},
  dateFinished: {label: 'Date Finished', type: 'date'},
  metadataScore: {label: 'Metadata Score', type: 'decimal', max: 100},
  title: {label: 'Title'},
  authors: {label: 'Authors'},
  categories: {label: 'Categories'},
  moods: {label: 'Moods'},
  tags: {label: 'Tags'},
  publisher: {label: 'Publisher'},
  publishedDate: {label: 'Published Date', type: 'date'},
  personalRating: {label: 'Personal Rating', type: 'decimal', max: 10},
  pageCount: {label: 'Page Count', type: 'number'},
  language: {label: 'Language'},
  seriesName: {label: 'Series Name'},
  seriesNumber: {label: 'Series Number', type: 'number'},
  seriesTotal: {label: 'Books in Series', type: 'number'},
  fileSize: {label: 'File Size (Kb)', type: 'number'},
  fileType: {label: 'File Type'},
  subtitle: {label: 'Subtitle'},
  amazonRating: {label: 'Amazon Rating', type: 'decimal', max: 5},
  amazonReviewCount: {label: 'Amazon Review Count', type: 'number'},
  goodreadsRating: {label: 'Goodreads Rating', type: 'decimal', max: 5},
  goodreadsReviewCount: {label: 'Goodreads Review Count', type: 'number'},
  hardcoverRating: {label: 'Hardcover Rating', type: 'decimal', max: 5},
  hardcoverReviewCount: {label: 'Hardcover Review Count', type: 'number'}
};

@Component({
  selector: 'app-magic-shelf',
  templateUrl: './magic-shelf-component.html',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    InputText,
    Select,
    Button,
    DatePicker,
    InputNumber,
    MultiSelect,
    AutoComplete
  ]
})
export class MagicShelfComponent implements OnInit {

  numericFieldConfigMap = new Map<RuleField, FieldConfig>(
    Object.entries(FIELD_CONFIGS)
      .filter(([_, config]) => config.type)
      .map(([key, config]) => [key as RuleField, {type: config.type!, max: config.max}])
  );

  conditionOptions: { label: string; value: 'and' | 'or' }[] = [
    {label: 'AND', value: 'and'},
    {label: 'OR', value: 'or'},
  ];

  fieldOptions = Object.entries(FIELD_CONFIGS).map(([key, config]) => ({
    label: config.label,
    value: key as RuleField
  }));

  fileType: { label: string; value: string }[] = [
    {label: 'PDF', value: 'pdf'},
    {label: 'EPUB', value: 'epub'},
    {label: 'CBR', value: 'cbr'},
    {label: 'CBZ', value: 'cbz'},
    {label: 'CB7', value: 'cb7'}
  ];

  readStatusOptions = Object.entries(ReadStatus).map(([key, value]) => ({
    label: key.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase()),
    value
  }));

  libraries: Library[] = [];
  libraryOptions: { label: string; value: number }[] = [];

  form = new FormGroup({
    name: new FormControl<string | null>(null),
    icon: new FormControl<string | null>(null),
    group: this.createGroup()
  });

  shelfId: number | null = null;

  libraryService = inject(LibraryService);
  magicShelfService = inject(MagicShelfService);
  messageService = inject(MessageService);
  config = inject(DynamicDialogConfig);
  private iconPicker = inject(IconPickerService);

  trackByFn(ruleCtrl: AbstractControl, index: number): any {
    return ruleCtrl;
  }

  ngOnInit(): void {
    const id = this.config?.data?.id;

    if (id) {
      this.shelfId = id;
      this.magicShelfService.getShelf(id).subscribe((data) => {
        this.form = new FormGroup({
          name: new FormControl<string | null>(data?.name ?? null, {nonNullable: true, validators: [Validators.required]}),
          icon: new FormControl<string | null>(data?.icon ?? null, {nonNullable: true, validators: [Validators.required]}),
          group: data?.filterJson ? this.buildGroupFromData(JSON.parse(data.filterJson)) : this.createGroup()
        });
      });
    } else {
      this.form = new FormGroup({
        name: new FormControl<string | null>(null, {nonNullable: true, validators: [Validators.required]}),
        icon: new FormControl<string | null>(null, {nonNullable: true, validators: [Validators.required]}),
        group: this.createGroup()
      });
    }

    this.libraries = this.libraryService.getLibrariesFromState();
    this.libraryOptions = this.libraries.map(lib => ({
      label: lib.name,
      value: lib.id!
    }));
  }

  buildGroupFromData(data: GroupRule): GroupFormGroup {
    const rulesArray = new FormArray<FormGroup>([]);

    data.rules.forEach(rule => {
      if ('type' in rule && rule.type === 'group') {
        rulesArray.push(this.buildGroupFromData(rule));
      } else {
        rulesArray.push(this.buildRuleFromData(rule as Rule));
      }
    });

    return new FormGroup({
      type: new FormControl<'group'>('group'),
      join: new FormControl(data.join),
      rules: rulesArray as FormArray<GroupFormGroup | RuleFormGroup>
    }) as GroupFormGroup;
  }

  buildRuleFromData(data: Rule): RuleFormGroup {
    const config = FIELD_CONFIGS[data.field];
    const type = config?.type;

    return new FormGroup({
      field: new FormControl<RuleField>(data.field),
      operator: new FormControl<RuleOperator>(data.operator),
      value: new FormControl(parseValue(data.value, type)),
      valueStart: new FormControl(parseValue(data.valueStart, type)),
      valueEnd: new FormControl(parseValue(data.valueEnd, type)),
    }) as RuleFormGroup;
  }

  get group(): GroupFormGroup {
    return this.form.get('group') as GroupFormGroup;
  }

  getOperatorOptionsForField(field: RuleField | null | undefined) {
    const baseOperators = [
      {label: 'Equals', value: 'equals'},
      {label: '≠ Not Equal', value: 'not_equals'},
      {label: 'Empty', value: 'is_empty'},
      {label: 'Not Empty', value: 'is_not_empty'},
    ];

    const multiValueOperators = [
      {label: 'Includes Any', value: 'includes_any'},
      {label: 'Excludes All', value: 'excludes_all'},
      {label: 'Includes All', value: 'includes_all'},
    ];

    const textOperators = [
      {label: 'Contains', value: 'contains'},
      {label: 'Doesn\'t Contain', value: 'does_not_contain'},
      {label: 'Starts With', value: 'starts_with'},
      {label: 'Ends With', value: 'ends_with'},
    ];

    const comparisonOperators = [
      {label: '> Greater Than', value: 'greater_than'},
      {label: '≥ Greater or Equal', value: 'greater_than_equal_to'},
      {label: '< Less Than', value: 'less_than'},
      {label: '≤ Less or Equal', value: 'less_than_equal_to'},
      {label: 'Between', value: 'in_between'},
    ];

    if (!field) return [...baseOperators, ...multiValueOperators];

    const config = FIELD_CONFIGS[field];
    const isMultiValueField = ['library', 'authors', 'categories', 'moods', 'tags', 'readStatus', 'fileType', 'language', 'title', 'subtitle', 'publisher', 'seriesName'].includes(field);
    const operators = [...baseOperators];

    if (isMultiValueField) {
      operators.push(...multiValueOperators);
    }

    const isTextEligible = !['library', 'readStatus', 'fileType'].includes(field);

    if (config.type === 'number' || config.type === 'decimal' || config.type === 'date') {
      operators.push(...comparisonOperators);
    } else if (isTextEligible) {
      operators.push(...textOperators);
    }

    return operators;
  }

  createRule(): RuleFormGroup {
    return new FormGroup({
      field: new FormControl<RuleField | ''>(''),
      operator: new FormControl<RuleOperator | ''>(''),
      value: new FormControl<string | null>(null),
      valueStart: new FormControl<string | null>(null),
      valueEnd: new FormControl<string | null>(null),
    }) as RuleFormGroup;
  }

  createGroup(): GroupFormGroup {
    return new FormGroup({
      type: new FormControl<'group'>('group' as 'group'),
      join: new FormControl<'and' | 'or'>('and' as 'and' | 'or'),
      rules: new FormArray([] as (GroupFormGroup | RuleFormGroup)[]),
    }) as GroupFormGroup;
  }

  addGroup(group: GroupFormGroup) {
    const rules = group.get('rules') as FormArray;
    rules.push(this.createGroup());
  }

  addRule(group: GroupFormGroup) {
    const rules = group.get('rules') as FormArray;
    rules.push(this.createRule());
  }

  deleteGroup(group: GroupFormGroup) {
    const parent = group.parent;
    if (parent && parent instanceof FormArray) {
      const index = parent.controls.indexOf(group);
      if (index > -1) {
        parent.removeAt(index);
      }
    }
  }

  removeRule(group: GroupFormGroup, index: number) {
    const rules = group.get('rules') as FormArray;
    rules.removeAt(index);
  }

  isGroup(control: AbstractControl): boolean {
    return control instanceof FormGroup && control.get('rules') instanceof FormArray;
  }

  onOperatorChange(ruleCtrl: FormGroup) {
    const operator = ruleCtrl.get('operator')?.value as RuleOperator;

    const valueCtrl = ruleCtrl.get('value');
    const valueStartCtrl = ruleCtrl.get('valueStart');
    const valueEndCtrl = ruleCtrl.get('valueEnd');

    if (MULTI_VALUE_OPERATORS.includes(operator)) {
      valueCtrl?.setValue([]);
      valueStartCtrl?.setValue(null);
      valueEndCtrl?.setValue(null);
    } else if (EMPTY_CHECK_OPERATORS.includes(operator)) {
      valueCtrl?.setValue(null);
      valueStartCtrl?.setValue(null);
      valueEndCtrl?.setValue(null);
    } else {
      valueCtrl?.setValue('');
      valueStartCtrl?.setValue(null);
      valueEndCtrl?.setValue(null);
    }
  }

  onFieldChange(ruleCtrl: RuleFormGroup) {
    ruleCtrl.get('operator')?.setValue('');
    ruleCtrl.get('value')?.setValue(null);
    ruleCtrl.get('valueStart')?.setValue(null);
    ruleCtrl.get('valueEnd')?.setValue(null);
  }

  openIconPicker() {
    this.iconPicker.open().subscribe(icon => {
      if (icon) {
        this.form.get('icon')?.setValue(icon);
      }
    });
  }

  private hasAtLeastOneValidRule(group: GroupFormGroup): boolean {
    const rulesArray = group.get('rules') as FormArray;

    return rulesArray.controls.some(ctrl => {
      const type = (ctrl.get('type') as FormControl<'group'> | null)?.value;

      if (type === 'group') {
        return this.hasAtLeastOneValidRule(ctrl as GroupFormGroup);
      } else {
        const field = ctrl.get('field')?.value;
        const operator = ctrl.get('operator')?.value;
        return !!field && !!operator;
      }
    });
  }

  onAutoCompleteBlur(formControl: any, event: any) {
    const inputValue = event.target.value?.trim();
    if (inputValue) {
      const currentValue = formControl.value || [];
      const values = Array.isArray(currentValue) ? currentValue :
        typeof currentValue === 'string' && currentValue ? currentValue.split(',').map((v: string) => v.trim()) : [];

      if (!values.includes(inputValue)) {
        values.push(inputValue);
        formControl.setValue(values);
      }
      event.target.value = '';
    }
  }

  submit() {
    if (!this.hasAtLeastOneValidRule(this.group)) {
      this.messageService.add({severity: 'warn', summary: 'Validation Error', detail: 'You must add at least one valid rule before saving.'});
      return;
    }

    const value = this.form.value as { name: string | null; icon: string | null; group: GroupRule };
    const cleanedGroup = removeNulls(serializeDateRules(value.group));

    this.magicShelfService.saveShelf({
      id: this.shelfId ?? undefined,
      name: value.name,
      icon: value.icon,
      group: cleanedGroup
    }).subscribe({
      next: (savedShelf) => {
        this.messageService.add({severity: 'success', summary: 'Success', detail: 'Magic shelf saved successfully.'});
        if (savedShelf?.id) {
          this.shelfId = savedShelf.id;
        }
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: err?.error?.message || 'Failed to save magic shelf.'
        });
      }
    });
  }
}
