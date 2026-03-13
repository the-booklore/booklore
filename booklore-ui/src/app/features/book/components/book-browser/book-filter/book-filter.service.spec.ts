import {beforeEach, describe, expect, it} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {of, firstValueFrom} from 'rxjs';

import {BookFilterService} from './book-filter.service';
import {BookService} from '../../../service/book.service';
import {LibraryService} from '../../../service/library.service';
import {BookRuleEvaluatorService} from '../../../../magic-shelf/service/book-rule-evaluator.service';

describe('BookFilterService', () => {
  let service: BookFilterService;
  let bookState$: any;

  function setupBooks(books: any[]) {
    bookState$.bookState$ = of({ books });
  }

  beforeEach(() => {
    bookState$ = {};

    TestBed.configureTestingModule({
      providers: [
        BookFilterService,
        {
          provide: BookService,
          useValue: bookState$
        },
        {
          provide: LibraryService,
          useValue: {
            libraryState$: of([])
          }
        },
        {
          provide: BookRuleEvaluatorService,
          useValue: {}
        }
      ]
    });

    service = TestBed.inject(BookFilterService);
  });

  it('should sort category filters alphabetically', async () => {
    setupBooks([
      { metadata: { categories: ['Zulu'] } },
      { metadata: { categories: ['Alpha'] } }
    ]);

    const streams = service.createFilterStreams(
      of(null),
      of('library' as any),
      of(null),
      of('and')
    );

    const categoryFilters = await firstValueFrom(streams.category);

    expect(categoryFilters.map(f => f.value.name)).toEqual(['Alpha', 'Zulu']);
  });

  it('should sort category filters alphabetically ignoring case', async () => {
    setupBooks([
      { metadata: { categories: ['zulu'] } },
      { metadata: { categories: ['Alpha'] } },
      { metadata: { categories: ['bravo'] } }
    ]);

    const streams = service.createFilterStreams(
      of(null),
      of('library' as any),
      of(null),
      of('and')
    );

    const categoryFilters = await firstValueFrom(streams.category);

    expect(categoryFilters.map(f => f.value.name)).toEqual(['Alpha', 'bravo', 'zulu']);
  });
});
