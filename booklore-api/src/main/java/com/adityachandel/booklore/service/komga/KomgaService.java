package com.adityachandel.booklore.service.komga;

import com.adityachandel.booklore.mapper.komga.KomgaMapper;
import com.adityachandel.booklore.model.dto.komga.*;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KomgaService {

    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final KomgaMapper komgaMapper;

    public List<KomgaLibraryDto> getAllLibraries() {
        return libraryRepository.findAll().stream()
                .map(komgaMapper::toKomgaLibraryDto)
                .collect(Collectors.toList());
    }

    public KomgaLibraryDto getLibraryById(Long libraryId) {
        LibraryEntity library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new RuntimeException("Library not found"));
        return komgaMapper.toKomgaLibraryDto(library);
    }

    public KomgaPageableDto<KomgaSeriesDto> getAllSeries(Long libraryId, int page, int size) {
        List<BookEntity> books;
        
        if (libraryId != null) {
            books = bookRepository.findAllWithMetadataByLibraryId(libraryId);
        } else {
            books = bookRepository.findAllWithMetadata();
        }
        
        // Group books by series
        Map<String, List<BookEntity>> seriesMap = groupBooksBySeries(books);
        
        // Convert to DTOs
        List<KomgaSeriesDto> allSeries = seriesMap.entrySet().stream()
                .map(entry -> {
                    String seriesName = entry.getKey();
                    List<BookEntity> seriesBooks = entry.getValue();
                    Long libId = seriesBooks.get(0).getLibrary().getId();
                    return komgaMapper.toKomgaSeriesDto(seriesName, libId, seriesBooks);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(KomgaSeriesDto::getName))
                .collect(Collectors.toList());
        
        // Paginate
        int totalElements = allSeries.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        
        List<KomgaSeriesDto> content = allSeries.subList(fromIndex, toIndex);
        
        return KomgaPageableDto.<KomgaSeriesDto>builder()
                .content(content)
                .number(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .empty(content.isEmpty())
                .build();
    }

    public KomgaSeriesDto getSeriesById(String seriesId) {
        // Parse seriesId to extract library and series name
        String[] parts = seriesId.split("-", 2);
        if (parts.length < 2) {
            throw new RuntimeException("Invalid series ID");
        }
        
        Long libraryId = Long.parseLong(parts[0]);
        String seriesSlug = parts[1];
        
        // Get all books in the library
        List<BookEntity> books = bookRepository.findAllWithMetadataByLibraryId(libraryId);
        
        // Find books matching the series
        List<BookEntity> seriesBooks = books.stream()
                .filter(book -> {
                    BookMetadataEntity metadata = book.getMetadata();
                    String bookSeriesName = metadata != null && metadata.getSeriesName() != null 
                                          ? metadata.getSeriesName() 
                                          : "Unknown Series";
                    String bookSeriesSlug = bookSeriesName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
                    return bookSeriesSlug.equals(seriesSlug);
                })
                .collect(Collectors.toList());
        
        if (seriesBooks.isEmpty()) {
            throw new RuntimeException("Series not found");
        }
        
        String seriesName = seriesBooks.get(0).getMetadata() != null && seriesBooks.get(0).getMetadata().getSeriesName() != null
                          ? seriesBooks.get(0).getMetadata().getSeriesName()
                          : "Unknown Series";
        
        return komgaMapper.toKomgaSeriesDto(seriesName, libraryId, seriesBooks);
    }

    public KomgaPageableDto<KomgaBookDto> getBooksBySeries(String seriesId, int page, int size, boolean unpaged) {
        KomgaSeriesDto series = getSeriesById(seriesId);
        
        // Get all books for the series
        String[] parts = seriesId.split("-", 2);
        Long libraryId = Long.parseLong(parts[0]);
        String seriesSlug = parts[1];
        
        List<BookEntity> allBooks = bookRepository.findAllWithMetadataByLibraryId(libraryId);
        List<BookEntity> seriesBooks = allBooks.stream()
                .filter(book -> {
                    BookMetadataEntity metadata = book.getMetadata();
                    String bookSeriesName = metadata != null && metadata.getSeriesName() != null 
                                          ? metadata.getSeriesName() 
                                          : "Unknown Series";
                    String bookSeriesSlug = bookSeriesName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
                    return bookSeriesSlug.equals(seriesSlug);
                })
                .sorted(Comparator.comparing(book -> {
                    BookMetadataEntity metadata = book.getMetadata();
                    return metadata != null && metadata.getSeriesNumber() != null 
                         ? metadata.getSeriesNumber() 
                         : 0f;
                }))
                .collect(Collectors.toList());
        
        // Handle unpaged mode
        int totalElements = seriesBooks.size();
        List<KomgaBookDto> content;
        int actualPage;
        int actualSize;
        int totalPages;
        
        if (unpaged) {
            // Return all books without pagination
            content = seriesBooks.stream()
                    .map(komgaMapper::toKomgaBookDto)
                    .collect(Collectors.toList());
            actualPage = 0;
            actualSize = totalElements;
            totalPages = totalElements > 0 ? 1 : 0;
        } else {
            // Paginate
            totalPages = (int) Math.ceil((double) totalElements / size);
            int fromIndex = Math.min(page * size, totalElements);
            int toIndex = Math.min(fromIndex + size, totalElements);
            
            content = seriesBooks.subList(fromIndex, toIndex).stream()
                    .map(komgaMapper::toKomgaBookDto)
                    .collect(Collectors.toList());
            actualPage = page;
            actualSize = size;
        }
        
        return KomgaPageableDto.<KomgaBookDto>builder()
                .content(content)
                .number(actualPage)
                .size(actualSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(actualPage == 0)
                .last(totalElements == 0 || actualPage >= totalPages - 1)
                .empty(content.isEmpty())
                .build();
    }

    public KomgaPageableDto<KomgaBookDto> getAllBooks(Long libraryId, int page, int size) {
        List<BookEntity> books;
        
        if (libraryId != null) {
            books = bookRepository.findAllWithMetadataByLibraryId(libraryId);
        } else {
            books = bookRepository.findAllWithMetadata();
        }
        
        // Manual pagination
        int totalElements = books.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        
        List<KomgaBookDto> content = books.subList(fromIndex, toIndex).stream()
                .map(komgaMapper::toKomgaBookDto)
                .collect(Collectors.toList());
        
        return KomgaPageableDto.<KomgaBookDto>builder()
                .content(content)
                .number(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .empty(content.isEmpty())
                .build();
    }

    public KomgaBookDto getBookById(Long bookId) {
        BookEntity book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found"));
        return komgaMapper.toKomgaBookDto(book);
    }

    public List<KomgaPageDto> getBookPages(Long bookId) {
        BookEntity book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found"));
        
        BookMetadataEntity metadata = book.getMetadata();
        Integer pageCount = metadata != null && metadata.getPageCount() != null ? metadata.getPageCount() : 0;
        
        List<KomgaPageDto> pages = new ArrayList<>();
        if (pageCount > 0) {
            for (int i = 1; i <= pageCount; i++) {
                pages.add(KomgaPageDto.builder()
                        .number(i)
                        .fileName("page-" + i)
                        .mediaType("image/jpeg")
                        .build());
            }
        }
        
        return pages;
    }

    private Map<String, List<BookEntity>> groupBooksBySeries(List<BookEntity> books) {
        Map<String, List<BookEntity>> seriesMap = new HashMap<>();
        
        for (BookEntity book : books) {
            BookMetadataEntity metadata = book.getMetadata();
            String seriesName = metadata != null && metadata.getSeriesName() != null 
                              ? metadata.getSeriesName() 
                              : "Unknown Series";
            
            seriesMap.computeIfAbsent(seriesName, k -> new ArrayList<>()).add(book);
        }
        
        return seriesMap;
    }
}
