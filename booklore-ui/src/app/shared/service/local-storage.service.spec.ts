import {describe, it, expect, beforeEach, vi} from 'vitest';
import {LocalStorageService} from './local-storage.service';

describe('LocalStorageService', () => {
  let service: LocalStorageService;

  beforeEach(() => {
    service = new LocalStorageService();
    vi.spyOn(window.localStorage, 'getItem').mockClear();
    vi.spyOn(window.localStorage, 'setItem').mockClear();
    vi.spyOn(window.localStorage, 'removeItem').mockClear();
  });

  it('should get value from localStorage', () => {
    window.localStorage.setItem('test', JSON.stringify({foo: 'bar'}));
    const result = service.get<{foo: string}>('test');
    expect(result).toEqual({foo: 'bar'});
  });

  it('should return null if key does not exist', () => {
    window.localStorage.removeItem('notfound');
    const result = service.get('notfound');
    expect(result).toBeNull();
  });

  it('should handle JSON parse error and return null', () => {
    window.localStorage.setItem('bad', 'not-json');
    const result = service.get('bad');
    expect(result).toBeNull();
  });

  it('should set value in localStorage', () => {
    service.set('foo', {bar: 123});
    const stored = window.localStorage.getItem('foo');
    expect(stored).toBe(JSON.stringify({bar: 123}));
  });

  it('should handle set error gracefully', () => {
    vi.spyOn(window.localStorage, 'setItem').mockImplementation(() => { throw new Error('fail'); });
    expect(() => service.set('fail', {a: 1})).not.toThrow();
  });

  it('should remove value from localStorage', () => {
    window.localStorage.setItem('toremove', '1');
    service.remove('toremove');
    expect(window.localStorage.getItem('toremove')).toBeNull();
  });
});

