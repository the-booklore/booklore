package org.booklore.util;

import lombok.experimental.UtilityClass;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

@UtilityClass
public class MetadataChangeDetector {

    private record FieldDescriptor<T>(
            String name,
            Function<BookMetadata, T> dtoValueGetter,
            Function<BookMetadataEntity, T> entityValueGetter,
            Function<BookMetadata, Boolean> dtoLockGetter,
            Function<BookMetadataEntity, Boolean> entityLockGetter,
            Predicate<MetadataClearFlags> clearFlagGetter,
            boolean includedInFileWrite
    ) {
        boolean isUnlocked(BookMetadataEntity entity) {
            return !isTrue(entityLockGetter.apply(entity));
        }

        boolean shouldClear(MetadataClearFlags flags) {
            return clearFlagGetter.test(flags);
        }

        T getNewValue(BookMetadata dto) {
            return dtoValueGetter.apply(dto);
        }

        T getOldValue(BookMetadataEntity entity) {
            return entityValueGetter.apply(entity);
        }

        Boolean getNewLock(BookMetadata dto) {
            return dtoLockGetter != null ? dtoLockGetter.apply(dto) : null;
        }

        Boolean getOldLock(BookMetadataEntity entity) {
            return entityLockGetter != null ? entityLockGetter.apply(entity) : null;
        }
    }

    private record CollectionFieldDescriptor(
            String name,
            Function<BookMetadata, Set<String>> dtoValueGetter,
            Function<BookMetadataEntity, Set<?>> entityValueGetter,
            Function<BookMetadata, Boolean> dtoLockGetter,
            Function<BookMetadataEntity, Boolean> entityLockGetter,
            Predicate<MetadataClearFlags> clearFlagGetter,
            boolean includedInFileWrite
    ) {
        boolean isUnlocked(BookMetadataEntity entity) {
            return !isTrue(entityLockGetter.apply(entity));
        }

        boolean shouldClear(MetadataClearFlags flags) {
            return clearFlagGetter.test(flags);
        }

        Set<String> getNewValue(BookMetadata dto) {
            return dtoValueGetter.apply(dto);
        }

        Set<String> getOldValue(BookMetadataEntity entity) {
            return toNameSet(entityValueGetter.apply(entity));
        }

        Boolean getNewLock(BookMetadata dto) {
            return dtoLockGetter != null ? dtoLockGetter.apply(dto) : null;
        }

        Boolean getOldLock(BookMetadataEntity entity) {
            return entityLockGetter != null ? entityLockGetter.apply(entity) : null;
        }
    }

