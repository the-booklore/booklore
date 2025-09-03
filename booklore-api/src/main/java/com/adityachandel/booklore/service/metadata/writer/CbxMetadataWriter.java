package com.adityachandel.booklore.service.metadata.writer;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import lombok.extern.slf4j.Slf4j;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

@Slf4j
@Component
public class CbxMetadataWriter implements MetadataWriter {

    @Override
    public void writeMetadataToFile(File file, BookMetadataEntity metadata, String thumbnailUrl, boolean restoreMode, MetadataClearFlags clearFlags) {
        try {
            String nameLower = file.getName().toLowerCase();
            boolean isCbz = nameLower.endsWith(".cbz");
            boolean isCbr = nameLower.endsWith(".cbr");

            if (!isCbz && !isCbr) {
                log.warn("Unsupported file type for CBX writer: {}", file.getName());
                return;
            }

            // Build (or load and update) ComicInfo.xml as a Document
            Document doc;
            if (isCbz) {
                try (ZipFile zipFile = new ZipFile(file)) {
                    ZipEntry existing = findComicInfoEntry(zipFile);
                    if (existing != null) {
                        try (InputStream is = zipFile.getInputStream(existing)) {
                            doc = buildSecureDocument(is);
                        }
                    } else {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        doc = builder.newDocument();
                        doc.appendChild(doc.createElement("ComicInfo"));
                    }
                }
            } else {
                // .cbr: try to read existing ComicInfo.xml if present; otherwise create new doc
                try (Archive archive = new Archive(file)) {
                    FileHeader existing = findComicInfoHeader(archive);
                    if (existing != null) {
                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            archive.extractFile(existing, baos);
                            try (InputStream is = new java.io.ByteArrayInputStream(baos.toByteArray())) {
                                doc = buildSecureDocument(is);
                            }
                        }
                    } else {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        doc = builder.newDocument();
                        doc.appendChild(doc.createElement("ComicInfo"));
                    }
                }
            }

            // Apply metadata to the Document
            Element root = doc.getDocumentElement();
            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            helper.copyTitle(restoreMode, clearFlags != null && clearFlags.isTitle(), val -> setElement(doc, root, "Title", val));
            helper.copyDescription(restoreMode, clearFlags != null && clearFlags.isDescription(), val -> {
                setElement(doc, root, "Summary", val);
                removeElement(root, "Description");
            });
            helper.copyPublisher(restoreMode, clearFlags != null && clearFlags.isPublisher(), val -> setElement(doc, root, "Publisher", val));
            helper.copySeriesName(restoreMode, clearFlags != null && clearFlags.isSeriesName(), val -> setElement(doc, root, "Series", val));
            helper.copySeriesNumber(restoreMode, clearFlags != null && clearFlags.isSeriesNumber(), val -> setElement(doc, root, "Number", formatFloat(val)));
            helper.copySeriesTotal(restoreMode, clearFlags != null && clearFlags.isSeriesTotal(), val -> setElement(doc, root, "Count", val != null ? val.toString() : null));
            helper.copyPublishedDate(restoreMode, clearFlags != null && clearFlags.isPublishedDate(), date -> setDateElements(doc, root, date));
            helper.copyPageCount(restoreMode, clearFlags != null && clearFlags.isPageCount(), val -> setElement(doc, root, "PageCount", val != null ? val.toString() : null));
            helper.copyLanguage(restoreMode, clearFlags != null && clearFlags.isLanguage(), val -> setElement(doc, root, "LanguageISO", val));
            helper.copyAuthors(restoreMode, clearFlags != null && clearFlags.isAuthors(), set -> {
                setElement(doc, root, "Writer", join(set));
                removeElement(root, "Penciller");
                removeElement(root, "Inker");
                removeElement(root, "Colorist");
                removeElement(root, "Letterer");
                removeElement(root, "CoverArtist");
            });
            helper.copyCategories(restoreMode, clearFlags != null && clearFlags.isCategories(), set -> {
                setElement(doc, root, "Genre", join(set));
                removeElement(root, "Tags");
            });

            // Serialize ComicInfo.xml
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            ByteArrayOutputStream xmlBaos = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(xmlBaos));
            byte[] xmlBytes = xmlBaos.toByteArray();

