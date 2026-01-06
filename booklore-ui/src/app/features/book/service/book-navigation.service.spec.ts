import {beforeEach, describe, expect, it} from 'vitest';
import {firstValueFrom} from 'rxjs';
import {BookNavigationService} from './book-navigation.service';

describe('BookNavigationService', () => {
  let service: BookNavigationService;
  const bookIds = [10, 20, 30, 40];

  beforeEach(() => {
    service = new BookNavigationService();
  });

  it('should set and get available book ids', () => {
    service.setAvailableBookIds(bookIds);
    expect(service.getAvailableBookIds()).toEqual(bookIds);
  });

  it('should set navigation context and emit state', async () => {
    service.setNavigationContext(bookIds, 30);

    const state = await firstValueFrom(service.getNavigationState());

    expect(state).not.toBeNull();
    expect(state!.bookIds).toEqual(bookIds);
    expect(state!.currentIndex).toBe(2);
  });

  it('should emit null if currentBookId not in bookIds', async () => {
    service.setNavigationContext(bookIds, 99);

    const state = await firstValueFrom(service.getNavigationState());

    expect(state).toBeNull();
  });

  it('should determine canNavigatePrevious correctly', () => {
    service.setNavigationContext(bookIds, 10);
    expect(service.canNavigatePrevious()).toBe(false);

    service.setNavigationContext(bookIds, 30);
    expect(service.canNavigatePrevious()).toBe(true);
  });

  it('should determine canNavigateNext correctly', () => {
    service.setNavigationContext(bookIds, 40);
    expect(service.canNavigateNext()).toBe(false);

    service.setNavigationContext(bookIds, 20);
    expect(service.canNavigateNext()).toBe(true);
  });

  it('should get previous book id', () => {
    service.setNavigationContext(bookIds, 30);
    expect(service.getPreviousBookId()).toBe(20);

    service.setNavigationContext(bookIds, 10);
    expect(service.getPreviousBookId()).toBeNull();
  });

  it('should get next book id', () => {
    service.setNavigationContext(bookIds, 20);
    expect(service.getNextBookId()).toBe(30);

    service.setNavigationContext(bookIds, 40);
    expect(service.getNextBookId()).toBeNull();
  });

  it('should update current book and emit new index', async () => {
    service.setNavigationContext(bookIds, 10);
    service.updateCurrentBook(30);

    const state = await firstValueFrom(service.getNavigationState());

    expect(state!.currentIndex).toBe(2);
    expect(state!.bookIds[state!.currentIndex]).toBe(30);
  });

  it('should not update current book if id not in list', async () => {
    service.setNavigationContext(bookIds, 10);
    service.updateCurrentBook(99);

    const state = await firstValueFrom(service.getNavigationState());

    expect(state!.currentIndex).toBe(0);
    expect(state!.bookIds[state!.currentIndex]).toBe(10);
  });

  it('should return current position', () => {
    service.setNavigationContext(bookIds, 30);
    expect(service.getCurrentPosition()).toEqual({current: 3, total: 4});
  });

  it('should return null for current position if no state', () => {
    expect(service.getCurrentPosition()).toBeNull();
  });

  it('should handle navigation methods gracefully if state is null', () => {
    expect(service.canNavigatePrevious()).toBe(false);
    expect(service.canNavigateNext()).toBe(false);
    expect(service.getPreviousBookId()).toBeNull();
    expect(service.getNextBookId()).toBeNull();

    service.updateCurrentBook(10);
    expect(service.getCurrentPosition()).toBeNull();
  });
});
