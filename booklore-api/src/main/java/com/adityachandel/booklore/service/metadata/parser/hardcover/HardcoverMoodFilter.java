package com.adityachandel.booklore.service.metadata.parser.hardcover;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class HardcoverMoodFilter {


    public static final Set<String> RECOGNIZED_MOODS = Set.of(
            "adventurous", "challenging", "dark", "emotional", "funny",
            "hopeful", "informative", "inspiring", "lighthearted", "mysterious",
            "reflective", "relaxing", "romantic", "sad", "scary", "tense"
    );

    public static final int MIN_VOTE_COUNT = 3;

    public static final double MIN_VOTE_RATIO = 0.15;

    public static final int MAX_MOODS = 5;

    private HardcoverMoodFilter() {
    }

    public static Set<String> filterBasicMoods(List<String> moods) {
        if (moods == null || moods.isEmpty()) {
            return Collections.emptySet();
        }

        return moods.stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .map(String::trim)
                .filter(RECOGNIZED_MOODS::contains)
                .limit(MAX_MOODS)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<String> filterMoodsWithCounts(Map<String, List<HardcoverBookDetails.CachedTag>> cachedTags) {
        if (cachedTags == null || cachedTags.isEmpty()) {
            return Collections.emptySet();
        }

        List<HardcoverBookDetails.CachedTag> moodTags = cachedTags.get("Mood");
        if (moodTags == null || moodTags.isEmpty()) {
            return Collections.emptySet();
        }

        // Find the maximum vote count
        int maxCount = moodTags.stream()
                .filter(tag -> tag.getCount() != null)
                .mapToInt(HardcoverBookDetails.CachedTag::getCount)
                .max()
                .orElse(0);

        if (maxCount == 0) {
            // No votes at all, just return empty
            return Collections.emptySet();
        }

        int minCountThreshold = Math.max(MIN_VOTE_COUNT, (int) (maxCount * MIN_VOTE_RATIO));

        List<HardcoverBookDetails.CachedTag> filteredMoods = moodTags.stream()
                .filter(tag -> tag.getCount() != null && tag.getCount() >= minCountThreshold)
                .filter(tag -> tag.getTag() != null)
                .sorted(Comparator.comparingInt(HardcoverBookDetails.CachedTag::getCount).reversed())
                .limit(MAX_MOODS)
                .toList();

        log.debug("Filtered moods from {} to {} (max count: {}, threshold: {})",
                moodTags.size(), filteredMoods.size(), maxCount, minCountThreshold);

        return filteredMoods.stream()
                .map(HardcoverBookDetails.CachedTag::getTag)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<String> filterGenresWithCounts(Map<String, List<HardcoverBookDetails.CachedTag>> cachedTags) {
        if (cachedTags == null || cachedTags.isEmpty()) {
            return Collections.emptySet();
        }

        List<HardcoverBookDetails.CachedTag> genreTags = cachedTags.get("Genre");
        if (genreTags == null || genreTags.isEmpty()) {
            return Collections.emptySet();
        }

        // For genres, we're more lenient as they're generally more reliable
        return genreTags.stream()
                .filter(tag -> tag.getCount() != null && tag.getCount() >= 1)
                .filter(tag -> tag.getTag() != null)
                .sorted(Comparator.comparingInt(HardcoverBookDetails.CachedTag::getCount).reversed())
                .limit(10) // Allow more genres
                .map(HardcoverBookDetails.CachedTag::getTag)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<String> filterTagsWithCounts(Map<String, List<HardcoverBookDetails.CachedTag>> cachedTags) {
        if (cachedTags == null || cachedTags.isEmpty()) {
            return Collections.emptySet();
        }

        List<HardcoverBookDetails.CachedTag> tagList = cachedTags.get("Tag");
        if (tagList == null || tagList.isEmpty()) {
            return Collections.emptySet();
        }

        // For general tags, use a moderate threshold
        return tagList.stream()
                .filter(tag -> tag.getCount() != null && tag.getCount() >= 2)
                .filter(tag -> tag.getTag() != null)
                .sorted(Comparator.comparingInt(HardcoverBookDetails.CachedTag::getCount).reversed())
                .limit(10)
                .map(HardcoverBookDetails.CachedTag::getTag)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static String normalizeMood(String mood) {
        if (mood == null) {
            return null;
        }
        return mood.toLowerCase().trim();
    }
}
