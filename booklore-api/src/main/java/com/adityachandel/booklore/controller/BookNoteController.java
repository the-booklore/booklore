package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.BookNote;
import com.adityachandel.booklore.model.dto.CreateBookNoteRequest;
import com.adityachandel.booklore.service.book.BookNoteService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/book-notes")
@AllArgsConstructor
public class BookNoteController {

    private final BookNoteService bookNoteService;


    @GetMapping("/book/{bookId}")
    public List<BookNote> getNotesForBook(@PathVariable Long bookId) {
        return bookNoteService.getNotesForBook(bookId);
    }

    @PostMapping
    public BookNote createNote(@Valid @RequestBody CreateBookNoteRequest request) {
        return bookNoteService.createOrUpdateNote(request);
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable Long noteId) {
        bookNoteService.deleteNote(noteId);
        return ResponseEntity.noContent().build();
    }
}