            if (isCbz) {
                // Repack ZIP with updated/added ComicInfo.xml
                Path temp = Files.createTempFile("cbx_edit", ".cbz");
                try (ZipFile zipFile = new ZipFile(file);
                     ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(temp))) {
                    ZipEntry existing = findComicInfoEntry(zipFile);
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (existing != null && entry.getName().equals(existing.getName())) {
                            continue; // skip old ComicInfo.xml
                        }
                        zos.putNextEntry(new ZipEntry(entry.getName()));
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            is.transferTo(zos);
                        }
                        zos.closeEntry();
                    }
                    String entryName = existing != null ? existing.getName() : "ComicInfo.xml";
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.write(xmlBytes);
                    zos.closeEntry();
                }
                Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            // === CBR path ===
            // NOTE: Java libraries (junrar) don't support writing RAR. We'll shell out to a `rar` binary if available.
            String rarBin = System.getenv().getOrDefault("BOOKLORE_RAR_BIN", "rar");
            boolean rarAvailable;
            try {
                Process check = new ProcessBuilder(rarBin).redirectErrorStream(true).start();
                check.destroy();
                rarAvailable = true;
            } catch (Exception ex) {
                rarAvailable = false;
            }

            if (rarAvailable) {
                Path tempDir = Files.createTempDirectory("cbx_rar_");
                try {
                    // Extract entire RAR into a temp directory
                    try (Archive archive = new Archive(file)) {
                        for (FileHeader fh : archive.getFileHeaders()) {
                            String name = fh.getFileName();
                            if (name == null || name.isBlank()) continue;
                            Path out = tempDir.resolve(name);
                            if (fh.isDirectory()) {
                                Files.createDirectories(out);
                            } else {
                                Files.createDirectories(out.getParent());
                                try (OutputStream os = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                                    archive.extractFile(fh, os);
                                }
                            }
                        }
                    }

                    // Write/replace ComicInfo.xml in extracted tree root
                    Path comicInfo = tempDir.resolve("ComicInfo.xml");
                    Files.write(comicInfo, xmlBytes);

                    // Rebuild RAR archive in-place (replace original file)
                    ProcessBuilder pb = new ProcessBuilder(
                        rarBin, "a", "-idq", "-ep1", "-ma5", file.getAbsolutePath(), "."
                    );
                    pb.directory(tempDir.toFile());
                    Process p = pb.start();
                    int code = p.waitFor();
                    if (code == 0) {
                        // success; original CBR replaced/updated
                        return;
                    } else {
                        log.warn("RAR creation failed with exit code {}. Falling back to CBZ conversion for {}", code, file.getName());
                    }
                } finally {
                    try { // cleanup temp dir
                        java.nio.file.Files.walk(tempDir)
                            .sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignore) {} });
                    } catch (Exception ignore) {}
                }
            } else {
                log.warn("`rar` binary not found. Falling back to CBZ conversion for {}", file.getName());
            }

            // Fallback: convert the CBR to CBZ containing updated ComicInfo.xml
            Path tempZip = Files.createTempFile("cbx_edit", ".cbz");
            try (Archive archive = new Archive(file);
                 ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
                // Copy all entries from RAR to ZIP
                for (FileHeader fh : archive.getFileHeaders()) {
                    if (fh.isDirectory()) continue;
                    String entryName = fh.getFileName();
                    if ("ComicInfo.xml".equalsIgnoreCase(entryName)) {
                        // skip old; we'll add the updated one below
                        continue;
                    }
                    zos.putNextEntry(new ZipEntry(entryName));
                    archive.extractFile(fh, zos);
                    zos.closeEntry();
                }
                // Add updated ComicInfo.xml
                zos.putNextEntry(new ZipEntry("ComicInfo.xml"));
                zos.write(xmlBytes);
                zos.closeEntry();
            }
            Path target = file.toPath().resolveSibling(file.getName().substring(0, file.getName().lastIndexOf('.')) + ".cbz");
            Files.move(tempZip, target, StandardCopyOption.REPLACE_EXISTING);
            
            try { 
                // Remove original CBR after conversion
                log.info("Removing original CBR file: {}", file.getAbsolutePath());
                Files.deleteIfExists(file.toPath());
            } catch (Exception ignored) { /* if field/name differs, adjust in entity */ }
        } catch (Exception e) {
            log.warn("Failed to write metadata for {}: {}", file.getName(), e.getMessage(), e);
        }
    }

    private ZipEntry findComicInfoEntry(ZipFile zipFile) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if ("comicinfo.xml".equalsIgnoreCase(entry.getName())) {
                return entry;
            }
        }
        return null;
    }

    private FileHeader findComicInfoHeader(Archive archive) {
        for (FileHeader fh : archive.getFileHeaders()) {
            String name = fh.getFileName();
            if (name != null && name.equalsIgnoreCase("ComicInfo.xml")) {
                return fh;
            }
        }
        return null;
    }

    private Document buildSecureDocument(InputStream is) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(is);
    }

    private void setElement(Document doc, Element root, String tag, String value) {
        removeElement(root, tag);
        if (value != null && !value.isBlank()) {
            Element el = doc.createElement(tag);
            el.setTextContent(value);
            root.appendChild(el);
        }
    }

    private void removeElement(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            root.removeChild(nodes.item(i));
        }
    }

    private void setDateElements(Document doc, Element root, LocalDate date) {
        if (date == null) {
            removeElement(root, "Year");
            removeElement(root, "Month");
            removeElement(root, "Day");
            return;
        }
        setElement(doc, root, "Year", Integer.toString(date.getYear()));
        setElement(doc, root, "Month", Integer.toString(date.getMonthValue()));
        setElement(doc, root, "Day", Integer.toString(date.getDayOfMonth()));
    }

    private String join(Set<String> set) {
        return (set == null || set.isEmpty()) ? null : String.join(", ", set);
    }

    private String formatFloat(Float val) {
        if (val == null) return null;
        if (val % 1 == 0) return Integer.toString(val.intValue());
        return val.toString();
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.CBX;
    }
}