    private static final List<FieldDescriptor<?>> SIMPLE_FIELDS = List.of(
            new FieldDescriptor<>("title",
                    BookMetadata::getTitle, BookMetadataEntity::getTitle,
                    BookMetadata::getTitleLocked, BookMetadataEntity::getTitleLocked,
                    MetadataClearFlags::isTitle, true),
            new FieldDescriptor<>("subtitle",
                    BookMetadata::getSubtitle, BookMetadataEntity::getSubtitle,
                    BookMetadata::getSubtitleLocked, BookMetadataEntity::getSubtitleLocked,
                    MetadataClearFlags::isSubtitle, true),
            new FieldDescriptor<>("publisher",
                    BookMetadata::getPublisher, BookMetadataEntity::getPublisher,
                    BookMetadata::getPublisherLocked, BookMetadataEntity::getPublisherLocked,
                    MetadataClearFlags::isPublisher, true),
            new FieldDescriptor<>("publishedDate",
                    BookMetadata::getPublishedDate, BookMetadataEntity::getPublishedDate,
                    BookMetadata::getPublishedDateLocked, BookMetadataEntity::getPublishedDateLocked,
                    MetadataClearFlags::isPublishedDate, true),
            new FieldDescriptor<>("description",
                    BookMetadata::getDescription, BookMetadataEntity::getDescription,
                    BookMetadata::getDescriptionLocked, BookMetadataEntity::getDescriptionLocked,
                    MetadataClearFlags::isDescription, true),
            new FieldDescriptor<>("seriesName",
                    BookMetadata::getSeriesName, BookMetadataEntity::getSeriesName,
                    BookMetadata::getSeriesNameLocked, BookMetadataEntity::getSeriesNameLocked,
                    MetadataClearFlags::isSeriesName, true),
            new FieldDescriptor<>("seriesNumber",
                    BookMetadata::getSeriesNumber, BookMetadataEntity::getSeriesNumber,
                    BookMetadata::getSeriesNumberLocked, BookMetadataEntity::getSeriesNumberLocked,
                    MetadataClearFlags::isSeriesNumber, true),
            new FieldDescriptor<>("seriesTotal",
                    BookMetadata::getSeriesTotal, BookMetadataEntity::getSeriesTotal,
                    BookMetadata::getSeriesTotalLocked, BookMetadataEntity::getSeriesTotalLocked,
                    MetadataClearFlags::isSeriesTotal, true),
            new FieldDescriptor<>("isbn13",
                    BookMetadata::getIsbn13, BookMetadataEntity::getIsbn13,
                    BookMetadata::getIsbn13Locked, BookMetadataEntity::getIsbn13Locked,
                    MetadataClearFlags::isIsbn13, true),
            new FieldDescriptor<>("isbn10",
                    BookMetadata::getIsbn10, BookMetadataEntity::getIsbn10,
                    BookMetadata::getIsbn10Locked, BookMetadataEntity::getIsbn10Locked,
                    MetadataClearFlags::isIsbn10, true),
            new FieldDescriptor<>("asin",
                    BookMetadata::getAsin, BookMetadataEntity::getAsin,
                    BookMetadata::getAsinLocked, BookMetadataEntity::getAsinLocked,
                    MetadataClearFlags::isAsin, true),
            new FieldDescriptor<>("goodreadsId",
                    BookMetadata::getGoodreadsId, BookMetadataEntity::getGoodreadsId,
                    BookMetadata::getGoodreadsIdLocked, BookMetadataEntity::getGoodreadsIdLocked,
                    MetadataClearFlags::isGoodreadsId, true),
            new FieldDescriptor<>("comicvineId",
                    BookMetadata::getComicvineId, BookMetadataEntity::getComicvineId,
                    BookMetadata::getComicvineIdLocked, BookMetadataEntity::getComicvineIdLocked,
                    MetadataClearFlags::isComicvineId, true),
            new FieldDescriptor<>("hardcoverId",
                    BookMetadata::getHardcoverId, BookMetadataEntity::getHardcoverId,
                    BookMetadata::getHardcoverIdLocked, BookMetadataEntity::getHardcoverIdLocked,
                    MetadataClearFlags::isHardcoverId, true),
            new FieldDescriptor<>("hardcoverBookId",
                    BookMetadata::getHardcoverBookId, BookMetadataEntity::getHardcoverBookId,
                    BookMetadata::getHardcoverBookIdLocked, BookMetadataEntity::getHardcoverBookIdLocked,
                    MetadataClearFlags::isHardcoverBookId, true),
            new FieldDescriptor<>("googleId",
                    BookMetadata::getGoogleId, BookMetadataEntity::getGoogleId,
                    BookMetadata::getGoogleIdLocked, BookMetadataEntity::getGoogleIdLocked,
                    MetadataClearFlags::isGoogleId, true),
            new FieldDescriptor<>("lubimyczytacId",
                    BookMetadata::getLubimyczytacId, BookMetadataEntity::getLubimyczytacId,
                    BookMetadata::getLubimyczytacIdLocked, BookMetadataEntity::getLubimyczytacIdLocked,
                    MetadataClearFlags::isLubimyczytacId, true),
            new FieldDescriptor<>("ranobedbId",
                    BookMetadata::getRanobedbId, BookMetadataEntity::getRanobedbId,
                    BookMetadata::getRanobedbIdLocked, BookMetadataEntity::getRanobedbIdLocked,
                    MetadataClearFlags::isRanobedbId, true),
            new FieldDescriptor<>("language",
                    BookMetadata::getLanguage, BookMetadataEntity::getLanguage,
                    BookMetadata::getLanguageLocked, BookMetadataEntity::getLanguageLocked,
                    MetadataClearFlags::isLanguage, true),
            new FieldDescriptor<>("pageCount",
                    BookMetadata::getPageCount, BookMetadataEntity::getPageCount,
                    BookMetadata::getPageCountLocked, BookMetadataEntity::getPageCountLocked,
                    MetadataClearFlags::isPageCount, false),
            new FieldDescriptor<>("amazonRating",
                    BookMetadata::getAmazonRating, BookMetadataEntity::getAmazonRating,
                    BookMetadata::getAmazonRatingLocked, BookMetadataEntity::getAmazonRatingLocked,
                    MetadataClearFlags::isAmazonRating, false),
            new FieldDescriptor<>("amazonReviewCount",
                    BookMetadata::getAmazonReviewCount, BookMetadataEntity::getAmazonReviewCount,
                    BookMetadata::getAmazonReviewCountLocked, BookMetadataEntity::getAmazonReviewCountLocked,
                    MetadataClearFlags::isAmazonReviewCount, false),
            new FieldDescriptor<>("goodreadsRating",
                    BookMetadata::getGoodreadsRating, BookMetadataEntity::getGoodreadsRating,
                    BookMetadata::getGoodreadsRatingLocked, BookMetadataEntity::getGoodreadsRatingLocked,
                    MetadataClearFlags::isGoodreadsRating, false),
            new FieldDescriptor<>("goodreadsReviewCount",
                    BookMetadata::getGoodreadsReviewCount, BookMetadataEntity::getGoodreadsReviewCount,
                    BookMetadata::getGoodreadsReviewCountLocked, BookMetadataEntity::getGoodreadsReviewCountLocked,
                    MetadataClearFlags::isGoodreadsReviewCount, false),
            new FieldDescriptor<>("hardcoverRating",
                    BookMetadata::getHardcoverRating, BookMetadataEntity::getHardcoverRating,
                    BookMetadata::getHardcoverRatingLocked, BookMetadataEntity::getHardcoverRatingLocked,
                    MetadataClearFlags::isHardcoverRating, false),
            new FieldDescriptor<>("hardcoverReviewCount",
                    BookMetadata::getHardcoverReviewCount, BookMetadataEntity::getHardcoverReviewCount,
                    BookMetadata::getHardcoverReviewCountLocked, BookMetadataEntity::getHardcoverReviewCountLocked,
                    MetadataClearFlags::isHardcoverReviewCount, false),
            new FieldDescriptor<>("lubimyczytacRating",
                    BookMetadata::getLubimyczytacRating, BookMetadataEntity::getLubimyczytacRating,
                    BookMetadata::getLubimyczytacRatingLocked, BookMetadataEntity::getLubimyczytacRatingLocked,
                    MetadataClearFlags::isLubimyczytacRating, false),
            new FieldDescriptor<>("ranobedbRating",
                    BookMetadata::getRanobedbRating, BookMetadataEntity::getRanobedbRating,
                    BookMetadata::getRanobedbRatingLocked, BookMetadataEntity::getRanobedbRatingLocked,
                    MetadataClearFlags::isRanobedbRating, false),
            new FieldDescriptor<>("narrator",
                    BookMetadata::getNarrator, BookMetadataEntity::getNarrator,
                    BookMetadata::getNarratorLocked, BookMetadataEntity::getNarratorLocked,
                    MetadataClearFlags::isNarrator, false),
            new FieldDescriptor<>("abridged",
                    BookMetadata::getAbridged, BookMetadataEntity::getAbridged,
                    BookMetadata::getAbridgedLocked, BookMetadataEntity::getAbridgedLocked,
                    MetadataClearFlags::isAbridged, false)
    );

