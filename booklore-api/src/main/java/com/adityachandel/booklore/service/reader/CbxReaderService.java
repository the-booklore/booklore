package com.adityachandel.booklore.service.reader;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.pdfbox.io.IOUtils;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CbxReaderService {

    private static final String CACHE_INFO_FILENAME = ".cache-info";
    private static final String CBZ_EXTENSION = ".cbz";
    private static final String CBR_EXTENSION = ".cbr";
    private static final String CB7_EXTENSION = ".cb7";
    private static final String[] SUPPORTED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"};

    private final BookRepository bookRepository;
    private final AppSettingService appSettingService;
    private final FileService fileService;

    public List<Integer> getAvailablePages(Long bookId) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        String bookFullPath = FileUtils.getBookFullPath(bookEntity);

        Path cbzPath = Path.of(bookFullPath);
        Path cacheDir = Path.of(fileService.getCbxCachePath(), String.valueOf(bookId));
        Path cacheInfoPath = cacheDir.resolve(CACHE_INFO_FILENAME);

        try {
            long maxCacheSizeBytes = mbToBytes(appSettingService.getAppSettings().getCbxCacheSizeInMb());
            long estimatedSize = estimateArchiveSize(cbzPath);
            if (estimatedSize > maxCacheSizeBytes) {
                log.warn("Cache skipped: Estimated archive size {} exceeds max cache size {}", estimatedSize, maxCacheSizeBytes);
                throw ApiError.CACHE_TOO_LARGE.createException();
            }
            enforceCacheLimit();

            if (needsCacheRefresh(cbzPath, cacheInfoPath)) {
                log.info("Invalidating cache for book {}", bookId);
                if (Files.exists(cacheDir)) FileUtils.deleteDirectoryRecursively(cacheDir);
                Files.createDirectories(cacheDir);
                extractCbxArchive(cbzPath, cacheDir);
                writeCacheInfo(cbzPath, cacheInfoPath);
                if (!Files.exists(cacheDir)) {
                    log.warn("Cache for book {} was deleted during enforcement. Re-extracting.", bookId);
                    Files.createDirectories(cacheDir);
                    extractCbxArchive(cbzPath, cacheDir);
                    writeCacheInfo(cbzPath, cacheInfoPath);
                }
            }
        } catch (IOException e) {
            log.error("Failed to cache CBZ for book {}", bookId, e);
            return List.of();
        }

        try (var stream = Files.list(cacheDir)) {
            List<Path> imageFiles = stream
                    .filter(p -> isImageFile(p.getFileName().toString()))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .toList();

            return java.util.stream.IntStream.rangeClosed(1, imageFiles.size())
                    .boxed()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list pages for book {}", bookId, e);
            return List.of();
        }
    }

    public void streamPageImage(Long bookId, int page, OutputStream outputStream) throws IOException {
        Path bookDir = Path.of(fileService.getCbxCachePath(), String.valueOf(bookId));
        List<Path> images;
        try (Stream<Path> files = Files.list(bookDir)) {
            images = files
                    .filter(p -> isImageFile(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
        if (images.isEmpty()) {
            throw new FileNotFoundException("No image files found for book: " + bookId);
        }
        if (page < 1 || page > images.size()) {
            throw new FileNotFoundException("Page out of range: " + page);
        }
        Path pagePath = images.get(page - 1);
        try (InputStream in = Files.newInputStream(pagePath)) {
            IOUtils.copy(in, outputStream);
        }
    }

    private void extractCbxArchive(Path cbxPath, Path targetDir) throws IOException {
        String filename = cbxPath.getFileName().toString().toLowerCase();
        if (filename.endsWith(CBZ_EXTENSION)) {
            extractZipArchive(cbxPath, targetDir);
        } else if (filename.endsWith(CB7_EXTENSION)) {
            extract7zArchive(cbxPath, targetDir);
        } else if (filename.endsWith(CBR_EXTENSION)) {
            extractRarArchive(cbxPath, targetDir);
        } else {
            throw new IOException("Unsupported archive format: " + cbxPath.getFileName());
        }
    }

    private void extractZipArchive(Path cbzPath, Path targetDir) throws IOException {
        try (InputStream fis = Files.newInputStream(cbzPath);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && isImageFile(entry.getName())) {
                    String fileName = extractFileNameFromPath(entry.getName());
                    Path target = targetDir.resolve(fileName);
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private void extract7zArchive(Path cb7Path, Path targetDir) throws IOException {
        try (SevenZFile sevenZFile = new SevenZFile(cb7Path.toFile())) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (!entry.isDirectory() && isImageFile(entry.getName())) {
                    String fileName = extractFileNameFromPath(entry.getName());
                    Path target = targetDir.resolve(fileName);
                    try (OutputStream out = Files.newOutputStream(target)) {
                        copySevenZEntry(sevenZFile, out, entry.getSize());
                    }
                }
            }
        }
    }

    private void copySevenZEntry(SevenZFile sevenZFile, OutputStream out, long size) throws IOException {
        byte[] buffer = new byte[8192];
        long read = 0;
        int count;
        while (read < size && (count = sevenZFile.read(buffer, 0, (int) Math.min(buffer.length, size - read))) > 0) {
            out.write(buffer, 0, count);
            read += count;
        }
    }

    private void extractRarArchive(Path cbrPath, Path targetDir) throws IOException {
        try (Archive archive = new Archive(cbrPath.toFile())) {
            List<FileHeader> headers = archive.getFileHeaders();
            for (FileHeader header : headers) {
                if (!header.isDirectory() && isImageFile(header.getFileName())) {
                    String fileName = extractFileNameFromPath(header.getFileName());
                    Path target = targetDir.resolve(fileName);
                    try (OutputStream out = Files.newOutputStream(target)) {
                        archive.extractFile(header, out);
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to extract CBR archive", e);
        }
    }

    private String extractFileNameFromPath(String fullPath) {
        String normalizedPath = fullPath.replace("\\", "/");
        int lastSlash = normalizedPath.lastIndexOf('/');
        return lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;
    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase().replace("\\", "/");
        for (String extension : SUPPORTED_IMAGE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }


    private boolean needsCacheRefresh(Path cbzPath, Path cacheInfoPath) throws IOException {
        if (!Files.exists(cacheInfoPath)) return true;

        long currentLastModified = Files.getLastModifiedTime(cbzPath).toMillis();
        String recordedTimestamp = Files.readString(cacheInfoPath).trim();

        try {
            long recordedLastModified = Long.parseLong(recordedTimestamp);
            return recordedLastModified != currentLastModified;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private void writeCacheInfo(Path cbzPath, Path cacheInfoPath) throws IOException {
        long lastModified = Files.getLastModifiedTime(cbzPath).toMillis();
        Files.writeString(cacheInfoPath, String.valueOf(lastModified), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void enforceCacheLimit() {
        try {
            Path cacheRoot = Path.of(fileService.getCbxCachePath());
            if (!Files.exists(cacheRoot) || !Files.isDirectory(cacheRoot)) {
                return;
            }
            long totalSize = 0L;
            List<Path> cacheDirs;
            try (Stream<Path> stream = Files.list(cacheRoot)) {
                cacheDirs = stream.filter(Files::isDirectory).collect(Collectors.toList());
            }

            for (Path dir : cacheDirs) {
                totalSize += getDirectorySize(dir);
            }

            long maxCacheSizeBytes = mbToBytes(appSettingService.getAppSettings().getCbxCacheSizeInMb());

            while (totalSize > maxCacheSizeBytes && !cacheDirs.isEmpty()) {
                Path leastRecentlyReadDir = cacheDirs.stream()
                        .min(Comparator.comparingLong(this::getLastReadTime))
                        .orElse(null);

                long sizeFreed = getDirectorySize(leastRecentlyReadDir);
                FileUtils.deleteDirectoryRecursively(leastRecentlyReadDir);
                cacheDirs.remove(leastRecentlyReadDir);
                totalSize -= sizeFreed;
                log.info("Deleted cache directory {} to enforce cache size limit", leastRecentlyReadDir);
            }
        } catch (IOException e) {
            log.error("Error enforcing cache size limit", e);
        }
    }

    private long getLastReadTime(Path cacheDir) {
        Path cacheInfoPath = cacheDir.resolve(CACHE_INFO_FILENAME);
        if (!Files.exists(cacheInfoPath)) {
            return Long.MIN_VALUE;
        }
        try {
            String content = Files.readString(cacheInfoPath).trim();
            return Long.parseLong(content);
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    private long getDirectorySize(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private long estimateArchiveSize(Path cbxPath) {
        try {
            String name = cbxPath.getFileName().toString().toLowerCase();
            if (name.endsWith(CBZ_EXTENSION)) {
                return estimateCbzArchiveSize(cbxPath);
            } else if (name.endsWith(CB7_EXTENSION)) {
                return estimateCb7ArchiveSize(cbxPath);
            } else if (name.endsWith(CBR_EXTENSION)) {
                return estimateCbrArchiveSize(cbxPath);
            }
        } catch (Exception e) {
            log.warn("Failed to estimate archive size for {}", cbxPath, e);
        }
        return Long.MAX_VALUE;
    }

    private long estimateCbzArchiveSize(Path cbxPath) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(cbxPath))) {
            ZipEntry entry;
            long total = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && isImageFile(entry.getName())) {
                    total += entry.getCompressedSize();
                }
            }
            return total;
        }
    }

    private long estimateCb7ArchiveSize(Path cbxPath) throws IOException {
        try (SevenZFile sevenZFile = new SevenZFile(cbxPath.toFile())) {
            SevenZArchiveEntry entry;
            long total = 0;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (!entry.isDirectory() && isImageFile(entry.getName())) {
                    total += entry.getSize();
                }
            }
            return total;
        }
    }

    private long estimateCbrArchiveSize(Path cbxPath) throws IOException, RarException {
        try (Archive archive = new Archive(cbxPath.toFile())) {
            long total = 0;
            for (FileHeader header : archive.getFileHeaders()) {
                if (!header.isDirectory() && isImageFile(header.getFileName())) {
                    total += header.getFullUnpackSize();
                }
            }
            return total;
        }
    }

    private long mbToBytes(int mb) {
        return mb * 1024L * 1024L;
    }
}