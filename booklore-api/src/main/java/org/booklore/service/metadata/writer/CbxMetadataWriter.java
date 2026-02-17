package org.booklore.service.metadata.writer;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.ArchiveUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class CbxMetadataWriter implements MetadataWriter {

    private static final Pattern VALID_FILENAME_PATTERN = Pattern.compile("^[\\w./\\\\-]+$");
    private static final int BUFFER_SIZE = 8192;

    // Cache JAXBContext for performance
    private static final JAXBContext JAXB_CONTEXT;

    static {
        // XXE protection: Disable external DTD and Schema access for security
        System.setProperty("javax.xml.accessExternalDTD", "");
        System.setProperty("javax.xml.accessExternalSchema", "");
        
        try {
            JAXB_CONTEXT = JAXBContext.newInstance(ComicInfo.class);
        } catch (jakarta.xml.bind.JAXBException e) {
            throw new RuntimeException("Failed to initialize JAXB Context", e);
        }
    }

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
            log.warn("Unknown archive type for file: {}", file.getName());
            return;
        }

        Path backupPath = createBackupFile(file);
        Path extractDir = null;
        Path tempArchive = null;
        boolean writeSucceeded = false;

        try {
            ComicInfo comicInfo = loadOrCreateComicInfo(file, isCbz, isCb7, isCbr);
            applyMetadataChanges(comicInfo, metadata, clearFlags);
            byte[] xmlContent = convertToBytes(comicInfo);

            if (isCbz) {
                log.debug("CbxMetadataWriter: Writing ComicInfo.xml to CBZ file: {}, XML size: {} bytes", file.getName(), xmlContent.length);
                tempArchive = updateZipArchive(file, xmlContent);
                writeSucceeded = true;
                log.info("CbxMetadataWriter: Successfully wrote metadata to CBZ file: {}", file.getName());
            } else if (isCb7) {
                log.debug("CbxMetadataWriter: Converting CB7 to CBZ and writing ComicInfo.xml: {}", file.getName());
                tempArchive = convert7zToZip(file, xmlContent);
                writeSucceeded = true;
                log.info("CbxMetadataWriter: Successfully converted CB7 to CBZ and wrote metadata: {}", file.getName());
            } else {
                log.debug("CbxMetadataWriter: Writing ComicInfo.xml to RAR file: {}", file.getName());
                tempArchive = updateRarArchive(file, xmlContent, extractDir);
                writeSucceeded = true;
                log.info("CbxMetadataWriter: Successfully wrote metadata to RAR/CBZ file: {}", file.getName());
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

    private ComicInfo loadOrCreateComicInfo(File file, boolean isCbz, boolean isCb7, boolean isCbr) throws Exception {
        if (isCbz) {
            return loadFromZip(file);
        } else if (isCb7) {
            return loadFrom7z(file);
        } else {
            return loadFromRar(file);
        }
    }

    private ComicInfo loadFromZip(File file) throws Exception {
        try (ZipFile zipFile = new ZipFile(file)) {
            ZipEntry xmlEntry = findComicInfoEntry(zipFile);
            if (xmlEntry != null) {
                try (InputStream stream = zipFile.getInputStream(xmlEntry)) {
                    return parseComicInfo(stream);
                }
            }
            return new ComicInfo();
        }
    }

    private ComicInfo loadFrom7z(File file) throws Exception {
        try (SevenZFile archive = SevenZFile.builder().setFile(file).get()) {
            SevenZArchiveEntry xmlEntry = findComicInfoIn7z(archive);
            if (xmlEntry != null) {
                try (InputStream stream = archive.getInputStream(xmlEntry)) {
                    return parseComicInfo(stream);
                }
            }
            return new ComicInfo();
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

    private ComicInfo loadFromRar(File file) throws Exception {
        try (Archive archive = new Archive(file)) {
            FileHeader xmlHeader = findComicInfoInRar(archive);
            if (xmlHeader != null) {
                try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    extractRarEntry(archive, xmlHeader, buffer);
                    try (InputStream stream = new ByteArrayInputStream(buffer.toByteArray())) {
                        return parseComicInfo(stream);
                    }
                }
            }
            return new ComicInfo();
        }
    }

    private void applyMetadataChanges(ComicInfo info, BookMetadataEntity metadata, MetadataClearFlags clearFlags) {
        MetadataCopyHelper helper = new MetadataCopyHelper(metadata);

        helper.copyTitle(clearFlags != null && clearFlags.isTitle(), info::setTitle);
        
        // Summary: Remove HTML tags safely using Jsoup (handles complex HTML like attributes with '>')
        helper.copyDescription(clearFlags != null && clearFlags.isDescription(), val -> {
            if (val != null) {
                // Jsoup.clean with Safelist.none() removes all HTML tags safely, 
                // handling edge cases like '<a href="...>">' that regex fails on
                String clean = Jsoup.clean(val, Safelist.none()).trim();
                log.debug("CbxMetadataWriter: Setting Summary to: {} (original length: {}, cleaned length: {})", 
                    clean.length() > 50 ? clean.substring(0, 50) + "..." : clean, 
                    val.length(), 
                    clean.length());
                info.setSummary(clean);
            } else {
                log.debug("CbxMetadataWriter: Clearing Summary (null description)");
                info.setSummary(null);
            }
        });
        
        helper.copyPublisher(clearFlags != null && clearFlags.isPublisher(), info::setPublisher);
        helper.copySeriesName(clearFlags != null && clearFlags.isSeriesName(), info::setSeries);
        helper.copySeriesNumber(clearFlags != null && clearFlags.isSeriesNumber(), val -> info.setNumber(formatFloatValue(val)));
        helper.copySeriesTotal(clearFlags != null && clearFlags.isSeriesTotal(), info::setCount);
        
        helper.copyPublishedDate(clearFlags != null && clearFlags.isPublishedDate(), date -> {
             if (date != null) {
                 info.setYear(date.getYear());
                 info.setMonth(date.getMonthValue());
                 info.setDay(date.getDayOfMonth());
             } else {
                 info.setYear(null);
                 info.setMonth(null);
                 info.setDay(null);
             }
        });
        
        helper.copyPageCount(clearFlags != null && clearFlags.isPageCount(), info::setPageCount);
        helper.copyLanguage(clearFlags != null && clearFlags.isLanguage(), info::setLanguageISO);
        
        helper.copyAuthors(clearFlags != null && clearFlags.isAuthors(), set -> {
            info.setWriter(joinStrings(set));
            info.setPenciller(null);
            info.setInker(null);
            info.setColorist(null);
            info.setLetterer(null);
            info.setCoverArtist(null);
        });

        // Genre - categories
        helper.copyCategories(clearFlags != null && clearFlags.isCategories(), set -> {
            info.setGenre(joinStrings(set));
        });
        
        // Tags - separate from Genre per Anansi v2.1
        if (metadata.getTags() != null && !metadata.getTags().isEmpty()) {
            info.setTags(joinStrings(metadata.getTags().stream().map(TagEntity::getName).collect(Collectors.toSet())));
        }

        // CommunityRating - normalized to 0-5 scale
        helper.copyRating(false, rating -> {
            if (rating != null) {
                double normalized = Math.min(5.0, Math.max(0.0, rating / 2.0));
                info.setCommunityRating(String.format(Locale.US, "%.1f", normalized));
            } else {
                info.setCommunityRating(null);
            }
        });

        // Web field - pick one primary
        String primaryUrl = null;
        if (metadata.getHardcoverBookId() != null && !metadata.getHardcoverBookId().isBlank()) {
            primaryUrl = "https://hardcover.app/books/" + metadata.getHardcoverBookId();
        } else if (metadata.getComicvineId() != null && !metadata.getComicvineId().isBlank()) {
            primaryUrl = "https://comicvine.gamespot.com/issue/" + metadata.getComicvineId();
        } else if (metadata.getGoodreadsId() != null && !metadata.getGoodreadsId().isBlank()) {
            primaryUrl = "https://www.goodreads.com/book/show/" + metadata.getGoodreadsId();
        } else if (metadata.getAsin() != null && !metadata.getAsin().isBlank()) {
            primaryUrl = "https://www.amazon.com/dp/" + metadata.getAsin();
        }
        info.setWeb(primaryUrl);

        // Notes - Custom Metadata
        StringBuilder notesBuilder = new StringBuilder();
        String existingNotes = info.getNotes();
        
        // Preserve existing notes that don't start with [BookLore
        if (existingNotes != null && !existingNotes.isBlank()) {
            String preservedRules = existingNotes.lines()
                    .map(String::trim)
                    .filter(line -> !line.startsWith("[BookLore:") && !line.startsWith("[BookLore]"))
                    .collect(Collectors.joining("\n"));
             if (!preservedRules.isEmpty()) {
                 notesBuilder.append(preservedRules);
             }
        }

        if (metadata.getMoods() != null) {
            appendBookLoreTag(notesBuilder, "Moods", joinStrings(metadata.getMoods().stream().map(MoodEntity::getName).collect(Collectors.toSet())));
        }
        if (metadata.getTags() != null) {
            appendBookLoreTag(notesBuilder, "Tags", joinStrings(metadata.getTags().stream().map(TagEntity::getName).collect(Collectors.toSet())));
        }
        appendBookLoreTag(notesBuilder, "Subtitle", metadata.getSubtitle());
        
        if (metadata.getIsbn13() != null && !metadata.getIsbn13().isBlank()) {
            info.setGtin(metadata.getIsbn13());
        }
        appendBookLoreTag(notesBuilder, "ISBN10", metadata.getIsbn10());
        
        appendBookLoreTag(notesBuilder, "AmazonRating", metadata.getAmazonRating());
        appendBookLoreTag(notesBuilder, "GoodreadsRating", metadata.getGoodreadsRating());
        appendBookLoreTag(notesBuilder, "HardcoverRating", metadata.getHardcoverRating());
        appendBookLoreTag(notesBuilder, "LubimyczytacRating", metadata.getLubimyczytacRating());
        appendBookLoreTag(notesBuilder, "RanobedbRating", metadata.getRanobedbRating());

        appendBookLoreTag(notesBuilder, "HardcoverBookId", metadata.getHardcoverBookId());
        appendBookLoreTag(notesBuilder, "HardcoverId", metadata.getHardcoverId());
        appendBookLoreTag(notesBuilder, "LubimyczytacId", metadata.getLubimyczytacId());
        appendBookLoreTag(notesBuilder, "RanobedbId", metadata.getRanobedbId());
        appendBookLoreTag(notesBuilder, "GoogleId", metadata.getGoogleId());
        appendBookLoreTag(notesBuilder, "GoodreadsId", metadata.getGoodreadsId());
        appendBookLoreTag(notesBuilder, "ASIN", metadata.getAsin());
        appendBookLoreTag(notesBuilder, "ComicvineId", metadata.getComicvineId());
        
        // Comic-specific metadata from ComicMetadataEntity
        ComicMetadataEntity comic = metadata.getComicMetadata();
        if (comic != null) {
            // Volume
            if (comic.getVolumeNumber() != null) {
                info.setVolume(comic.getVolumeNumber());
            }
            
            // Alternate Series
            if (comic.getAlternateSeries() != null && !comic.getAlternateSeries().isBlank()) {
                info.setAlternateSeries(comic.getAlternateSeries());
            }
            if (comic.getAlternateIssue() != null && !comic.getAlternateIssue().isBlank()) {
                info.setAlternateNumber(comic.getAlternateIssue());
            }
            
            // Story Arc
            if (comic.getStoryArc() != null && !comic.getStoryArc().isBlank()) {
                info.setStoryArc(comic.getStoryArc());
            }
            
            // Format
            if (comic.getFormat() != null && !comic.getFormat().isBlank()) {
                info.setFormat(comic.getFormat());
            }
            
            // Imprint
            if (comic.getImprint() != null && !comic.getImprint().isBlank()) {
                info.setImprint(comic.getImprint());
            }
            
            // BlackAndWhite (Yes/No)
            if (comic.getBlackAndWhite() != null) {
                info.setBlackAndWhite(comic.getBlackAndWhite() ? "Yes" : "No");
            }
            
            // Manga / Reading Direction
            if (comic.getManga() != null && comic.getManga()) {
                if (comic.getReadingDirection() != null && "RTL".equalsIgnoreCase(comic.getReadingDirection())) {
                    info.setManga("YesAndRightToLeft");
                } else {
                    info.setManga("Yes");
                }
            } else if (comic.getManga() != null) {
                info.setManga("No");
            }
            
            // Characters (comma-separated)
            if (comic.getCharacters() != null && !comic.getCharacters().isEmpty()) {
                String chars = comic.getCharacters().stream()
                        .map(ComicCharacterEntity::getName)
                        .collect(Collectors.joining(", "));
                info.setCharacters(chars);
            }
            
            // Teams (comma-separated)
            if (comic.getTeams() != null && !comic.getTeams().isEmpty()) {
                String teams = comic.getTeams().stream()
                        .map(ComicTeamEntity::getName)
                        .collect(Collectors.joining(", "));
                info.setTeams(teams);
            }
            
            // Locations (comma-separated)
            if (comic.getLocations() != null && !comic.getLocations().isEmpty()) {
                String locs = comic.getLocations().stream()
                        .map(ComicLocationEntity::getName)
                        .collect(Collectors.joining(", "));
                info.setLocations(locs);
            }
            
            // Creators by role (overrides the author-based writer if present)
            if (comic.getCreatorMappings() != null && !comic.getCreatorMappings().isEmpty()) {
                String pencillers = getCreatorsByRole(comic, ComicCreatorRole.PENCILLER);
                String inkers = getCreatorsByRole(comic, ComicCreatorRole.INKER);
                String colorists = getCreatorsByRole(comic, ComicCreatorRole.COLORIST);
                String letterers = getCreatorsByRole(comic, ComicCreatorRole.LETTERER);
                String coverArtists = getCreatorsByRole(comic, ComicCreatorRole.COVER_ARTIST);
                String editors = getCreatorsByRole(comic, ComicCreatorRole.EDITOR);
                
                if (!pencillers.isEmpty()) info.setPenciller(pencillers);
                if (!inkers.isEmpty()) info.setInker(inkers);
                if (!colorists.isEmpty()) info.setColorist(colorists);
                if (!letterers.isEmpty()) info.setLetterer(letterers);
                if (!coverArtists.isEmpty()) info.setCoverArtist(coverArtists);
                if (!editors.isEmpty()) info.setEditor(editors);
            }
            
            // Store comic-specific metadata in notes as well
            appendBookLoreTag(notesBuilder, "VolumeName", comic.getVolumeName());
            appendBookLoreTag(notesBuilder, "StoryArcNumber", comic.getStoryArcNumber());
            appendBookLoreTag(notesBuilder, "IssueNumber", comic.getIssueNumber());
        }
        
        // Age Rating (from BookMetadataEntity - mapped to ComicInfo AgeRating format)
        if (metadata.getAgeRating() != null) {
            info.setAgeRating(mapAgeRatingToComicInfo(metadata.getAgeRating()));
        }
        
        info.setNotes(notesBuilder.length() > 0 ? notesBuilder.toString() : null);
    }
    
    private String getCreatorsByRole(ComicMetadataEntity comic, ComicCreatorRole role) {
        if (comic.getCreatorMappings() == null) return "";
        return comic.getCreatorMappings().stream()
                .filter(m -> m.getRole() == role)
                .map(m -> m.getCreator().getName())
                .collect(Collectors.joining(", "));
    }
    
    private String mapAgeRatingToComicInfo(Integer ageRating) {
        // Map numeric age rating to ComicInfo AgeRating string values
        if (ageRating == null) return null;
        if (ageRating >= 18) return "Adults Only 18+";
        if (ageRating >= 17) return "Mature 17+";
        if (ageRating >= 15) return "MA15+";
        if (ageRating >= 13) return "Teen";
        if (ageRating >= 10) return "Everyone 10+";
        if (ageRating >= 6) return "Everyone";
        return "Early Childhood";
    }

    private ComicInfo parseComicInfo(InputStream xmlStream) throws Exception {
        Unmarshaller unmarshaller = JAXB_CONTEXT.createUnmarshaller();
        unmarshaller.setEventHandler(event -> {
            if (event.getSeverity() == jakarta.xml.bind.ValidationEvent.WARNING || 
                event.getSeverity() == jakarta.xml.bind.ValidationEvent.ERROR) {
                log.warn("JAXB Parsing Issue: {} [Line: {}, Col: {}]", 
                    event.getMessage(), 
                    event.getLocator().getLineNumber(), 
                    event.getLocator().getColumnNumber());
            }
            return true; // Continue processing
        });
        ComicInfo result = (ComicInfo) unmarshaller.unmarshal(xmlStream);
        log.debug("CbxMetadataWriter: Parsed ComicInfo - Title: {}, Summary length: {}", 
            result.getTitle(), 
            result.getSummary() != null ? result.getSummary().length() : 0);
        return result;
    }

    private byte[] convertToBytes(ComicInfo comicInfo) throws Exception {
        Marshaller marshaller = JAXB_CONTEXT.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, false);
        // Ensure 2-space indentation if possible
        try {
            marshaller.setProperty("com.sun.xml.bind.indentString", "  ");
        } catch (Exception ignored) {
            log.debug("Custom indentation property not supported via 'com.sun.xml.bind.indentString'");
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        marshaller.marshal(comicInfo, outputStream);
        return outputStream.toByteArray();
    }

    private Path updateZipArchive(File originalFile, byte[] xmlContent) throws Exception {
        // Create temp file in same directory as original for true atomic move on same filesystem
        Path tempArchive = Files.createTempFile(originalFile.toPath().getParent(), ".cbx_edit_", ".cbz");
        rebuildZipWithNewXml(originalFile.toPath(), tempArchive, xmlContent);
        replaceFileAtomic(tempArchive, originalFile.toPath());
        return null;
    }

    private Path convert7zToZip(File original7z, byte[] xmlContent) throws Exception {
        // Create temp file in same directory as original for true atomic move on same filesystem
        Path tempZip = Files.createTempFile(original7z.toPath().getParent(), ".cbx_edit_", ".cbz");
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
        // Create temp file in same directory as original for true atomic move on same filesystem
        Path tempZip = Files.createTempFile(rarFile.toPath().getParent(), ".cbx_edit_", ".cbz");

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

    private void appendBookLoreTag(StringBuilder sb, String tag, String value) {
        if (value != null && !value.isBlank()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("[BookLore:").append(tag).append("] ").append(value);
        }
    }

    private void appendBookLoreTag(StringBuilder sb, String tag, Number value) {
        if (value != null) {
            if (sb.length() > 0) sb.append("\n");
            String formatted = (value instanceof Double || value instanceof Float) 
                    ? String.format(Locale.US, "%.2f", value.doubleValue())
                    : value.toString();
            sb.append("[BookLore:").append(tag).append("] ").append(formatted);
        }
    }
}