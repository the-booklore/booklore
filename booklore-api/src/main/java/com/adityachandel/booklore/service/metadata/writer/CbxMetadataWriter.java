package com.adityachandel.booklore.service.metadata.writer;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.ArchiveUtils;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class CbxMetadataWriter implements MetadataWriter {

    private static final Pattern VALID_FILENAME_PATTERN = Pattern.compile("^[\\w./\\\\-]+$");
    private static final int BUFFER_SIZE = 8192;

    private final AppSettingService appSettingService;

    @Override
    public void saveMetadataToFile(File file, BookMetadataEntity metadata, String thumbnailUrl, MetadataClearFlags clearFlags) {
        if (!shouldSaveMetadataToFile(file)) {
            return;
        }

        ArchiveUtils.ArchiveType type = ArchiveUtils.detectArchiveType(file);
        boolean isCbz = type == ArchiveUtils.ArchiveType.ZIP;
        boolean isCbr = type == ArchiveUtils.ArchiveType.RAR;
        boolean isCb7 = type == ArchiveUtils.ArchiveType.SEVEN_ZIP;

        if (type == ArchiveUtils.ArchiveType.UNKNOWN) {
            log.warn("Unsupported file type for CBX writer: {}", file.getName());
            return;
        }

        Path backupPath = createBackupFile(file);
        Path extractDir = null;
        Path tempArchive = null;
        boolean writeSucceeded = false;

        try {
            Document xmlDoc = loadOrCreateComicInfoXml(file, isCbz, isCb7, isCbr);
            applyMetadataChanges(xmlDoc, metadata, clearFlags);
            byte[] xmlContent = convertDocumentToBytes(xmlDoc);

            if (isCbz) {
                tempArchive = updateZipArchive(file, xmlContent);
                writeSucceeded = true;
            } else if (isCb7) {
                tempArchive = convert7zToZip(file, xmlContent);
                writeSucceeded = true;
            } else {
                tempArchive = updateRarArchive(file, xmlContent, extractDir);
                writeSucceeded = true;
            }
        } catch (Exception e) {
            restoreOriginalFile(backupPath, file);
            log.warn("Failed to write metadata for {}: {}", file.getName(), e.getMessage(), e);
        } finally {
            cleanupTempFiles(tempArchive, extractDir, backupPath, writeSucceeded);
        }
    }

    public boolean shouldSaveMetadataToFile(File file) {
        MetadataPersistenceSettings.SaveToOriginalFile settings = appSettingService.getAppSettings().getMetadataPersistenceSettings().getSaveToOriginalFile();

        MetadataPersistenceSettings.FormatSettings cbxSettings = settings.getCbx();
        if (cbxSettings == null || !cbxSettings.isEnabled()) {
            log.debug("CBX metadata writing is disabled. Skipping: {}", file.getName());
            return false;
        }

        long fileSizeInMb = file.length() / (1024 * 1024);
        if (fileSizeInMb > cbxSettings.getMaxFileSizeInMb()) {
            log.info("CBX file {} ({} MB) exceeds max size limit ({} MB). Skipping metadata write.", file.getName(), fileSizeInMb, cbxSettings.getMaxFileSizeInMb());
            return false;
        }

        return true;
    }

    private Path createBackupFile(File file) {
        try {
            Path backupPath = Files.createTempFile(file.getParentFile().toPath(), "cbx_backup_", ".bak");
            Files.copy(file.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);
            return backupPath;
        } catch (Exception ex) {
            log.warn("Unable to create backup for {}: {}", file.getAbsolutePath(), ex.getMessage(), ex);
            return null;
        }
    }

    private Document loadOrCreateComicInfoXml(File file, boolean isCbz, boolean isCb7, boolean isCbr) throws Exception {
        if (isCbz) {
            return loadXmlFromZip(file);
        } else if (isCb7) {
            return loadXmlFrom7z(file);
        } else {
            return loadXmlFromRar(file);
        }
    }

    private Document loadXmlFromZip(File file) throws Exception {
        try (ZipFile zipFile = new ZipFile(file)) {
            ZipEntry xmlEntry = findComicInfoEntry(zipFile);
            if (xmlEntry != null) {
                try (InputStream stream = zipFile.getInputStream(xmlEntry)) {
                    return parseXmlSecurely(stream);
                }
            }
            return createEmptyComicInfoXml();
        }
    }

    private Document loadXmlFrom7z(File file) throws Exception {
        try (SevenZFile archive = SevenZFile.builder().setFile(file).get()) {
            SevenZArchiveEntry xmlEntry = findComicInfoIn7z(archive);
            if (xmlEntry != null) {
                try (InputStream stream = archive.getInputStream(xmlEntry)) {
                    return parseXmlSecurely(stream);
                }
            }
            return createEmptyComicInfoXml();
        }
    }

    private SevenZArchiveEntry findComicInfoIn7z(SevenZFile archive) {
        for (SevenZArchiveEntry entry : archive.getEntries()) {
            if (entry != null && !entry.isDirectory() && isComicInfoXml(entry.getName())) {
                return entry;
            }
        }
        return null;
    }

    private Document loadXmlFromRar(File file) throws Exception {
        try (Archive archive = new Archive(file)) {
            FileHeader xmlHeader = findComicInfoInRar(archive);
            if (xmlHeader != null) {
                try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    extractRarEntry(archive, xmlHeader, buffer);
                    try (InputStream stream = new ByteArrayInputStream(buffer.toByteArray())) {
                        return parseXmlSecurely(stream);
                    }
                }
            }
            return createEmptyComicInfoXml();
        }
    }

    private void applyMetadataChanges(Document xmlDoc, BookMetadataEntity metadata, MetadataClearFlags clearFlags) {
        Element rootElement = xmlDoc.getDocumentElement();
        MetadataCopyHelper helper = new MetadataCopyHelper(metadata);

        helper.copyTitle(clearFlags != null && clearFlags.isTitle(), val -> updateXmlElement(xmlDoc, rootElement, "Title", val));
        helper.copyDescription(clearFlags != null && clearFlags.isDescription(), val -> {
            updateXmlElement(xmlDoc, rootElement, "Summary", val);
            removeXmlElement(rootElement, "Description");
        });
        helper.copyPublisher(clearFlags != null && clearFlags.isPublisher(), val -> updateXmlElement(xmlDoc, rootElement, "Publisher", val));
        helper.copySeriesName(clearFlags != null && clearFlags.isSeriesName(), val -> updateXmlElement(xmlDoc, rootElement, "Series", val));
        helper.copySeriesNumber(clearFlags != null && clearFlags.isSeriesNumber(), val -> updateXmlElement(xmlDoc, rootElement, "Number", formatFloatValue(val)));
        helper.copySeriesTotal(clearFlags != null && clearFlags.isSeriesTotal(), val -> updateXmlElement(xmlDoc, rootElement, "Count", val != null ? val.toString() : null));
        helper.copyPublishedDate(clearFlags != null && clearFlags.isPublishedDate(), date -> updateDateElements(xmlDoc, rootElement, date));
        helper.copyPageCount(clearFlags != null && clearFlags.isPageCount(), val -> updateXmlElement(xmlDoc, rootElement, "PageCount", val != null ? val.toString() : null));
        helper.copyLanguage(clearFlags != null && clearFlags.isLanguage(), val -> updateXmlElement(xmlDoc, rootElement, "LanguageISO", val));
        helper.copyAuthors(clearFlags != null && clearFlags.isAuthors(), set -> {
            updateXmlElement(xmlDoc, rootElement, "Writer", joinStrings(set));
            removeXmlElement(rootElement, "Penciller");
            removeXmlElement(rootElement, "Inker");
            removeXmlElement(rootElement, "Colorist");
            removeXmlElement(rootElement, "Letterer");
            removeXmlElement(rootElement, "CoverArtist");
        });
        helper.copyCategories(clearFlags != null && clearFlags.isCategories(), set -> {
            updateXmlElement(xmlDoc, rootElement, "Genre", joinStrings(set));
            removeXmlElement(rootElement, "Tags");
        });
    }

    private byte[] convertDocumentToBytes(Document xmlDoc) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(xmlDoc), new StreamResult(outputStream));
        return outputStream.toByteArray();
    }

    private Path updateZipArchive(File originalFile, byte[] xmlContent) throws Exception {
        Path tempArchive = Files.createTempFile("cbx_edit", ".cbz");
        rebuildZipWithNewXml(originalFile.toPath(), tempArchive, xmlContent);
        replaceFileAtomic(tempArchive, originalFile.toPath());
        return null; // temp file already moved
    }

    private Path convert7zToZip(File original7z, byte[] xmlContent) throws Exception {
        Path tempZip = Files.createTempFile("cbx_edit", ".cbz");
        repack7zToZipWithXml(original7z, tempZip, xmlContent);

        Path targetPath = original7z.toPath().resolveSibling(removeFileExtension(original7z.getName()) + ".cbz");
        replaceFileAtomic(tempZip, targetPath);

        try {
            Files.deleteIfExists(original7z.toPath());
        } catch (Exception ignored) {
        }
        return null;
    }

    private void repack7zToZipWithXml(File source7z, Path targetZip, byte[] xmlContent) throws Exception {
        try (SevenZFile archive = SevenZFile.builder().setFile(source7z).get();
             ZipOutputStream zipOutput = new ZipOutputStream(Files.newOutputStream(targetZip))) {

            for (SevenZArchiveEntry entry : archive.getEntries()) {
                if (entry.isDirectory()) continue;
                String entryName = entry.getName();
                if (isComicInfoXml(entryName)) continue;
                if (!isPathSafe(entryName)) {
                    log.warn("Skipping unsafe 7z entry name: {}", entryName);
                    continue;
                }

                zipOutput.putNextEntry(new ZipEntry(entryName));
                try (InputStream entryStream = archive.getInputStream(entry)) {
                    if (entryStream != null) copyStream(entryStream, zipOutput);
                }
                zipOutput.closeEntry();
            }

            zipOutput.putNextEntry(new ZipEntry("ComicInfo.xml"));
            zipOutput.write(xmlContent);
            zipOutput.closeEntry();
        }
    }

    private Path updateRarArchive(File originalRar, byte[] xmlContent, Path extractDir) throws Exception {
        String rarCommand = System.getenv().getOrDefault("BOOKLORE_RAR_BIN", "rar");
        boolean rarAvailable = checkRarAvailability(rarCommand);

        if (rarAvailable) {
            return updateRarWithCommand(originalRar, xmlContent, rarCommand, extractDir);
        } else {
            log.warn("`rar` binary not found. Falling back to CBZ conversion for {}", originalRar.getName());
            return convertRarToZipArchive(originalRar, xmlContent);
        }
    }

    private Path updateRarWithCommand(File originalRar, byte[] xmlContent, String rarCommand, Path extractDir) throws Exception {
        extractDir = Files.createTempDirectory("cbx_rar_");
        extractRarContents(originalRar, extractDir);

        Path xmlPath = extractDir.resolve("ComicInfo.xml");
        Files.write(xmlPath, xmlContent);

        Path targetRar = originalRar.toPath().toAbsolutePath().normalize();
        String safeCommand = isExecutableSafe(rarCommand) ? rarCommand : "rar";
        ProcessBuilder processBuilder = new ProcessBuilder(safeCommand, "a", "-idq", "-ep1", "-ma5", targetRar.toString(), ".");
        processBuilder.directory(extractDir.toFile());
        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            return null;
        } else {
            log.warn("RAR creation failed with exit code {}. Falling back to CBZ conversion for {}", exitCode, originalRar.getName());
            return convertRarToZipArchive(originalRar, xmlContent);
        }
    }

    private void extractRarContents(File rarFile, Path targetDir) throws Exception {
        try (Archive archive = new Archive(rarFile)) {
            for (FileHeader header : archive.getFileHeaders()) {
                String entryName = header.getFileName();
                if (entryName == null || entryName.isBlank()) continue;
                if (!isPathSafe(entryName)) {
                    log.warn("Skipping unsafe RAR entry name: {}", entryName);
                    continue;
                }

                Path outputPath = targetDir.resolve(entryName).normalize();
                if (!outputPath.startsWith(targetDir)) {
                    log.warn("Skipping traversal entry outside tempDir: {}", entryName);
                    continue;
                }

                if (header.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    try (OutputStream fileOutput = Files.newOutputStream(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        extractRarEntry(archive, header, fileOutput);
                    }
                }
            }
        }
    }

    private Path convertRarToZipArchive(File rarFile, byte[] xmlContent) throws Exception {
        Path tempZip = Files.createTempFile("cbx_edit", ".cbz");

        try (Archive rarArchive = new Archive(rarFile);
             ZipOutputStream zipOutput = new ZipOutputStream(Files.newOutputStream(tempZip))) {

            for (FileHeader header : rarArchive.getFileHeaders()) {
                if (header.isDirectory()) continue;
                String entryName = header.getFileName();
                if (isComicInfoXml(entryName)) continue;
                if (!isPathSafe(entryName)) {
                    log.warn("Skipping unsafe RAR entry name: {}", entryName);
                    continue;
                }

                zipOutput.putNextEntry(new ZipEntry(entryName));
                extractRarEntry(rarArchive, header, zipOutput);
                zipOutput.closeEntry();
            }

            zipOutput.putNextEntry(new ZipEntry("ComicInfo.xml"));
            zipOutput.write(xmlContent);
            zipOutput.closeEntry();
        }

        Path targetPath = rarFile.toPath().resolveSibling(removeFileExtension(rarFile.getName()) + ".cbz");
        replaceFileAtomic(tempZip, targetPath);

        try {
            Files.deleteIfExists(rarFile.toPath());
        } catch (Exception ignored) {
        }

        return null;
    }

    private void restoreOriginalFile(Path backupPath, File targetFile) {
        try {
            if (backupPath != null) {
                Files.copy(backupPath, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Restored original file from backup after failure: {}", targetFile.getAbsolutePath());
            }
        } catch (Exception restoreException) {
            log.warn("Failed to restore original file from backup: {} -> {}", backupPath, targetFile.getAbsolutePath(), restoreException);
        }
    }

    private void cleanupTempFiles(Path tempArchive, Path extractDir, Path backupPath, boolean writeSucceeded) {
        if (tempArchive != null) {
            try {
                Files.deleteIfExists(tempArchive);
            } catch (Exception e) {
                log.warn("Failed to delete temp file: {}", tempArchive, e);
            }
        }

        if (extractDir != null) {
            deleteDirectoryRecursively(extractDir);
        }

        if (writeSucceeded && backupPath != null) {
            try {
                Files.deleteIfExists(backupPath);
            } catch (Exception e) {
                log.warn("Failed to delete backup file: {}", backupPath, e);
            }
        }
    }

    private ZipEntry findComicInfoEntry(ZipFile zipFile) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (isComicInfoXml(entryName)) return entry;
        }
        return null;
    }

    private FileHeader findComicInfoInRar(Archive archive) {
        for (FileHeader header : archive.getFileHeaders()) {
            String entryName = header.getFileName();
            if (entryName != null && isComicInfoXml(entryName)) return header;
        }
        return null;
    }

    private Document parseXmlSecurely(InputStream xmlStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(xmlStream);
    }

    private Document createEmptyComicInfoXml() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document xmlDoc = builder.newDocument();
        xmlDoc.appendChild(xmlDoc.createElement("ComicInfo"));
        return xmlDoc;
    }

    private void updateXmlElement(Document xmlDoc, Element rootElement, String tagName, String value) {
        removeXmlElement(rootElement, tagName);
        if (value != null && !value.isBlank()) {
            Element element = xmlDoc.createElement(tagName);
            element.setTextContent(value);
            rootElement.appendChild(element);
        }
    }

    private void removeXmlElement(Element rootElement, String tagName) {
        NodeList nodes = rootElement.getElementsByTagName(tagName);
        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            rootElement.removeChild(nodes.item(i));
        }
    }

    private void updateDateElements(Document xmlDoc, Element rootElement, LocalDate date) {
        if (date == null) {
            removeXmlElement(rootElement, "Year");
            removeXmlElement(rootElement, "Month");
            removeXmlElement(rootElement, "Day");
            return;
        }
        updateXmlElement(xmlDoc, rootElement, "Year", Integer.toString(date.getYear()));
        updateXmlElement(xmlDoc, rootElement, "Month", Integer.toString(date.getMonthValue()));
        updateXmlElement(xmlDoc, rootElement, "Day", Integer.toString(date.getDayOfMonth()));
    }

    private String joinStrings(Set<String> values) {
        return (values == null || values.isEmpty()) ? null : String.join(", ", values);
    }

    private String formatFloatValue(Float value) {
        if (value == null) return null;
        if (value % 1 == 0) return Integer.toString(value.intValue());
        return value.toString();
    }

    private static boolean isComicInfoXml(String entryName) {
        if (entryName == null) return false;
        String normalized = entryName.replace('\\', '/');
        if (normalized.endsWith("/")) return false;
        String lowerCase = normalized.toLowerCase(Locale.ROOT);
        return "comicinfo.xml".equals(lowerCase) || lowerCase.endsWith("/comicinfo.xml");
    }

    private static boolean isPathSafe(String entryName) {
        if (entryName == null || entryName.isBlank()) return false;
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/")) return false;
        if (normalized.contains("../")) return false;
        if (normalized.contains("\0")) return false;
        return true;
    }

    private void rebuildZipWithNewXml(Path sourceZip, Path targetZip, byte[] xmlContent) throws Exception {
        try (ZipFile zipFile = new ZipFile(sourceZip.toFile());
             ZipOutputStream zipOutput = new ZipOutputStream(Files.newOutputStream(targetZip))) {
            ZipEntry existingXml = findComicInfoEntry(zipFile);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (existingXml != null && entryName.equals(existingXml.getName())) {
                    continue;
                }
                if (!isPathSafe(entryName)) {
                    log.warn("Skipping unsafe ZIP entry name: {}", entryName);
                    continue;
                }
                zipOutput.putNextEntry(new ZipEntry(entryName));
                try (InputStream entryStream = zipFile.getInputStream(entry)) {
                    copyStream(entryStream, zipOutput);
                }
                zipOutput.closeEntry();
            }
            String xmlEntryName = (existingXml != null ? existingXml.getName() : "ComicInfo.xml");
            zipOutput.putNextEntry(new ZipEntry(xmlEntryName));
            zipOutput.write(xmlContent);
            zipOutput.closeEntry();
        }
    }

    private void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    private void extractRarEntry(Archive archive, FileHeader fileHeader, OutputStream output) throws Exception {
        try (InputStream entryStream = archive.getInputStream(fileHeader)) {
            copyStream(entryStream, output);
        }
    }

    private static void replaceFileAtomic(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean checkRarAvailability(String rarCommand) {
        try {
            String safeCommand = isExecutableSafe(rarCommand) ? rarCommand : "rar";
            Process check = new ProcessBuilder(safeCommand, "--help").redirectErrorStream(true).start();
            int exitCode = check.waitFor();
            return (exitCode == 0);
        } catch (Exception ex) {
            log.warn("RAR binary check failed: {}", ex.getMessage());
            return false;
        }
    }

    private boolean isExecutableSafe(String command) {
        if (command == null || command.isBlank()) return false;
        return VALID_FILENAME_PATTERN.matcher(command).matches();
    }

    private static String removeFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) return filename.substring(0, lastDot);
        return filename;
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.CBX;
    }

    private void deleteDirectoryRecursively(Path directory) {
        try (var pathStream = Files.walk(directory)) {
            pathStream
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete temp file/directory: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to clean up temporary directory: {}", directory, e);
        }
    }
}