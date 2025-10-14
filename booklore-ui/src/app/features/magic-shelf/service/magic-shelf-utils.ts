import {RuleOperator} from '../component/magic-shelf-component';

export const MULTI_VALUE_OPERATORS: RuleOperator[] = [
  'includes_any',
  'includes_all',
  'excludes_all'
];

export const EMPTY_CHECK_OPERATORS: RuleOperator[] = [
  'is_empty',
  'is_not_empty'
];

export function parseValue(val: any, type: 'string' | 'number' | 'decimal' | 'date' | undefined): any {
  if (val == null) return null;
  if (type === 'number' || type === 'decimal') {
    const num = Number(val);
    return isNaN(num) ? null : num;
  }
  if (type === 'date') {
    const d = new Date(val);
    return isNaN(d.getTime()) ? null : d;
  }
  return val;
}

export function removeNulls(obj: any): any {
  if (Array.isArray(obj)) {
    return obj.map(removeNulls);
  } else if (typeof obj === 'object' && obj !== null) {
    return Object.entries(obj).reduce((acc, [key, value]) => {
      const cleanedValue = removeNulls(value);
      if (cleanedValue !== null && cleanedValue !== undefined) {
        acc[key] = cleanedValue;
      }
      return acc;
    }, {} as any);
  }
  return obj;
}

export function serializeDateRules(ruleOrGroup: any): any {
  if ('rules' in ruleOrGroup) {
    return {
      ...ruleOrGroup,
      rules: ruleOrGroup.rules.map(serializeDateRules)
    };
  }

  const isDateField = ruleOrGroup.field === 'publishedDate' || ruleOrGroup.field === 'dateFinished';
  const serialize = (val: any) => (val instanceof Date ? val.toISOString().split('T')[0] : val);

  return {
    ...ruleOrGroup,
    value: isDateField ? serialize(ruleOrGroup.value) : ruleOrGroup.value,
    valueStart: isDateField ? serialize(ruleOrGroup.valueStart) : ruleOrGroup.valueStart,
    valueEnd: isDateField ? serialize(ruleOrGroup.valueEnd) : ruleOrGroup.valueEnd
  };
}
