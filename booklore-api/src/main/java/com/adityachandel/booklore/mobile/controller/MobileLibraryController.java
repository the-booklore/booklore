package com.adityachandel.booklore.mobile.controller;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mobile.dto.MobileLibrarySummary;
import com.adityachandel.booklore.mobile.mapper.MobileBookMapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@RestController
@RequestMapping("/api/mobile/v1/libraries")
@Tag(name = "Mobile Libraries", description = "Mobile-optimized endpoints for library operations")
public class MobileLibraryController {

    private final AuthenticationService authenticationService;
    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final MobileBookMapper mobileBookMapper;

    @Operation(summary = "Get user's accessible libraries",
            description = "Retrieve a list of libraries the current user has access to.")
    @ApiResponse(responseCode = "200", description = "Libraries retrieved successfully")
    @GetMapping
    public ResponseEntity<List<MobileLibrarySummary>> getLibraries() {
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

        List<MobileLibrarySummary> summaries = libraries.stream()
                .map(library -> {
                    long bookCount = bookRepository.countByLibraryId(library.getId());
                    return mobileBookMapper.toLibrarySummary(library, bookCount);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }
}
