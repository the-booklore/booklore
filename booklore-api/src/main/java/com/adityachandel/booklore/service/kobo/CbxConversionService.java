package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.model.entity.BookEntity;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.exception.RarException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.IIOImage;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CbxConversionService {

    private static final String IMAGE_ROOT_PATH = "OEBPS/Images/";
    private static final String HTML_ROOT_PATH = "OEBPS/Text/";
    private static final String CONTENT_OPF_PATH = "OEBPS/content.opf";
    private static final String NAV_XHTML_PATH = "OEBPS/nav.xhtml";
    private static final String TOC_NCX_PATH = "OEBPS/toc.ncx";
    private static final String STYLESHEET_CSS_PATH = "OEBPS/Styles/stylesheet.css";
    private static final String COVER_IMAGE_PATH = "OEBPS/Images/cover.jpg";
    private static final String MIMETYPE_CONTENT = "application/epub+zip";
    
    private final Configuration freemarkerConfig;

    public CbxConversionService() {
        this.freemarkerConfig = initializeFreemarkerConfiguration();
    }

    public record EpubContentFileGroup(String contentKey, String imagePath, String htmlPath) {
    }

    public File convertCbxToEpub(File cbxFile, File tempDir, BookEntity bookEntity) 
            throws IOException, TemplateException, RarException {
        validateInputs(cbxFile, tempDir);
        
        log.info("Starting CBX to EPUB conversion for: {}", cbxFile.getName());
        
        File outputFile = executeCbxConversion(cbxFile, tempDir, bookEntity);
        
        log.info("Successfully converted {} to {} (size: {} bytes)",
                cbxFile.getName(), outputFile.getName(), outputFile.length());
        return outputFile;
    }

    private File executeCbxConversion(File cbxFile, File tempDir, BookEntity bookEntity) 
            throws IOException, TemplateException, RarException {
        
        Path epubFilePath = Paths.get(tempDir.getAbsolutePath(),
                cbxFile.getName().replaceFirst("\\.[^.]+$", "") + ".epub");
        File epubFile = epubFilePath.toFile();

        List<BufferedImage> images = extractImagesFromCbx(cbxFile);
        if (images.isEmpty()) {
            throw new IllegalStateException("No valid images found in CBX file: " + cbxFile.getName());
        }

        log.debug("Extracted {} images from CBX file", images.size());

        try (ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(new FileOutputStream(epubFile))) {
            addMimetypeEntry(zipOut);
            addMetaInfContainer(zipOut);
            addStylesheet(zipOut);
            
            List<EpubContentFileGroup> contentGroups = addImagesAndPages(zipOut, images);
            
            addContentOpf(zipOut, bookEntity, contentGroups);
            addTocNcx(zipOut, bookEntity, contentGroups);
            addNavXhtml(zipOut, bookEntity, contentGroups);
        }

        return epubFile;
    }

    private void validateInputs(File cbxFile, File tempDir) {
        if (cbxFile == null || !cbxFile.isFile()) {
            throw new IllegalArgumentException("Invalid CBX file: " + cbxFile);
        }

        if (!isSupportedCbxFormat(cbxFile.getName())) {
            throw new IllegalArgumentException("Unsupported file format: " + cbxFile.getName() + 
                    ". Supported formats: CBZ, CBR, CB7");
        }
        
        if (tempDir == null || !tempDir.isDirectory()) {
            throw new IllegalArgumentException("Invalid temp directory: " + tempDir);
        }
    }

    private Configuration initializeFreemarkerConfiguration() {
        Configuration config = new Configuration(Configuration.VERSION_2_3_33);
        config.setTemplateLoader(new ClassTemplateLoader(this.getClass(), "/templates/epub"));
        config.setDefaultEncoding(StandardCharsets.UTF_8.name());
        config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        config.setLogTemplateExceptions(false);
        config.setWrapUncheckedExceptions(true);
        return config;
    }

    private List<BufferedImage> extractImagesFromCbx(File cbxFile) throws IOException, RarException {
        String fileName = cbxFile.getName().toLowerCase();
        
        if (fileName.endsWith(".cbz")) {
            return extractImagesFromZip(cbxFile);
        } else if (fileName.endsWith(".cbr")) {
            return extractImagesFromRar(cbxFile);
        } else if (fileName.endsWith(".cb7")) {
            return extractImagesFrom7z(cbxFile);
        } else {
            throw new IllegalArgumentException("Unsupported archive format: " + fileName);
        }
    }
    
    private List<BufferedImage> extractImagesFromZip(File cbzFile) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        
        try (ZipFile zipFile = ZipFile.builder().setFile(cbzFile).get()) {
            List<ZipArchiveEntry> imageEntries = Collections.list(zipFile.getEntries())
                    .stream()
                    .filter(entry -> !entry.isDirectory() && isImageFile(entry.getName()))
                    .sorted(Comparator.comparing(entry -> entry.getName().toLowerCase()))
                    .collect(Collectors.toList());

            log.debug("Found {} image entries in CBZ file", imageEntries.size());

            for (ZipArchiveEntry entry : imageEntries) {
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    BufferedImage image = ImageIO.read(inputStream);
                    if (image != null) {
                        images.add(image);
                        log.debug("Successfully loaded image: {}", entry.getName());
                    } else {
                        log.warn("Failed to load image (unsupported format?): {}", entry.getName());
                    }
                } catch (Exception e) {
                    log.warn("Error reading image {}: {}", entry.getName(), e.getMessage());
                }
            }
        }
        
        return images;
    }
    
    private List<BufferedImage> extractImagesFromRar(File cbrFile) throws IOException, RarException {
        List<BufferedImage> images = new ArrayList<>();
        
        try (Archive rarFile = new Archive(cbrFile)) {
            List<FileHeader> imageHeaders = new ArrayList<>();
            
            for (FileHeader fileHeader : rarFile) {
                if (!fileHeader.isDirectory() && isImageFile(fileHeader.getFileName())) {
                    imageHeaders.add(fileHeader);
                }
            }
            
            imageHeaders.sort(Comparator.comparing(FileHeader::getFileName, String.CASE_INSENSITIVE_ORDER));
            
            log.debug("Found {} image entries in CBR file", imageHeaders.size());
            
            for (FileHeader fileHeader : imageHeaders) {
                try (InputStream inputStream = rarFile.getInputStream(fileHeader)) {
                    BufferedImage image = ImageIO.read(inputStream);
                    if (image != null) {
                        images.add(image);
                        log.debug("Successfully loaded image: {}", fileHeader.getFileName());
                    } else {
                        log.warn("Failed to load image (unsupported format?): {}", fileHeader.getFileName());
                    }
                } catch (Exception e) {
                    log.warn("Error reading image {}: {}", fileHeader.getFileName(), e.getMessage());
                }
            }
        }
        
        return images;
    }
    
    private List<BufferedImage> extractImagesFrom7z(File cb7File) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        Map<String, byte[]> imageDataMap = new HashMap<>();
        
        try (SevenZFile sevenZFile = SevenZFile.builder().setFile(cb7File).get()) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (!entry.isDirectory() && isImageFile(entry.getName())) {
                    byte[] imageData = new byte[(int) entry.getSize()];
                    sevenZFile.read(imageData);
                    imageDataMap.put(entry.getName(), imageData);
                }
            }
        }
        
        log.debug("Found {} image entries in CB7 file", imageDataMap.size());
        
        List<String> sortedImageNames = imageDataMap.keySet().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
        
        for (String imageName : sortedImageNames) {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(imageDataMap.get(imageName))) {
                BufferedImage image = ImageIO.read(bis);
                if (image != null) {
                    images.add(image);
                    log.debug("Successfully loaded image: {}", imageName);
                } else {
                    log.warn("Failed to load image (unsupported format?): {}", imageName);
                }
            } catch (Exception e) {
                log.warn("Error reading image {}: {}", imageName, e.getMessage());
            }
        }
        
        return images;
    }

    private boolean isImageFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp)$");
    }

    private void addMimetypeEntry(ZipArchiveOutputStream zipOut) throws IOException {
        byte[] mimetypeBytes = MIMETYPE_CONTENT.getBytes(StandardCharsets.UTF_8);
        ZipArchiveEntry mimetypeEntry = new ZipArchiveEntry("mimetype");
        mimetypeEntry.setMethod(ZipArchiveEntry.STORED);
        mimetypeEntry.setSize(mimetypeBytes.length);
        mimetypeEntry.setCrc(calculateCrc32(mimetypeBytes));
        
        zipOut.putArchiveEntry(mimetypeEntry);
        zipOut.write(mimetypeBytes);
        zipOut.closeArchiveEntry();
    }

    private void addMetaInfContainer(ZipArchiveOutputStream zipOut) throws IOException, TemplateException {
        Map<String, Object> model = new HashMap<>();
        model.put("contentOpfPath", CONTENT_OPF_PATH);
        
        String containerXml = processTemplate("xml/container.xml.ftl", model);
        
        ZipArchiveEntry containerEntry = new ZipArchiveEntry("META-INF/container.xml");
        zipOut.putArchiveEntry(containerEntry);
        zipOut.write(containerXml.getBytes(StandardCharsets.UTF_8));
        zipOut.closeArchiveEntry();
    }

    private void addStylesheet(ZipArchiveOutputStream zipOut) throws IOException {
        String stylesheetContent = loadResourceAsString("/templates/epub/css/stylesheet.css");
        
        ZipArchiveEntry stylesheetEntry = new ZipArchiveEntry(STYLESHEET_CSS_PATH);
        zipOut.putArchiveEntry(stylesheetEntry);
        zipOut.write(stylesheetContent.getBytes(StandardCharsets.UTF_8));
        zipOut.closeArchiveEntry();
    }

    private List<EpubContentFileGroup> addImagesAndPages(ZipArchiveOutputStream zipOut, List<BufferedImage> images) 
            throws IOException, TemplateException {
        
        List<EpubContentFileGroup> contentGroups = new ArrayList<>();

        if (!images.isEmpty()) {
            addImageToZip(zipOut, COVER_IMAGE_PATH, images.get(0));
        }

        for (int i = 0; i < images.size(); i++) {
            BufferedImage image = images.get(i);
            String contentKey = String.format("page-%04d", i + 1);
            String imageFileName = contentKey + ".jpg";
            String htmlFileName = contentKey + ".xhtml";

            String imagePath = IMAGE_ROOT_PATH + imageFileName;
            String htmlPath = HTML_ROOT_PATH + htmlFileName;

            addImageToZip(zipOut, imagePath, image);

            String htmlContent = generatePageHtml(imageFileName, i + 1);
            ZipArchiveEntry htmlEntry = new ZipArchiveEntry(htmlPath);
            zipOut.putArchiveEntry(htmlEntry);
            zipOut.write(htmlContent.getBytes(StandardCharsets.UTF_8));
            zipOut.closeArchiveEntry();

            contentGroups.add(new EpubContentFileGroup(contentKey, imagePath, htmlPath));
        }

        return contentGroups;
    }

    private void addImageToZip(ZipArchiveOutputStream zipOut, String imagePath, BufferedImage image) 
            throws IOException {
        ZipArchiveEntry imageEntry = new ZipArchiveEntry(imagePath);
        zipOut.putArchiveEntry(imageEntry);
        
        writeJpegImage(image, zipOut, 0.85f);
        
        zipOut.closeArchiveEntry();
    }
    
    private void writeJpegImage(BufferedImage image, ZipArchiveOutputStream zipOut, float quality) 
            throws IOException {
        BufferedImage rgbImage = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgbImage.getGraphics().drawImage(image, 0, 0, null);
            rgbImage.getGraphics().dispose();
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }
        
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgbImage, null, null), param);
        } finally {
            writer.dispose();
        }
        
        zipOut.write(baos.toByteArray());
    }

    private String generatePageHtml(String imageFileName, int pageNumber) throws IOException, TemplateException {
        Map<String, Object> model = new HashMap<>();
        model.put("imageFileName", "../Images/" + imageFileName);
        model.put("pageNumber", pageNumber);
        model.put("stylesheetPath", "../Styles/stylesheet.css");
        
        return processTemplate("xml/image_page.xhtml.ftl", model);
    }

    private void addContentOpf(ZipArchiveOutputStream zipOut, BookEntity bookEntity, 
                              List<EpubContentFileGroup> contentGroups) throws IOException, TemplateException {
        
        Map<String, Object> model = createBookMetadataModel(bookEntity);
        
        List<EpubContentFileGroup> relativeContentGroups = contentGroups.stream()
                .map(group -> new EpubContentFileGroup(
                        group.contentKey(),
                        makeRelativeToOebps(group.imagePath()),
                        makeRelativeToOebps(group.htmlPath())
                ))
                .toList();
        
        model.put("contentFileGroups", relativeContentGroups);
        model.put("coverImagePath", makeRelativeToOebps(COVER_IMAGE_PATH));
        model.put("tocNcxPath", makeRelativeToOebps(TOC_NCX_PATH));
        model.put("navXhtmlPath", makeRelativeToOebps(NAV_XHTML_PATH));
        model.put("stylesheetCssPath", makeRelativeToOebps(STYLESHEET_CSS_PATH));
        model.put("firstPageId", contentGroups.isEmpty() ? "" : "page_" + contentGroups.get(0).contentKey());
        
        String contentOpf = processTemplate("xml/content.opf.ftl", model);
        
        ZipArchiveEntry contentEntry = new ZipArchiveEntry(CONTENT_OPF_PATH);
        zipOut.putArchiveEntry(contentEntry);
        zipOut.write(contentOpf.getBytes(StandardCharsets.UTF_8));
        zipOut.closeArchiveEntry();
    }

    private void addTocNcx(ZipArchiveOutputStream zipOut, BookEntity bookEntity, 
                          List<EpubContentFileGroup> contentGroups) throws IOException, TemplateException {
        
        Map<String, Object> model = createBookMetadataModel(bookEntity);
        model.put("contentFileGroups", contentGroups);
        
        String tocNcx = processTemplate("xml/toc.xml.ftl", model);
        
        ZipArchiveEntry tocEntry = new ZipArchiveEntry(TOC_NCX_PATH);
        zipOut.putArchiveEntry(tocEntry);
        zipOut.write(tocNcx.getBytes(StandardCharsets.UTF_8));
        zipOut.closeArchiveEntry();
    }

    private void addNavXhtml(ZipArchiveOutputStream zipOut, BookEntity bookEntity, 
                            List<EpubContentFileGroup> contentGroups) throws IOException, TemplateException {
        
        Map<String, Object> model = createBookMetadataModel(bookEntity);
        model.put("contentFileGroups", contentGroups);
        
        String navXhtml = processTemplate("xml/nav.xhtml.ftl", model);
        
        ZipArchiveEntry navEntry = new ZipArchiveEntry(NAV_XHTML_PATH);
        zipOut.putArchiveEntry(navEntry);
        zipOut.write(navXhtml.getBytes(StandardCharsets.UTF_8));
        zipOut.closeArchiveEntry();
    }

    private Map<String, Object> createBookMetadataModel(BookEntity bookEntity) {
        Map<String, Object> model = new HashMap<>();
        
        if (bookEntity != null && bookEntity.getMetadata() != null) {
            var metadata = bookEntity.getMetadata();
            
            model.put("title", metadata.getTitle() != null ? metadata.getTitle() : "Unknown Comic");
            model.put("language", metadata.getLanguage() != null ? metadata.getLanguage() : "en");
            
            if (metadata.getSubtitle() != null && !metadata.getSubtitle().trim().isEmpty()) {
                model.put("subtitle", metadata.getSubtitle());
            }
            if (metadata.getDescription() != null && !metadata.getDescription().trim().isEmpty()) {
                model.put("description", metadata.getDescription());
            }
            
            if (metadata.getSeriesName() != null && !metadata.getSeriesName().trim().isEmpty()) {
                model.put("seriesName", metadata.getSeriesName());
            }
            if (metadata.getSeriesNumber() != null) {
                model.put("seriesNumber", metadata.getSeriesNumber());
            }
            if (metadata.getSeriesTotal() != null) {
                model.put("seriesTotal", metadata.getSeriesTotal());
            }
            
            if (metadata.getPublisher() != null && !metadata.getPublisher().trim().isEmpty()) {
                model.put("publisher", metadata.getPublisher());
            }
            if (metadata.getPublishedDate() != null) {
                model.put("publishedDate", metadata.getPublishedDate().toString());
            }
            if (metadata.getPageCount() != null && metadata.getPageCount() > 0) {
                model.put("pageCount", metadata.getPageCount());
            }
            
            if (metadata.getIsbn13() != null && !metadata.getIsbn13().trim().isEmpty()) {
                model.put("isbn13", metadata.getIsbn13());
            }
            if (metadata.getIsbn10() != null && !metadata.getIsbn10().trim().isEmpty()) {
                model.put("isbn10", metadata.getIsbn10());
            }
            if (metadata.getAsin() != null && !metadata.getAsin().trim().isEmpty()) {
                model.put("asin", metadata.getAsin());
            }
            if (metadata.getGoodreadsId() != null && !metadata.getGoodreadsId().trim().isEmpty()) {
                model.put("goodreadsId", metadata.getGoodreadsId());
            }
            
            if (metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()) {
                model.put("authors", metadata.getAuthors().stream()
                        .map(author -> author.getName())
                        .toList());
            }
            
            if (metadata.getCategories() != null && !metadata.getCategories().isEmpty()) {
                model.put("categories", metadata.getCategories().stream()
                        .map(category -> category.getName())
                        .toList());
            }
            
            if (metadata.getTags() != null && !metadata.getTags().isEmpty()) {
                model.put("tags", metadata.getTags().stream()
                        .map(tag -> tag.getName())
                        .toList());
            }
            
            model.put("identifier", "urn:uuid:" + UUID.randomUUID());
        } else {
            model.put("title", "Unknown Comic");
            model.put("language", "en");
            model.put("identifier", "urn:uuid:" + UUID.randomUUID());
        }
        
        model.put("modified", Instant.now().toString());
        
        return model;
    }

    private String processTemplate(String templateName, Map<String, Object> model) 
            throws IOException, TemplateException {
        try {
            Template template = freemarkerConfig.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(model, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new IOException("Failed to load template: " + templateName, e);
        } catch (TemplateException e) {
            throw new TemplateException("Failed to process template: " + templateName, e, null);
        }
    }

    private String loadResourceAsString(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String makeRelativeToOebps(String fullPath) {
        Path oebpsPath = Paths.get("OEBPS");
        Path targetPath = Paths.get(fullPath);
        
        if (targetPath.startsWith(oebpsPath)) {
            return oebpsPath.relativize(targetPath).toString();
        }
        
        return fullPath;
    }

    private long calculateCrc32(byte[] data) {
        java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
        crc32.update(data);
        return crc32.getValue();
    }

    public boolean isSupportedCbxFormat(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".cbz") || 
               lowerName.endsWith(".cbr") || 
               lowerName.endsWith(".cb7");
    }

}
