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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
public class CbxMetadataWriter implements MetadataWriter {

    @Override
    public void writeMetadataToFile(File file, BookMetadataEntity metadata, String thumbnailUrl, boolean restoreMode, MetadataClearFlags clearFlags) {
        try {
            Path temp = Files.createTempFile("cbx_edit", ".cbz");
            try (ZipFile zipFile = new ZipFile(file);
                 ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(temp))) {

                ZipEntry existing = findComicInfoEntry(zipFile);
                Document doc;
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

                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                transformer.transform(new DOMSource(doc), new StreamResult(baos));
                byte[] xmlBytes = baos.toByteArray();

                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (existing != null && entry.getName().equals(existing.getName())) {
                        continue;
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
        } catch (Exception e) {
            log.warn("Failed to write metadata to CBZ file {}: {}", file.getName(), e.getMessage(), e);
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
        return String.format(java.util.Locale.US, "%s", val);
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.CBX;
    }
}
