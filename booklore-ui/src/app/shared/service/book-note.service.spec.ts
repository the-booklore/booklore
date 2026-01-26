import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {BookNote, BookNoteService, CreateBookNoteRequest} from './book-note.service';

describe('BookNoteService', () => {
  let service: BookNoteService;
  let httpClientMock: any;

  const mockNote: BookNote = {
    id: 1,
    userId: 2,
    bookId: 3,
    title: 'Note Title',
    content: 'Note content',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T01:00:00Z'
  };

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      delete: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        BookNoteService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    service = TestBed.inject(BookNoteService);
  });

  it('should get notes for a book', () => {
    httpClientMock.get.mockReturnValue(of([mockNote]));
    service.getNotesForBook(3).subscribe(notes => {
      expect(notes).toEqual([mockNote]);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringContaining('/book/3'));
    });
  });

  it('should create or update a note', () => {
    httpClientMock.post.mockReturnValue(of(mockNote));
    const req: CreateBookNoteRequest = {bookId: 3, title: 'Note Title', content: 'Note content'};
    service.createOrUpdateNote(req).subscribe(note => {
      expect(note).toEqual(mockNote);
      expect(httpClientMock.post).toHaveBeenCalledWith(expect.any(String), req);
    });
  });

  it('should delete a note', () => {
    httpClientMock.delete.mockReturnValue(of(void 0));
    service.deleteNote(1).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringContaining('/1'));
    });
  });

  it('should handle getNotesForBook error', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getNotesForBook(3).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
      }
    });
  });

  it('should handle createOrUpdateNote error', () => {
    httpClientMock.post.mockReturnValue(throwError(() => new Error('fail')));
    service.createOrUpdateNote({bookId: 3, title: 'fail', content: 'fail'}).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
      }
    });
  });

  it('should handle deleteNote error', () => {
    httpClientMock.delete.mockReturnValue(throwError(() => new Error('fail')));
    service.deleteNote(1).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
      }
    });
  });
});

describe('BookNoteService - API Contract Tests', () => {
  let service: BookNoteService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      delete: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        BookNoteService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    service = TestBed.inject(BookNoteService);
  });

  describe('BookNote interface contract', () => {
    it('should validate all required BookNote fields exist', () => {
      const requiredFields: (keyof BookNote)[] = [
        'id', 'userId', 'bookId', 'title', 'content', 'createdAt', 'updatedAt'
      ];
      const mockResponse: BookNote = {
        id: 1,
        userId: 2,
        bookId: 3,
        title: 't',
        content: 'c',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T01:00:00Z'
      };
      httpClientMock.get.mockReturnValue(of([mockResponse]));
      service.getNotesForBook(3).subscribe(notes => {
        requiredFields.forEach(field => {
          expect(notes[0]).toHaveProperty(field);
          expect(notes[0][field]).toBeDefined();
        });
      });
    });
  });

  describe('HTTP endpoint contract', () => {
    it('should call correct endpoint for getNotesForBook', () => {
      httpClientMock.get.mockReturnValue(of([]));
      service.getNotesForBook(5).subscribe();
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/book-notes\/book\/5$/));
    });

    it('should call correct endpoint for createOrUpdateNote', () => {
      httpClientMock.post.mockReturnValue(of({}));
      const req: CreateBookNoteRequest = {bookId: 2, title: 't', content: 'c'};
      service.createOrUpdateNote(req).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/book-notes$/),
        req
      );
    });

    it('should call correct endpoint for deleteNote', () => {
      httpClientMock.delete.mockReturnValue(of(void 0));
      service.deleteNote(8).subscribe();
      expect(httpClientMock.delete).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/book-notes\/8$/)
      );
    });
  });

  describe('Request payload contract', () => {
    it('should send correct structure for createOrUpdateNote', () => {
      httpClientMock.post.mockReturnValue(of({}));
      const req: CreateBookNoteRequest = {bookId: 2, title: 't', content: 'c'};
      service.createOrUpdateNote(req).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(expect.any(String), req);
    });
  });

  describe('Response type contract', () => {
    it('should expect BookNote[] from getNotesForBook', () => {
      const mockNotes: BookNote[] = [{
        id: 1,
        userId: 2,
        bookId: 3,
        title: 't',
        content: 'c',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T01:00:00Z'
      }];
      httpClientMock.get.mockReturnValue(of(mockNotes));
      service.getNotesForBook(3).subscribe(notes => {
        expect(Array.isArray(notes)).toBe(true);
        expect(notes[0]).toHaveProperty('id');
        expect(notes[0]).toHaveProperty('title');
      });
    });

    it('should expect BookNote from createOrUpdateNote', () => {
      const mockNote: BookNote = {
        id: 1,
        userId: 2,
        bookId: 3,
        title: 't',
        content: 'c',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T01:00:00Z'
      };
      httpClientMock.post.mockReturnValue(of(mockNote));
      service.createOrUpdateNote({bookId: 2, title: 't', content: 'c'}).subscribe(note => {
        expect(note).toHaveProperty('id');
        expect(note).toHaveProperty('title');
      });
    });

    it('should expect void from deleteNote', () => {
      httpClientMock.delete.mockReturnValue(of(void 0));
      service.deleteNote(1).subscribe(result => {
        expect(result).toBeUndefined();
      });
    });
  });
});