    private static final List<CollectionFieldDescriptor> COLLECTION_FIELDS = List.of(
            new CollectionFieldDescriptor("authors",
                    BookMetadata::getAuthors, BookMetadataEntity::getAuthors,
                    BookMetadata::getAuthorsLocked, BookMetadataEntity::getAuthorsLocked,
                    MetadataClearFlags::isAuthors, true),
            new CollectionFieldDescriptor("categories",
                    BookMetadata::getCategories, BookMetadataEntity::getCategories,
                    BookMetadata::getCategoriesLocked, BookMetadataEntity::getCategoriesLocked,
                    MetadataClearFlags::isCategories, true),
            new CollectionFieldDescriptor("moods",
                    BookMetadata::getMoods, BookMetadataEntity::getMoods,
                    BookMetadata::getMoodsLocked, BookMetadataEntity::getMoodsLocked,
                    MetadataClearFlags::isMoods, false),
            new CollectionFieldDescriptor("tags",
                    BookMetadata::getTags, BookMetadataEntity::getTags,
                    BookMetadata::getTagsLocked, BookMetadataEntity::getTagsLocked,
                    MetadataClearFlags::isTags, false)
    );

    public static boolean isDifferent(BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        if (clear == null) return true;
        for (FieldDescriptor<?> field : SIMPLE_FIELDS) {
            if (hasFieldDifference(field, newMeta, existingMeta, clear)) {
                return true;
            }
        }
        for (CollectionFieldDescriptor field : COLLECTION_FIELDS) {
            if (hasCollectionFieldDifference(field, newMeta, existingMeta, clear)) {
                return true;
            }
        }
        return differsLock(newMeta.getCoverLocked(), existingMeta.getCoverLocked()) || differsLock(newMeta.getAudiobookCoverLocked(), existingMeta.getAudiobookCoverLocked());
    }

