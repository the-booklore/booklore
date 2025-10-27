package com.adityachandel.booklore.service.reader;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReaderService {

    private static final String CACHE_INFO_FILENAME = ".cache-info";
    private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("\\D+");

    private final BookRepository bookRepository;
    private final AppSettingService appSettingService;
    private final FileService fileService;

    public List<Integer> getAvailablePages(Long bookId) throws IOException {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        String bookFullPath = FileUtils.getBookFullPath(bookEntity);

        Path pdfPath = Path.of(bookFullPath);
        Path cacheDir = Path.of(fileService.getPdfCachePath(), String.valueOf(bookId));
        Path cacheInfoPath = cacheDir.resolve(CACHE_INFO_FILENAME);
        long maxCacheSizeBytes = appSettingService.getAppSettings().getPdfCacheSizeInMb() * 1024L * 1024L;
        long estimatedSize = Files.size(pdfPath);
        if (estimatedSize > maxCacheSizeBytes) {
            log.warn("Cache skipped: Estimated PDF size {} exceeds max cache size {}", estimatedSize, maxCacheSizeBytes);
            throw ApiError.CACHE_TOO_LARGE.createException();
        }

        try {
            if (needsCacheRefresh(pdfPath, cacheInfoPath)) {
                log.info("Invalidating cache for PDF book {}", bookId);
                if (Files.exists(cacheDir)) FileUtils.deleteDirectoryRecursively(cacheDir);
                Files.createDirectories(cacheDir);
                extractPdfPages(pdfPath, cacheDir);
                writeCacheInfo(pdfPath, cacheInfoPath);
            }

            try (Stream<Path> stream = Files.list(cacheDir)) {
                return stream
                        .filter(p -> p.toString().endsWith(".jpg"))
                        .sorted()
                        .map(p -> extractPageNumber(p.getFileName().toString()))
                        .filter(p -> p != -1)
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            log.error("Failed to extract pages for book {}", bookId, e);
            throw new UncheckedIOException("Failed to extract pages from PDF for bookId: " + bookId, e);
        }
    }

    public void streamPageImage(Long bookId, int page, OutputStream outputStream) throws IOException {
        Path pagePath = Path.of(fileService.getPdfCachePath(), String.valueOf(bookId), String.format("%04d.jpg", page));
        if (!Files.exists(pagePath)) throw new FileNotFoundException("Page not found: " + page);
        try (InputStream in = Files.newInputStream(pagePath)) {
            try {
                in.transferTo(outputStream);
            } catch (IOException e) {
                log.error("Error streaming page {} of book {}", page, bookId, e);
                throw new UncheckedIOException("Failed to stream PDF page image for bookId: " + bookId, e);
            }
        }
    }

    private void extractPdfPages(Path pdfPath, Path targetDir) throws IOException {
        if (!Files.isReadable(pdfPath)) {
            throw new FileNotFoundException("PDF file is not readable: " + pdfPath);
        }
        try (PDDocument document = Loader.loadPDF(new File(pdfPath.toFile().toURI()))) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 200, ImageType.RGB);
                Path outputFile = targetDir.resolve(String.format("%04d.jpg", i + 1));
                ImageIO.write(image, "JPEG", outputFile.toFile());
            }
        } catch (IOException e) {
            log.error("Failed to render PDF pages from {}", pdfPath, e);
            throw new UncheckedIOException("Error rendering PDF to images", e);
        }
    }

    private boolean needsCacheRefresh(Path pdfPath, Path cacheInfoPath) throws IOException {
        if (!Files.exists(cacheInfoPath)) return true;

        long currentLastModified = Files.getLastModifiedTime(pdfPath).toMillis();
        String recordedTimestamp = Files.readString(cacheInfoPath).trim();

        try {
            long recordedLastModified = Long.parseLong(recordedTimestamp);
            return recordedLastModified != currentLastModified;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private void writeCacheInfo(Path pdfPath, Path cacheInfoPath) throws IOException {
        long lastModified = Files.getLastModifiedTime(pdfPath).toMillis();
        Files.writeString(cacheInfoPath, String.valueOf(lastModified), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private int extractPageNumber(String filename) {
        try {
            return Integer.parseInt(NON_DIGIT_PATTERN.matcher(filename).replaceAll(""));
        } catch (Exception e) {
            return -1;
        }
    }
}
