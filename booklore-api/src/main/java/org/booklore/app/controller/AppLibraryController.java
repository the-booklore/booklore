package org.booklore.app.controller;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.app.dto.AppLibrarySummary;
import org.booklore.app.mapper.AppBookMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/libraries")
public class AppLibraryController {

    private final AuthenticationService authenticationService;
    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final AppBookMapper mobileBookMapper;

    @GetMapping
    public ResponseEntity<List<AppLibrarySummary>> getLibraries() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        List<LibraryEntity> libraries;
        if (user.getPermissions().isAdmin()) {
            libraries = libraryRepository.findAll();
        } else {
            List<Long> libraryIds = user.getAssignedLibraries() != null
                    ? user.getAssignedLibraries().stream().map(Library::getId).collect(Collectors.toList())
                    : List.of();
            libraries = libraryRepository.findByIdIn(libraryIds);
        }

        List<AppLibrarySummary> summaries = libraries.stream()
                .map(library -> {
                    long bookCount = bookRepository.countByLibraryId(library.getId());
                    return mobileBookMapper.toLibrarySummary(library, bookCount);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }
}
