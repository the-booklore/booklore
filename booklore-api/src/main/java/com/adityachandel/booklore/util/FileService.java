package com.adityachandel.booklore.util;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RequiredArgsConstructor
@Service
public class FileService {

    private final AppProperties appProperties;
    private final AppSettingService appSettingService;

    public void createThumbnailFromFile(long bookId, MultipartFile file) {
        try {
            validateCoverFile(file);
            String outputFolder = getThumbnailPath(bookId);
            File folder = new File(outputFolder);
            if (!folder.exists() && !folder.mkdirs()) {
                throw ApiError.DIRECTORY_CREATION_FAILED.createException(folder.getAbsolutePath());
            }
            BufferedImage originalImage = ImageIO.read(file.getInputStream());
            if (originalImage == null) {
                throw ApiError.IMAGE_NOT_FOUND.createException();
            }
            BufferedImage resizedImage = resizeImage(originalImage);
            File outputFile = new File(folder, "f.jpg");
            ImageIO.write(resizedImage, "JPEG", outputFile);
            log.info("Thumbnail created and saved at: {}", outputFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("An error occurred while creating the thumbnail: {}", e.getMessage(), e);
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        }
    }

    private void validateCoverFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (!("image/jpeg".equalsIgnoreCase(contentType) || "image/png".equalsIgnoreCase(contentType))) {
            throw new IllegalArgumentException("Only JPEG and PNG files are allowed");
        }
        long maxFileSize = 5 * 1024 * 1024;
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size must not exceed 5 MB");
        }
    }

    public Resource getBookCover(String thumbnailPath) {
        Path thumbPath;
        if (thumbnailPath == null || thumbnailPath.isEmpty()) {
            thumbPath = Paths.get(getMissingThumbnailPath());
        } else {
            thumbPath = Paths.get(thumbnailPath);
        }
        try {
            Resource resource = new UrlResource(thumbPath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw ApiError.IMAGE_NOT_FOUND.createException(thumbPath);
            }
        } catch (IOException e) {
            throw ApiError.IMAGE_NOT_FOUND.createException(thumbPath);
        }
    }

    public String createThumbnail(long bookId, String thumbnailUrl) throws IOException {
        String newFilename = "f.jpg";
        resizeAndSaveImage(thumbnailUrl, new File(getThumbnailPath(bookId)), newFilename);
        return getThumbnailPath(bookId) + newFilename;
    }

    private void resizeAndSaveImage(String imageSource, File outputFolder, String outputFileName) throws IOException {
        BufferedImage originalImage;
        File file = new File(imageSource);
        if (file.exists()) {
            try (InputStream inputStream = new FileInputStream(file)) {
                originalImage = ImageIO.read(inputStream);
            }
        } else {
            try {
                URL url = new URL(imageSource);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                try (InputStream inputStream = connection.getInputStream()) {
                    originalImage = ImageIO.read(inputStream);
                }
            } catch (IOException e) {
                throw new IOException("Failed to download image from URL: " + imageSource + " - " + e.getMessage(), e);
            }
        }
        if (originalImage == null) {
            throw new IOException("Failed to read image from: " + imageSource);
        }
        BufferedImage resizedImage = resizeImage(originalImage);
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outputFolder.getAbsolutePath());
        }
        File outputFile = new File(outputFolder, outputFileName);
        ImageIO.write(resizedImage, "JPEG", outputFile);
        log.info("Image saved to: {}", outputFile.getAbsolutePath());
    }

    private BufferedImage resizeImage(BufferedImage originalImage) {
        String resolution = appSettingService.getAppSettings().getCoverResolution();
        String[] split = resolution.split("x");
        int x = Integer.parseInt(split[0]);
        int y = Integer.parseInt(split[1]);

        BufferedImage resizedImage = new BufferedImage(x, y, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(originalImage, 0, 0, x, y, null);
        g2d.dispose();
        return resizedImage;
    }

    public String getThumbnailPath(long bookId) {
        return appProperties.getPathConfig() + "/thumbs/" + bookId + "/";
    }

    public String getMetadataBackupPath() {
        return appProperties.getPathConfig() + "/metadata_backup/";
    }

    public String getBookMetadataBackupPath(long bookId) {
        return appProperties.getPathConfig() + "/metadata_backup/" + bookId + "/";
    }

    public String getCbxCachePath() {
        return appProperties.getPathConfig() + "/cbx_cache";
    }

    public String getPdfCachePath() {
        return appProperties.getPathConfig() + "/pdf_cache";
    }

    public String getMissingThumbnailPath() {
        return appProperties.getPathConfig() + "/thumbs/missing/m.jpg";
    }

    public String getTempBookdropCoverImagePath(long bookdropFileId) {
        return Paths.get(appProperties.getPathConfig(), "bookdrop_temp", bookdropFileId + ".jpg").toString();
    }

    public String getBookdropPath() {
        return appProperties.getBookdropFolder();
    }
}