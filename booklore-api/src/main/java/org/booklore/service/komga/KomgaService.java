package org.booklore.service.komga;

import org.booklore.context.KomgaCleanContext;
import org.booklore.mapper.komga.KomgaMapper;
import org.booklore.model.dto.MagicShelf;
import org.booklore.model.dto.Shelf;
import org.booklore.model.dto.komga.*;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.MagicShelfService;
import org.booklore.service.ShelfService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.reader.CbxReaderService;
import org.booklore.service.reader.PdfReaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KomgaService {

    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9]+");
    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final KomgaMapper komgaMapper;
    private final MagicShelfService magicShelfService;
    private final ShelfService shelfService;
    private final CbxReaderService cbxReaderService;
    private final PdfReaderService pdfReaderService;
    private final AppSettingService appSettingService;

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

    public KomgaPageableDto<Object> getReadlists(List<Long> libraryIds, String search, int page, int size, boolean unpaged) {
        log.debug("Getting all readlists (not implemented) for libraryIds: {}, page: {}, size: {}", libraryIds, page, size);
        
        return KomgaPageableDto.<Object>builder()
                .content(List.of())
                .number(0)
                .size(0)
                .pageable(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Pageable())
                .sort(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Sort())
                .numberOfElements(0)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .build();
    }

    public KomgaPageableDto<Object> getOnDeckBooks(List<Long> libraryIds, int page, int size, boolean unpaged) {
        log.debug("Getting on deck books (not implemented) for libraryIds: {}, page: {}, size: {}", libraryIds, page, size);
        
        return KomgaPageableDto.<Object>builder()
                .content(List.of())
                .number(0)
                .size(0)
                .pageable(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Pageable())
                .sort(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Sort())
                .numberOfElements(0)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .build();
    }
    
    public KomgaPageableDto<KomgaSeriesDto> getNewSeries(List<Long> libraryIds, int page, int size, boolean unpaged) {
        log.debug("Getting new series (not implemented) for libraryIds: {}, page: {}, size: {}", libraryIds, page, size);
        
        return KomgaPageableDto.<KomgaSeriesDto>builder()
                .content(List.of())
                .number(0)
                .size(0)
                .pageable(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Pageable())
                .sort(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Sort())
                .numberOfElements(0)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .build();
    }
    
    public KomgaPageableDto<KomgaSeriesDto> getUpdatedSeries(List<Long> libraryIds, int page, int size, boolean unpaged) {
        log.debug("Getting updated series (not implemented) for libraryIds: {}, page: {}, size: {}", libraryIds, page, size);
        
        return KomgaPageableDto.<KomgaSeriesDto>builder()
                .content(List.of())
                .number(0)
                .size(0)
                .pageable(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Pageable())
                .sort(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Sort())
                .numberOfElements(0)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .build();
    }

    public KomgaPageableDto<KomgaSeriesDto> getAllSeries(Long libraryId, int page, int size, boolean unpaged) {
        log.debug("Getting all series for libraryId: {}, page: {}, size: {}", libraryId, page, size);
        
        // Check if we should group unknown series
        boolean groupUnknown = appSettingService.getAppSettings().isKomgaGroupUnknown();
        
        // Get distinct series names directly from database (MUCH faster than loading all books)
        List<String> sortedSeriesNames;
        if (groupUnknown) {
            // Use optimized query that groups books without series as "Unknown Series"
            if (libraryId != null) {
                sortedSeriesNames = bookRepository.findDistinctSeriesNamesGroupedByLibraryId(
                    libraryId, komgaMapper.getUnknownSeriesName());
            } else {
                sortedSeriesNames = bookRepository.findDistinctSeriesNamesGrouped(
                    komgaMapper.getUnknownSeriesName());
            }
        } else {
            // Use query that gives each book without series its own entry
            if (libraryId != null) {
                sortedSeriesNames = bookRepository.findDistinctSeriesNamesUngroupedByLibraryId(libraryId);
            } else {
                sortedSeriesNames = bookRepository.findDistinctSeriesNamesUngrouped();
            }
        }
        
        log.debug("Found {} distinct series names from database (optimized)", sortedSeriesNames.size());
        
        // Calculate pagination
        int totalElements = sortedSeriesNames.size();
        List<String> pageSeriesNames;
        int actualPage;
        int actualSize;
        int totalPages;
        
        if (unpaged) {
            pageSeriesNames = sortedSeriesNames;
            actualPage = 0;
            actualSize = totalElements;
            totalPages = totalElements > 0 ? 1 : 0;
        } else {
            totalPages = (int) Math.ceil((double) totalElements / size);
            int fromIndex = Math.min(page * size, totalElements);
            int toIndex = Math.min(fromIndex + size, totalElements);
            
            pageSeriesNames = sortedSeriesNames.subList(fromIndex, toIndex);
            actualPage = page;
            actualSize = size;
        }
        
        // Now load books only for the series on this page (optimized - only loads what's needed)
        List<KomgaSeriesDto> content = new ArrayList<>();
        for (String seriesName : pageSeriesNames) {
            try {
                // Load only the books for this specific series
                List<BookEntity> seriesBooks;
                if (libraryId != null) {
                    if (groupUnknown) {
                        seriesBooks = bookRepository.findBooksBySeriesNameGroupedByLibraryId(
                            seriesName, libraryId, komgaMapper.getUnknownSeriesName());
                    } else {
                        seriesBooks = bookRepository.findBooksBySeriesNameUngroupedByLibraryId(
                            seriesName, libraryId);
                    }
                } else {
                    // For all libraries, need to load all books and filter (less common case)
                    List<BookEntity> allBooks = bookRepository.findAllWithMetadata();
                    seriesBooks = allBooks.stream()
                            .filter(book -> komgaMapper.getBookSeriesName(book).equals(seriesName))
                            .collect(Collectors.toList());
                }
                
                if (!seriesBooks.isEmpty()) {
                    Long libId = seriesBooks.get(0).getLibrary().getId();
                    KomgaSeriesDto seriesDto = komgaMapper.toKomgaSeriesDto(seriesName, libId, seriesBooks);
                    if (seriesDto != null) {
                        content.add(seriesDto);
                    }
                }
            } catch (Exception e) {
                log.error("Error mapping series: {}", seriesName, e);
            }
        }
        
        log.debug("Mapped {} series DTOs for this page", content.size());
        
        return KomgaPageableDto.<KomgaSeriesDto>builder()
                .content(content)
                .number(actualPage)
                .size(actualSize)
                .pageable(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Pageable())
                .sort(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Sort())
                .numberOfElements(content.size())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(actualPage == 0)
                .last(totalElements == 0 || actualPage >= totalPages - 1)
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
        
        // Get books matching the series - optimized to query by series name
        List<BookEntity> allSeriesBooks = bookRepository.findAllWithMetadataByLibraryId(libraryId);
        
        // Find the series name that matches this slug
        List<BookEntity> seriesBooks = allSeriesBooks.stream()
                .filter(book -> {
                    String bookSeriesName = komgaMapper.getBookSeriesName(book);
                    String bookSeriesSlug = NON_ALPHANUMERIC_PATTERN.matcher(bookSeriesName.toLowerCase()).replaceAll("-");
                    return bookSeriesSlug.equals(seriesSlug);
                })
                .collect(Collectors.toList());
        
        if (seriesBooks.isEmpty()) {
            throw new RuntimeException("Series not found");
        }
        
        String seriesName = komgaMapper.getBookSeriesName(seriesBooks.get(0));
        
        return komgaMapper.toKomgaSeriesDto(seriesName, libraryId, seriesBooks);
    }

    public KomgaPageableDto<KomgaBookDto> getBooksBySeries(String seriesId, int page, int size, boolean unpaged) {
        // Parse seriesId to extract library and series name
        String[] parts = seriesId.split("-", 2);
        if (parts.length < 2) {
            throw new RuntimeException("Invalid series ID");
        }
        
        Long libraryId = Long.parseLong(parts[0]);
        String seriesSlug = parts[1];
        
        // Get all books for the library once
        List<BookEntity> allBooks = bookRepository.findAllWithMetadataByLibraryId(libraryId);
        
        // Filter and sort books for this series
        List<BookEntity> seriesBooks = allBooks.stream()
                .filter(book -> {
                    String bookSeriesName = komgaMapper.getBookSeriesName(book);
                    String bookSeriesSlug = NON_ALPHANUMERIC_PATTERN.matcher(bookSeriesName.toLowerCase()).replaceAll("-");
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
                    .map(book -> komgaMapper.toKomgaBookDto(book))
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
                    .map(book -> komgaMapper.toKomgaBookDto(book))
                    .collect(Collectors.toList());
            actualPage = page;
            actualSize = size;
        }
        
        return KomgaPageableDto.<KomgaBookDto>builder()
                .content(content)
                .number(actualPage)
                .size(actualSize)
                .pageable(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Pageable())
                .sort(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Sort())
                .numberOfElements(content.size())
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
                .map(book -> komgaMapper.toKomgaBookDto(book))
                .collect(Collectors.toList());
        
        return KomgaPageableDto.<KomgaBookDto>builder()
                .content(content)
                .number(page)
                .size(size)
                .pageable(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Pageable())
                .sort(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Sort())
                .numberOfElements(content.size())
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
            String seriesName = komgaMapper.getBookSeriesName(book);
            seriesMap.computeIfAbsent(seriesName, k -> new ArrayList<>()).add(book);
        }
        
        return seriesMap;
    }
    
    public KomgaPageableDto<KomgaCollectionDto> getCollections(Long userId, int page, int size, boolean unpaged) {
        log.debug("Getting collections for user {}, page: {}, size: {}, unpaged: {}", userId, page, size, unpaged);
        
        List<MagicShelf> magicShelves = magicShelfService.getUserShelvesForOpds(userId);
        log.debug("Found {} magic shelves", magicShelves.size());
        List<Shelf> shelves = shelfService.getShelvesForUser(userId);

        // Convert to collection DTOs - for now, series count is 0 since we don't have 
        // the series filter implementation
        List<KomgaCollectionDto> allCollections = magicShelves.stream()
                .map(shelf -> komgaMapper.toKomgaCollectionDto(shelf))
                .sorted(Comparator.comparing(KomgaCollectionDto::getName))
                .collect(Collectors.toList());
        allCollections.addAll(shelves.stream()
                .map(shelf -> komgaMapper.toKomgaCollectionDto(shelf))
                .sorted(Comparator.comparing(KomgaCollectionDto::getName))
                .collect(Collectors.toList()));
        log.debug("Mapped to {} collection DTOs", allCollections.size());
        
        // Handle unpaged mode
        int totalElements = allCollections.size();
        List<KomgaCollectionDto> content;
        int actualPage;
        int actualSize;
        int totalPages;
        
        if (unpaged) {
            content = allCollections;
            actualPage = 0;
            actualSize = totalElements;
            totalPages = totalElements > 0 ? 1 : 0;
        } else {
            // Paginate
            totalPages = (int) Math.ceil((double) totalElements / size);
            int fromIndex = Math.min(page * size, totalElements);
            int toIndex = Math.min(fromIndex + size, totalElements);
            
            content = allCollections.subList(fromIndex, toIndex);
            actualPage = page;
            actualSize = size;
        }
        
        return KomgaPageableDto.<KomgaCollectionDto>builder()
                .content(content)
                .number(actualPage)
                .size(actualSize)
                .pageable(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Pageable())
                .sort(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Sort())
                .numberOfElements(content.size())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(actualPage == 0)
                .last(totalElements == 0 || actualPage >= totalPages - 1)
                .empty(content.isEmpty())
                .build();
    }
    
    public Resource getBookPageImage(Long bookId, Integer pageNumber, boolean convertToPng) throws IOException {
        log.debug("Getting page {} from book {} (convert to PNG: {})", pageNumber, bookId, convertToPng);
        
        BookEntity book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found: " + bookId));

        boolean isPDF = book.getPrimaryBookFile().getBookType() == BookFileType.PDF;
     
        // Stream the page to a ByteArrayOutputStream
        // streamPageImage will throw if page does not exist
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Make sure pages are cached
        if (isPDF) {
            pdfReaderService.getAvailablePages(bookId);
            pdfReaderService.streamPageImage(bookId, pageNumber, outputStream);
        } else {
            cbxReaderService.getAvailablePages(bookId);
            cbxReaderService.streamPageImage(bookId, pageNumber, outputStream);
        }
        
        byte[] imageData = outputStream.toByteArray();
        
        // If conversion to PNG is requested, convert the image
        if (convertToPng) {
            imageData = convertImageToPng(imageData);
        }
        
        return new ByteArrayResource(imageData);
    }
    
    private byte[] convertImageToPng(byte[] imageData) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IOException("Failed to read image data");
            }
            
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }
    
    public List<String> getGenres(List<Long> libraryIds, Long collectionId) {
        log.debug("Getting genres with libraryIds: {}, collectionId: {}", libraryIds, collectionId);
        
        List<BookEntity> books = getBooksForFilter(libraryIds, collectionId);
        
        return books.stream()
                .map(BookEntity::getMetadata)
                .filter(Objects::nonNull)
                .flatMap(metadata -> {
                    if (metadata.getCategories() != null) {
                        return metadata.getCategories().stream().map(category -> category.getName());
                    }
                    return java.util.stream.Stream.empty();
                })
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
    
    public List<String> getTags(List<Long> libraryIds, Long collectionId) {
        log.debug("Getting tags with libraryIds: {}, collectionId: {}", libraryIds, collectionId);
        
        List<BookEntity> books = getBooksForFilter(libraryIds, collectionId);
        
        return books.stream()
                .map(BookEntity::getMetadata)
                .filter(Objects::nonNull)
                .flatMap(metadata -> {
                    if (metadata.getTags() != null) {
                        return metadata.getTags().stream().map(tag -> tag.getName());
                    }
                    return java.util.stream.Stream.empty();
                })
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
    
    public List<String> getPublishers(List<Long> libraryIds, Long collectionId) {
        log.debug("Getting publishers with libraryIds: {}, collectionId: {}", libraryIds, collectionId);
        
        List<BookEntity> books = getBooksForFilter(libraryIds, collectionId);
        
        return books.stream()
                .map(BookEntity::getMetadata)
                .filter(Objects::nonNull)
                .map(BookMetadataEntity::getPublisher)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
    
    public KomgaPageableDto<KomgaAuthorDto> getAuthors(String search, String role, List<Long> libraryIds, 
                                                        Long collectionId, String seriesId, Long readlistId,
                                                        int page, int size, boolean unpaged) {
        log.debug("Getting authors with search: {}, role: {}, libraryIds: {}, collectionId: {}, seriesId: {}, readlistId: {}", 
                  search, role, libraryIds, collectionId, seriesId, readlistId);
        
        List<BookEntity> books = getBooksForFilter(libraryIds, collectionId);
        
        // Filter by series if provided
        if (seriesId != null && !seriesId.isEmpty()) {
            String[] parts = seriesId.split("-", 2);
            if (parts.length >= 2) {
                String seriesSlug = parts[1];
                books = books.stream()
                        .filter(book -> {
                            String bookSeriesName = komgaMapper.getBookSeriesName(book);
                            String bookSeriesSlug = NON_ALPHANUMERIC_PATTERN.matcher(bookSeriesName.toLowerCase()).replaceAll("-");
                            return bookSeriesSlug.equals(seriesSlug);
                        })
                        .collect(Collectors.toList());
            }
        }
        
        // Collect all authors with their book counts
        Map<String, Integer> authorBookCounts = new HashMap<>();
        for (BookEntity book : books) {
            BookMetadataEntity metadata = book.getMetadata();
            if (metadata != null && metadata.getAuthors() != null) {
                for (var author : metadata.getAuthors()) {
                    String authorName = author.getName();
                    authorBookCounts.put(authorName, authorBookCounts.getOrDefault(authorName, 0) + 1);
                }
            }
        }
        
        // Filter by search if provided
        List<KomgaAuthorDto> allAuthors = authorBookCounts.entrySet().stream()
                .filter(entry -> search == null || search.isEmpty() || 
                               entry.getKey().toLowerCase().contains(search.toLowerCase()))
                .map(entry -> KomgaAuthorDto.builder()
                        .name(entry.getKey())
                        .role(role != null ? role : "writer")
                        .build())
                .sorted(Comparator.comparing(KomgaAuthorDto::getName))
                .collect(Collectors.toList());
        
        // Handle pagination
        int totalElements = allAuthors.size();
        List<KomgaAuthorDto> content;
        int actualPage;
        int actualSize;
        int totalPages;
        
        if (unpaged) {
            content = allAuthors;
            actualPage = 0;
            actualSize = totalElements;
            totalPages = totalElements > 0 ? 1 : 0;
        } else {
            totalPages = (int) Math.ceil((double) totalElements / size);
            int fromIndex = Math.min(page * size, totalElements);
            int toIndex = Math.min(fromIndex + size, totalElements);
            
            content = allAuthors.subList(fromIndex, toIndex);
            actualPage = page;
            actualSize = size;
        }
        
        return KomgaPageableDto.<KomgaAuthorDto>builder()
                .content(content)
                .number(actualPage)
                .size(actualSize)
                .pageable(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Pageable())
                .sort(KomgaCleanContext.isCleanMode() ? null : new KomgaPageableDto.Sort())
                .numberOfElements(content.size())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(actualPage == 0)
                .last(totalElements == 0 || actualPage >= totalPages - 1)
                .empty(content.isEmpty())
                .build();
    }
    
    private List<BookEntity> getBooksForFilter(List<Long> libraryIds, Long collectionId) {
        List<BookEntity> books;
        
        if (libraryIds != null && !libraryIds.isEmpty()) {
            books = bookRepository.findAllWithMetadataByLibraryIds(libraryIds);
        } else {
            books = bookRepository.findAllWithMetadata();
        }
        
        // TODO: Filter by collectionId when collections are properly implemented
        // For now, we ignore collectionId as it's not yet properly mapped to magic shelves
        
        return books;
    }
}