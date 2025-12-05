import { TestBed } from '@angular/core/testing';
import { LoadingService } from './loading.service';
import { first, take, toArray } from 'rxjs';

describe('LoadingService', () => {
  let service: LoadingService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LoadingService]
    });
    service = TestBed.inject(LoadingService);
  });

  describe('initial state', () => {
    it('should be created', () => {
      expect(service).toBeTruthy();
    });

    it('should have loading$ observable', () => {
      expect(service.loading$).toBeTruthy();
    });

    it('should initially emit false', (done) => {
      service.loading$.pipe(first()).subscribe(loading => {
        expect(loading).toBe(false);
        done();
      });
    });
  });

  describe('show()', () => {
    it('should emit true when show() is called', (done) => {
      service.show();
      service.loading$.pipe(first()).subscribe(loading => {
        expect(loading).toBe(true);
        done();
      });
    });

    it('should emit true multiple times when show() is called multiple times', (done) => {
      const values: boolean[] = [];

      service.loading$.pipe(take(3), toArray()).subscribe(vals => {
        values.push(...vals);
        expect(values).toEqual([false, true, true]);
        done();
      });

      service.show();
      service.show();
    });
  });

  describe('hide()', () => {
    it('should emit false when hide() is called', (done) => {
      service.show();
      service.hide();
      service.loading$.pipe(first()).subscribe(loading => {
        expect(loading).toBe(false);
        done();
      });
    });

    it('should handle show then hide sequence', (done) => {
      const values: boolean[] = [];

      service.loading$.pipe(take(3), toArray()).subscribe(vals => {
        values.push(...vals);
        expect(values).toEqual([false, true, false]);
        done();
      });

      service.show();
      service.hide();
    });
  });

  describe('multiple subscribers', () => {
    it('should emit to all subscribers', (done) => {
      let subscriber1Value: boolean | null = null;
      let subscriber2Value: boolean | null = null;

      service.loading$.subscribe(val => subscriber1Value = val);
      service.loading$.subscribe(val => subscriber2Value = val);

      service.show();

      setTimeout(() => {
        expect(subscriber1Value).toBe(true);
        expect(subscriber2Value).toBe(true);
        done();
      }, 0);
    });

    it('should give new subscribers the current value', (done) => {
      service.show();

      // Subscribe after show() is called
      service.loading$.pipe(first()).subscribe(loading => {
        expect(loading).toBe(true);
        done();
      });
    });
  });
});
