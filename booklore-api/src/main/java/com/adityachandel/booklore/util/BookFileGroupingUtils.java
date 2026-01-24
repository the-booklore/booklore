package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@UtilityClass
public class BookFileGroupingUtils {

    private static final Pattern FORMAT_INDICATOR_PATTERN = Pattern.compile(
            "[\\(\\[]\\s*(?:pdf|epub|mobi|azw3?|fb2|cbz|cbr|cb7)\\s*[\\)\\]]",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    public String extractGroupingKey(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }

        int lastDot = fileName.lastIndexOf('.');
        String baseName = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;

        baseName = FORMAT_INDICATOR_PATTERN.matcher(baseName).replaceAll("");
        baseName = WHITESPACE_PATTERN.matcher(baseName.trim()).replaceAll(" ");

        return baseName.toLowerCase().trim();
    }

    public String generateDirectoryGroupKey(String fileSubPath, String fileName) {
        String safeSubPath = (fileSubPath == null) ? "" : fileSubPath;
        return safeSubPath + ":" + extractGroupingKey(fileName);
    }

    public Map<String, List<LibraryFile>> groupByBaseName(List<LibraryFile> libraryFiles) {
        return libraryFiles.stream()
                .collect(Collectors.groupingBy(file ->
                        file.getLibraryPathEntity().getId() + ":" +
                                generateDirectoryGroupKey(file.getFileSubPath(), file.getFileName())
                ));
    }
}
