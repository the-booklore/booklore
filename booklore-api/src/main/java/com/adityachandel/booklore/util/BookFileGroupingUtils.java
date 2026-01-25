package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import lombok.experimental.UtilityClass;
import org.apache.commons.text.similarity.FuzzyScore;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@UtilityClass
public class BookFileGroupingUtils {

    private static final Pattern FORMAT_INDICATOR_PATTERN = Pattern.compile(
            "[\\(\\[]\\s*(?:pdf|epub|mobi|azw3?|fb2|cbz|cbr|cb7)\\s*[\\)\\]]",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final Pattern UNDERSCORE_PATTERN = Pattern.compile("_");

    // Matches trailing author: " - J.R.R. Tolkien", " - George Orwell"
    private static final Pattern TRAILING_AUTHOR_PATTERN = Pattern.compile(
            "\\s*[-–—]\\s+[A-Z][a-z]+(?:\\s+[A-Z](?:\\.[A-Z])*\\.?)?(?:\\s+[A-Z][a-z]+)*\\s*$"
    );

    // Matches ", The" or ", A" or ", An" at end
    private static final Pattern ARTICLE_SUFFIX_PATTERN = Pattern.compile(
            ",\\s*(The|A|An)\\s*$", Pattern.CASE_INSENSITIVE
    );

    public String extractGroupingKey(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }

        int lastDot = fileName.lastIndexOf('.');
        String baseName = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;

        // Convert underscores to spaces
        baseName = UNDERSCORE_PATTERN.matcher(baseName).replaceAll(" ");

        baseName = FORMAT_INDICATOR_PATTERN.matcher(baseName).replaceAll("");

        // Strip trailing author names (after dash)
        baseName = TRAILING_AUTHOR_PATTERN.matcher(baseName).replaceAll("");

        // Reposition articles ("Hobbit, The" -> "The Hobbit")
        Matcher articleMatcher = ARTICLE_SUFFIX_PATTERN.matcher(baseName);
        if (articleMatcher.find()) {
            String article = articleMatcher.group(1);
            baseName = article + " " + baseName.substring(0, articleMatcher.start());
        }

        baseName = WHITESPACE_PATTERN.matcher(baseName.trim()).replaceAll(" ");

        return baseName.toLowerCase().trim();
    }

    public double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0;
        }
        FuzzyScore fuzzyScore = new FuzzyScore(Locale.ENGLISH);
        int score = fuzzyScore.fuzzyScore(s1, s2);
        int maxScore = Math.max(
                fuzzyScore.fuzzyScore(s1, s1),
                fuzzyScore.fuzzyScore(s2, s2)
        );
        return maxScore > 0 ? (double) score / maxScore : 0;
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
