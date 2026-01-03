package com.adityachandel.booklore.service.customfont;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.mapper.CustomFontMapper;
import com.adityachandel.booklore.model.dto.CustomFontDto;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.CustomFontEntity;
import com.adityachandel.booklore.model.enums.FontFormat;
import com.adityachandel.booklore.repository.CustomFontRepository;
import com.adityachandel.booklore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFontService {

    private final CustomFontRepository customFontRepository;
    private final UserRepository userRepository;
    private final CustomFontMapper customFontMapper;
    private final AppProperties appProperties;

    private static final int MAX_FONTS_PER_USER = 10;
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB
    private static final String CUSTOM_FONTS_DIR = "custom-fonts";

    @Transactional
    public CustomFontDto uploadFont(MultipartFile file, String fontName, Long userId) {
        Path fontPath = null;
        CustomFontEntity savedEntity = null;

        try {
            // Validate upload
            validateFontUpload(file, userId);

            // Get user entity
            BookLoreUserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Determine font format
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                throw new IllegalArgumentException("Invalid file name");
            }

            String extension = getFileExtension(originalFilename);
            FontFormat format = FontFormat.fromExtension(extension);

            // Generate unique identifiers
            String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String fileName = String.format("user_%d_font_%s%s", userId, uuid, format.getExtension());
            String cssIdentifier = "custom-font-" + uuid;

            // Save file to disk
            Path fontDir = getFontDirectory(userId);
            Files.createDirectories(fontDir);
            fontPath = fontDir.resolve(fileName);

            // Validate path to prevent directory traversal
            validatePath(fontPath, fontDir);

            file.transferTo(fontPath.toFile());

            // Validate font file magic bytes
            validateFontMagicBytes(fontPath, format);

            // Create entity
            CustomFontEntity entity = CustomFontEntity.builder()
                    .user(user)
                    .fontName(fontName != null && !fontName.trim().isEmpty() ? fontName.trim() : originalFilename)
                    .fileName(fileName)
                    .originalFileName(originalFilename)
                    .format(format)
                    .fileSize(file.getSize())
                    .uploadedAt(LocalDateTime.now())
                    .cssIdentifier(cssIdentifier)
                    .build();

            savedEntity = customFontRepository.save(entity);
            log.info("Font uploaded successfully for user {}: {} ({})", userId, fontName, fileName);

            return customFontMapper.toDto(savedEntity);

        } catch (IOException e) {
            log.error("Failed to upload font for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to save font file: " + e.getMessage(), e);
        } finally {
            // Cleanup file if database save failed
            if (fontPath != null && savedEntity == null) {
                try {
                    Files.deleteIfExists(fontPath);
                    log.debug("Cleaned up orphaned font file after upload failure: {}", fontPath);
                } catch (IOException e) {
                    log.warn("Failed to cleanup orphaned font file: {}", fontPath, e);
                }
            }
        }
    }

    public List<CustomFontDto> getUserFonts(Long userId) {
        List<CustomFontEntity> fonts = customFontRepository.findByUserId(userId);
        return fonts.stream()
                .map(customFontMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteFont(Long fontId, Long userId) {
        CustomFontEntity font = customFontRepository.findByIdAndUserId(fontId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Font not found or access denied"));

        try {
            // Delete file from disk
            Path fontDir = getFontDirectory(userId);
            Path fontPath = fontDir.resolve(font.getFileName());

            // Validate path to prevent directory traversal
            validatePath(fontPath, fontDir);

            Files.deleteIfExists(fontPath);

            // Delete from database (CASCADE will handle epub_viewer_preference references)
            customFontRepository.delete(font);

            log.info("Font deleted successfully for user {}: {} ({})", userId, font.getFontName(), font.getFileName());

        } catch (IOException e) {
            log.error("Failed to delete font file for user {}: {}", userId, e.getMessage(), e);
            // Still delete from database even if file deletion fails
            customFontRepository.delete(font);
            throw new RuntimeException("Failed to delete font file: " + e.getMessage(), e);
        }
    }

    public Resource getFontFile(Long fontId, Long userId) {
        CustomFontEntity font = customFontRepository.findByIdAndUserId(fontId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Font not found or access denied"));

        Path fontDir = getFontDirectory(userId);
        Path fontPath = fontDir.resolve(font.getFileName());

        // Validate path to prevent directory traversal
        try {
            validatePath(fontPath, fontDir);
        } catch (IOException e) {
            log.error("Invalid font path for user {}: {}", userId, fontPath, e);
            throw new IllegalArgumentException("Invalid font file path");
        }

        File fontFile = fontPath.toFile();

        if (!fontFile.exists()) {
            throw new IllegalArgumentException("Font file not found on disk");
        }

        return new FileSystemResource(fontFile);
    }

    public FontFormat getFontFormat(Long fontId, Long userId) {
        CustomFontEntity font = customFontRepository.findByIdAndUserId(fontId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Font not found or access denied"));
        return font.getFormat();
    }

    private void validateFontUpload(MultipartFile file, Long userId) {
        // Check if file is empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 5MB");
        }

        // Check user quota
        int currentFontCount = customFontRepository.countByUserId(userId);
        if (currentFontCount >= MAX_FONTS_PER_USER) {
            throw new IllegalArgumentException(String.format("Font limit exceeded. Maximum %d fonts per user allowed", MAX_FONTS_PER_USER));
        }

        // Validate file extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Invalid file name");
        }

        String extension = getFileExtension(originalFilename);
        if (!FontFormat.isSupportedExtension(extension)) {
            throw new IllegalArgumentException("Unsupported font format. Allowed formats: .ttf, .otf, .woff, .woff2");
        }

        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType != null && !isSupportedMimeType(contentType)) {
            log.warn("Potentially invalid MIME type for font: {}", contentType);
            // Don't throw exception, just log warning, as browsers may send different MIME types
        }
    }

    private boolean isSupportedMimeType(String mimeType) {
        return mimeType.equals("font/ttf") ||
               mimeType.equals("font/otf") ||
               mimeType.equals("font/woff") ||
               mimeType.equals("font/woff2") ||
               mimeType.equals("application/x-font-ttf") ||
               mimeType.equals("application/x-font-opentype") ||
               mimeType.equals("application/font-woff") ||
               mimeType.equals("application/font-woff2") ||
               mimeType.equals("application/octet-stream"); // Generic binary type
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("File has no extension");
        }
        return filename.substring(lastDotIndex);
    }

    private Path getFontDirectory(Long userId) {
        return Paths.get(appProperties.getPathConfig(), CUSTOM_FONTS_DIR, String.valueOf(userId));
    }

    /**
     * Validates that the resolved path stays within the expected parent directory
     * to prevent directory traversal attacks.
     *
     * @param resolvedPath The resolved file path to validate
     * @param expectedParent The expected parent directory
     * @throws IOException if path traversal is detected
     */
    private void validatePath(Path resolvedPath, Path expectedParent) throws IOException {
        Path normalizedPath = resolvedPath.toAbsolutePath().normalize();
        Path normalizedParent = expectedParent.toAbsolutePath().normalize();

        if (!normalizedPath.startsWith(normalizedParent)) {
            log.error("Path traversal attempt detected: {} does not start with {}", normalizedPath, normalizedParent);
            throw new IOException("Invalid file path: path traversal detected");
        }
    }

    /**
     * Validates font file format by checking magic bytes (file signature).
     * This prevents malicious files from being uploaded with font extensions.
     *
     * @param fontPath The path to the font file
     * @param expectedFormat The expected font format based on extension
     * @throws IOException if file cannot be read or format is invalid
     */
    private void validateFontMagicBytes(Path fontPath, FontFormat expectedFormat) throws IOException {
        byte[] header = Files.readAllBytes(fontPath);

        if (header.length < 4) {
            throw new IllegalArgumentException("Invalid font file: file too small");
        }

        boolean isValid = false;
        String detectedFormat = "Unknown";

        // Check TTF signature: 0x00 0x01 0x00 0x00 or "true" (0x74 0x72 0x75 0x65)
        if (header.length >= 4 &&
            ((header[0] == 0x00 && header[1] == 0x01 && header[2] == 0x00 && header[3] == 0x00) ||
             (header[0] == 0x74 && header[1] == 0x72 && header[2] == 0x75 && header[3] == 0x65))) {
            isValid = (expectedFormat == FontFormat.TTF);
            detectedFormat = "TTF";
        }
        // Check OTF signature: "OTTO" (0x4F 0x54 0x54 0x4F)
        else if (header.length >= 4 &&
                 header[0] == 0x4F && header[1] == 0x54 && header[2] == 0x54 && header[3] == 0x4F) {
            isValid = (expectedFormat == FontFormat.OTF);
            detectedFormat = "OTF";
        }
        // Check WOFF signature: "wOFF" (0x77 0x4F 0x46 0x46)
        else if (header.length >= 4 &&
                 header[0] == 0x77 && header[1] == 0x4F && header[2] == 0x46 && header[3] == 0x46) {
            isValid = (expectedFormat == FontFormat.WOFF);
            detectedFormat = "WOFF";
        }
        // Check WOFF2 signature: "wOF2" (0x77 0x4F 0x46 0x32)
        else if (header.length >= 4 &&
                 header[0] == 0x77 && header[1] == 0x4F && header[2] == 0x46 && header[3] == 0x32) {
            isValid = (expectedFormat == FontFormat.WOFF2);
            detectedFormat = "WOFF2";
        }

        if (!isValid) {
            log.error("Font file magic bytes validation failed. Expected: {}, Detected: {}", expectedFormat, detectedFormat);
            throw new IllegalArgumentException(
                String.format("Invalid font file format. File appears to be %s but extension indicates %s",
                              detectedFormat, expectedFormat)
            );
        }

        log.debug("Font file magic bytes validated successfully: {}", detectedFormat);
    }
}
