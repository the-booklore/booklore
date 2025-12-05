import { TestBed } from '@angular/core/testing';
import { LocalStorageService } from './local-storage-service';

describe('LocalStorageService', () => {
  let service: LocalStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LocalStorageService]
    });
    service = TestBed.inject(LocalStorageService);
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  describe('set', () => {
    it('should store a string value', () => {
      service.set('testKey', 'testValue');
      expect(localStorage.getItem('testKey')).toBe('"testValue"');
    });

    it('should store a number value', () => {
      service.set('numberKey', 42);
      expect(localStorage.getItem('numberKey')).toBe('42');
    });

    it('should store an object value', () => {
      const obj = { name: 'Test', value: 123 };
      service.set('objectKey', obj);
      expect(localStorage.getItem('objectKey')).toBe('{"name":"Test","value":123}');
    });

    it('should store an array value', () => {
      const arr = [1, 2, 3, 'four'];
      service.set('arrayKey', arr);
      expect(localStorage.getItem('arrayKey')).toBe('[1,2,3,"four"]');
    });

    it('should store boolean values', () => {
      service.set('trueKey', true);
      service.set('falseKey', false);
      expect(localStorage.getItem('trueKey')).toBe('true');
      expect(localStorage.getItem('falseKey')).toBe('false');
    });

    it('should store null value', () => {
      service.set('nullKey', null);
      expect(localStorage.getItem('nullKey')).toBe('null');
    });

    it('should overwrite existing values', () => {
      service.set('key', 'first');
      service.set('key', 'second');
      expect(localStorage.getItem('key')).toBe('"second"');
    });
  });

  describe('get', () => {
    it('should retrieve a stored string value', () => {
      localStorage.setItem('testKey', '"testValue"');
      const result = service.get<string>('testKey');
      expect(result).toBe('testValue');
    });

    it('should retrieve a stored number value', () => {
      localStorage.setItem('numberKey', '42');
      const result = service.get<number>('numberKey');
      expect(result).toBe(42);
    });

    it('should retrieve a stored object value', () => {
      localStorage.setItem('objectKey', '{"name":"Test","value":123}');
      const result = service.get<{ name: string; value: number }>('objectKey');
      expect(result).toEqual({ name: 'Test', value: 123 });
    });

    it('should retrieve a stored array value', () => {
      localStorage.setItem('arrayKey', '[1,2,3,"four"]');
      const result = service.get<(number | string)[]>('arrayKey');
      expect(result).toEqual([1, 2, 3, 'four']);
    });

    it('should return null for non-existent key', () => {
      const result = service.get<string>('nonExistentKey');
      expect(result).toBeNull();
    });

    it('should return null for invalid JSON', () => {
      localStorage.setItem('invalidKey', 'not valid json {');
      const result = service.get<object>('invalidKey');
      expect(result).toBeNull();
    });

    it('should retrieve boolean values', () => {
      localStorage.setItem('trueKey', 'true');
      localStorage.setItem('falseKey', 'false');
      expect(service.get<boolean>('trueKey')).toBe(true);
      expect(service.get<boolean>('falseKey')).toBe(false);
    });
  });

  describe('remove', () => {
    it('should remove a stored value', () => {
      localStorage.setItem('keyToRemove', '"value"');
      expect(localStorage.getItem('keyToRemove')).not.toBeNull();

      service.remove('keyToRemove');
      expect(localStorage.getItem('keyToRemove')).toBeNull();
    });

    it('should not throw when removing non-existent key', () => {
      expect(() => service.remove('nonExistentKey')).not.toThrow();
    });
  });

  describe('integration', () => {
    it('should correctly roundtrip complex objects', () => {
      const complexObj = {
        id: 1,
        name: 'Test Book',
        metadata: {
          authors: ['Author 1', 'Author 2'],
          published: '2023-01-01',
          tags: ['fiction', 'adventure']
        },
        isAvailable: true,
        rating: 4.5
      };

      service.set('complexKey', complexObj);
      const retrieved = service.get<typeof complexObj>('complexKey');

      expect(retrieved).toEqual(complexObj);
    });

    it('should handle special characters in values', () => {
      const specialString = 'Test with "quotes" and \'apostrophes\' and <tags>';
      service.set('specialKey', specialString);
      expect(service.get<string>('specialKey')).toBe(specialString);
    });

    it('should handle unicode characters', () => {
      const unicodeString = '日本語テスト 中文测试 한국어테스트 🎉📚';
      service.set('unicodeKey', unicodeString);
      expect(service.get<string>('unicodeKey')).toBe(unicodeString);
    });
  });
});
