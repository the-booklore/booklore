package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.mapper.BookNoteV2Mapper;
import com.adityachandel.booklore.model.dto.BookNoteV2;
import com.adityachandel.booklore.model.dto.CreateBookNoteV2Request;
import com.adityachandel.booklore.model.dto.UpdateBookNoteV2Request;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.BookNoteV2Entity;
import com.adityachandel.booklore.repository.BookNoteV2Repository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookNoteV2Service {

    private final BookNoteV2Repository bookNoteV2Repository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final BookNoteV2Mapper mapper;

    @Transactional(readOnly = true)
    public List<BookNoteV2> getNotesForBook(Long bookId) {
        Long userId = getCurrentUserId();
        return bookNoteV2Repository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookNoteV2 getNoteById(Long noteId) {
        return mapper.toDto(findNoteByIdAndUser(noteId));
    }

    @Transactional
    public BookNoteV2 createNote(CreateBookNoteV2Request request) {
        Long userId = getCurrentUserId();
        validateNoDuplicateNote(request.getCfi(), request.getBookId(), userId);

        String color = request.getColor() != null ? request.getColor() : "#FFC107";

        BookNoteV2Entity note = BookNoteV2Entity.builder()
                .cfi(request.getCfi())
                .selectedText(request.getSelectedText())
                .noteContent(request.getNoteContent())
                .color(color)
                .chapterTitle(request.getChapterTitle())
                .book(findBook(request.getBookId()))
                .user(findUser(userId))
                .build();

        log.info("Creating note for book {} by user {}", request.getBookId(), userId);
        return mapper.toDto(bookNoteV2Repository.save(note));
    }

    @Transactional
    public BookNoteV2 updateNote(Long noteId, UpdateBookNoteV2Request request) {
        BookNoteV2Entity note = findNoteByIdAndUser(noteId);

        applyUpdates(note, request);

        log.info("Updating note {}", noteId);
        return mapper.toDto(bookNoteV2Repository.save(note));
    }

    @Transactional
    public void deleteNote(Long noteId) {
        BookNoteV2Entity note = findNoteByIdAndUser(noteId);
        log.info("Deleting note {}", noteId);
        bookNoteV2Repository.delete(note);
    }

    private Long getCurrentUserId() {
        return authenticationService.getAuthenticatedUser().getId();
    }

    private BookNoteV2Entity findNoteByIdAndUser(Long noteId) {
        Long userId = getCurrentUserId();
        return bookNoteV2Repository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Note not found: " + noteId));
    }

    private BookEntity findBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + bookId));
    }

    private BookLoreUserEntity findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private void validateNoDuplicateNote(String cfi, Long bookId, Long userId) {
        boolean exists = bookNoteV2Repository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId);
        if (exists) {
            throw new APIException("Note already exists at this location", HttpStatus.CONFLICT);
        }
    }

    private void applyUpdates(BookNoteV2Entity note, UpdateBookNoteV2Request request) {
        Optional.ofNullable(request.getNoteContent()).ifPresent(note::setNoteContent);
        Optional.ofNullable(request.getColor()).ifPresent(note::setColor);
        Optional.ofNullable(request.getChapterTitle()).ifPresent(note::setChapterTitle);
    }
}