    public static boolean hasValueChanges(BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        for (FieldDescriptor<?> field : SIMPLE_FIELDS) {
            if (hasValueDifference(field, newMeta, existingMeta, clear)) {
                return true;
            }
        }
        for (CollectionFieldDescriptor field : COLLECTION_FIELDS) {
            if (hasCollectionValueDifference(field, newMeta, existingMeta, clear)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasValueChangesForFileWrite(BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        for (FieldDescriptor<?> field : SIMPLE_FIELDS) {
            if (field.includedInFileWrite() && hasValueDifference(field, newMeta, existingMeta, clear)) {
                return true;
            }
        }
        for (CollectionFieldDescriptor field : COLLECTION_FIELDS) {
            if (field.includedInFileWrite() && hasCollectionValueDifference(field, newMeta, existingMeta, clear)) {
                return true;
            }
        }
        return false;
    }

    private static <T> boolean hasFieldDifference(FieldDescriptor<T> field, BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        boolean valueChanged = differs(
                field.shouldClear(clear),
                field.getNewValue(newMeta),
                field.getOldValue(existingMeta),
                field.isUnlocked(existingMeta)
        );
        boolean lockChanged = differsLock(field.getNewLock(newMeta), field.getOldLock(existingMeta));
        return valueChanged || lockChanged;
    }

    private static boolean hasCollectionFieldDifference(CollectionFieldDescriptor field, BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        boolean valueChanged = differs(
                field.shouldClear(clear),
                field.getNewValue(newMeta),
                field.getOldValue(existingMeta),
                field.isUnlocked(existingMeta)
        );
        boolean lockChanged = differsLock(field.getNewLock(newMeta), field.getOldLock(existingMeta));
        return valueChanged || lockChanged;
    }

    private static <T> boolean hasValueDifference(FieldDescriptor<T> field, BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        return differs(
                field.shouldClear(clear),
                field.getNewValue(newMeta),
                field.getOldValue(existingMeta),
                field.isUnlocked(existingMeta)
        );
    }

    private static boolean hasCollectionValueDifference(CollectionFieldDescriptor field, BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        return differs(
                field.shouldClear(clear),
                field.getNewValue(newMeta),
                field.getOldValue(existingMeta),
                field.isUnlocked(existingMeta)
        );
    }

    private static boolean differs(boolean shouldClear, Object newVal, Object oldVal, boolean isUnlocked) {
        if (!isUnlocked) return false;

        Object normNew = normalize(newVal);
        Object normOld = normalize(oldVal);

        // Ignore transitions from null to empty string or empty set
        if (normOld == null && isEffectivelyEmpty(normNew)) return false;
        if (shouldClear) return normOld != null;

        return !Objects.equals(normNew, normOld);
    }

    private static boolean isEffectivelyEmpty(Object value) {
        return switch (value) {
            case null -> true;
            case String s -> s.isBlank();
            case Collection<?> c -> c.isEmpty();
            default -> false;
        };
    }

    private static boolean differsLock(Boolean dtoLock, Boolean entityLock) {
        return !Objects.equals(Boolean.TRUE.equals(dtoLock), Boolean.TRUE.equals(entityLock));
    }

    private static Object normalize(Object value) {
        if (value instanceof String s) return s.strip();
        return value;
    }

    private static Set<String> toNameSet(Set<?> entities) {
        if (entities == null) {
            return Collections.emptySet();
        }
        return entities.stream()
                .map(e -> switch (e) {
                    case AuthorEntity author -> author.getName();
                    case CategoryEntity category -> category.getName();
                    case MoodEntity mood -> mood.getName();
                    case TagEntity tag -> tag.getName();
                    default -> e.toString();
                })
                .collect(Collectors.toSet());
    }
}
