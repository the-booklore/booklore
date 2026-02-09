package org.booklore.service;

import jakarta.persistence.criteria.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.GroupRule;
import org.booklore.model.dto.Rule;
import org.booklore.model.dto.RuleField;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class BookRuleEvaluatorService {

    private final ObjectMapper objectMapper;

    public Specification<BookEntity> toSpecification(GroupRule groupRule, Long userId) {
        return (root, query, cb) -> {
            Join<BookEntity, UserBookProgressEntity> progressJoin = root.join("userBookProgress", JoinType.LEFT);

            Predicate userPredicate = cb.or(
                cb.isNull(progressJoin.get("user").get("id")),
                cb.equal(progressJoin.get("user").get("id"), userId)
            );

            Predicate rulePredicate = buildPredicate(groupRule, cb, root, progressJoin);

            return cb.and(userPredicate, rulePredicate);
        };
    }

    private Predicate buildPredicate(GroupRule group, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        if (group.getRules() == null || group.getRules().isEmpty()) {
            return cb.conjunction();
        }

        List<Predicate> predicates = new ArrayList<>();

        for (Object ruleObj : group.getRules()) {
            if (ruleObj == null) continue;

            Map<String, Object> ruleMap = objectMapper.convertValue(ruleObj, new TypeReference<>() {
            });
            String type = (String) ruleMap.get("type");

            if ("group".equals(type)) {
                GroupRule subGroup = objectMapper.convertValue(ruleObj, GroupRule.class);
                predicates.add(buildPredicate(subGroup, cb, root, progressJoin));
            } else {
                try {
                    Rule rule = objectMapper.convertValue(ruleObj, Rule.class);
                    Predicate rulePredicate = buildRulePredicate(rule, cb, root, progressJoin);
                    if (rulePredicate != null) {
                        predicates.add(rulePredicate);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse rule: {}, error: {}", ruleObj, e.getMessage(), e);
                }
            }
        }

        if (predicates.isEmpty()) {
            return cb.conjunction();
        }

        return group.getJoin() == org.booklore.model.dto.JoinType.AND
                ? cb.and(predicates.toArray(new Predicate[0]))
                : cb.or(predicates.toArray(new Predicate[0]));
    }

    private Predicate buildRulePredicate(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        if (rule.getField() == null || rule.getOperator() == null) return null;

        // Special handling for METADATA field
        if (rule.getField() == RuleField.METADATA) {
            return buildMetadataPredicate(rule, cb, root, progressJoin);
        }

        // Special handling for INCOMPLETE_SERIES field
        if (rule.getField() == RuleField.INCOMPLETE_SERIES) {
            return buildIncompleteSeriesPredicate(rule, cb, root, progressJoin);
        }

        // Special handling for SERIES_STATUS field
        if (rule.getField() == RuleField.SERIES_STATUS) {
            return buildSeriesStatusPredicate(rule, cb, root, progressJoin);
        }

        // Special handling for FILE_TYPE field
        if (rule.getField() == RuleField.FILE_TYPE) {
            return buildFileTypePredicate(rule, cb, root, progressJoin);
        }

        // Special handling for FILE_SIZE field
        if (rule.getField() == RuleField.FILE_SIZE) {
            return buildFileSizePredicate(rule, cb, root, progressJoin);
        }

        return switch (rule.getOperator()) {
            case EQUALS -> buildEquals(rule, cb, root, progressJoin);
            case NOT_EQUALS -> buildNotEquals(rule, cb, root, progressJoin);
            case CONTAINS -> buildContains(rule, cb, root, progressJoin);
            case DOES_NOT_CONTAIN -> cb.not(buildContains(rule, cb, root, progressJoin));
            case STARTS_WITH -> buildStartsWith(rule, cb, root, progressJoin);
            case ENDS_WITH -> buildEndsWith(rule, cb, root, progressJoin);
            case GREATER_THAN -> buildGreaterThan(rule, cb, root, progressJoin);
            case GREATER_THAN_EQUAL_TO -> buildGreaterThanEqual(rule, cb, root, progressJoin);
            case LESS_THAN -> buildLessThan(rule, cb, root, progressJoin);
            case LESS_THAN_EQUAL_TO -> buildLessThanEqual(rule, cb, root, progressJoin);
            case IN_BETWEEN -> buildInBetween(rule, cb, root, progressJoin);
            case IS_EMPTY -> buildIsEmpty(rule, cb, root, progressJoin);
            case IS_NOT_EMPTY -> cb.not(buildIsEmpty(rule, cb, root, progressJoin));
            case INCLUDES_ANY -> buildIncludesAny(rule, cb, root, progressJoin);
            case EXCLUDES_ALL -> buildExcludesAll(rule, cb, root, progressJoin);
            case INCLUDES_ALL -> buildIncludesAll(rule, cb, root, progressJoin);
            case HAS -> cb.not(buildIsEmpty(rule, cb, root, progressJoin));
            case MISSING -> buildIsEmpty(rule, cb, root, progressJoin);
        };
    }

    /**
     * Builds a predicate for the METADATA field rule.
     * Supports checking any metadata field by name using HAS/MISSING operators.
     *
     * @param rule The rule containing the metadata field name and operator
     * @param cb CriteriaBuilder for creating predicates
     * @param root Root entity for BookEntity
     * @param progressJoin Join to UserBookProgressEntity
     * @return Predicate for metadata field check, or conjunction if invalid
     */
    private Predicate buildMetadataPredicate(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        if (rule.getValue() == null) {
            log.warn("METADATA rule missing field name value");
            return cb.conjunction();
        }

        String metadataFieldName = rule.getValue().toString().toLowerCase();
        
        // Special handling for collection/array fields (moods, tags, authors, categories)
        if (isArrayMetadataField(metadataFieldName)) {
            return buildArrayMetadataFieldPredicate(rule, metadataFieldName, cb, root);
        }
        
        Expression<?> fieldExpression = getMetadataFieldExpression(metadataFieldName, cb, root, progressJoin);

        if (fieldExpression == null) {
            log.warn("Invalid metadata field name: {}", metadataFieldName);
            return cb.conjunction();
        }

        // HAS and MISSING operators check for null/empty
        if (rule.getOperator() == org.booklore.model.dto.RuleOperator.HAS) {
            // HAS: Field should not be null (empty strings count as "has")
            log.debug("METADATA filter: checking if '{}' HAS value", metadataFieldName);
            return cb.isNotNull(fieldExpression);
        } else if (rule.getOperator() == org.booklore.model.dto.RuleOperator.MISSING) {
            // MISSING: Field should be null
            log.debug("METADATA filter: checking if '{}' is MISSING value", metadataFieldName);
            return cb.isNull(fieldExpression);
        }

        log.warn("METADATA field only supports HAS and MISSING operators, got: {}", rule.getOperator());
        return cb.conjunction();
    }

    /**
     * Maps metadata field names to their JPA expressions.
     * Supports case-insensitive field name matching.
     *
     * @param fieldName The metadata field name (case-insensitive)
     * @param cb CriteriaBuilder for creating expressions
     * @param root Root entity for BookEntity
     * @param progressJoin Join to UserBookProgressEntity
     * @return Expression for the field, or null if field name is invalid
     */
    private Expression<?> getMetadataFieldExpression(String fieldName, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return switch (fieldName.toLowerCase()) {
            // IDs
            case "isbn13" -> root.get("metadata").get("isbn13");
            case "isbn10" -> root.get("metadata").get("isbn10");
            case "asin" -> root.get("metadata").get("asin");
            case "goodreadsid" -> root.get("metadata").get("goodreadsId");
            case "comicvineid" -> root.get("metadata").get("comicvineId");
            case "hardcoverid" -> root.get("metadata").get("hardcoverId");
            case "hardcoverbookid" -> root.get("metadata").get("hardcoverBookId");
            case "googleid" -> root.get("metadata").get("googleId");
            case "lubimyczytacid" -> root.get("metadata").get("lubimyczytacId");
            case "ranobedbid" -> root.get("metadata").get("ranobedbId");
            
            // Ratings
            case "amazonrating" -> root.get("metadata").get("amazonRating");
            case "goodreadsrating" -> root.get("metadata").get("goodreadsRating");
            case "hardcoverrating" -> root.get("metadata").get("hardcoverRating");
            case "lubimyczytacrating" -> root.get("metadata").get("lubimyczytacRating");
            case "ranobedbrating" -> root.get("metadata").get("ranobedbRating");
            case "personalrating" -> progressJoin.get("personalRating");
            
            // Review counts
            case "amazonreviewcount" -> root.get("metadata").get("amazonReviewCount");
            case "goodreadsreviewcount" -> root.get("metadata").get("goodreadsReviewCount");
            case "hardcoverreviewcount" -> root.get("metadata").get("hardcoverReviewCount");
            
            // Text fields
            case "title" -> root.get("metadata").get("title");
            case "subtitle" -> root.get("metadata").get("subtitle");
            case "publisher" -> root.get("metadata").get("publisher");
            case "description" -> root.get("metadata").get("description");
            case "seriesname" -> root.get("metadata").get("seriesName");
            case "language" -> root.get("metadata").get("language");
            
            // Numbers
            case "pagecount" -> root.get("metadata").get("pageCount");
            case "seriesnumber" -> root.get("metadata").get("seriesNumber");
            case "seriestotal" -> root.get("metadata").get("seriesTotal");
            
            // Dates
            case "publisheddate" -> root.get("metadata").get("publishedDate");
            
            // Content ratings
            case "agerating" -> root.get("metadata").get("ageRating");
            case "contentrating" -> root.get("metadata").get("contentRating");
            
            default -> null;
        };
    }

    /**
     * Checks if a metadata field is a text field that needs empty string checking.
     *
     * @param fieldName The metadata field name (case-insensitive)
     * @return true if field is a text field, false otherwise
     */
    private boolean isTextMetadataField(String fieldName) {
        return switch (fieldName.toLowerCase()) {
            case "isbn13", "isbn10", "asin", "goodreadsid", "comicvineid", "hardcoverid",
                 "hardcoverbookid", "googleid", "lubimyczytacid", "ranobedbid",
                 "title", "subtitle", "publisher", "description", "seriesname", "language", "contentrating" -> true;
            default -> false;
        };
    }

    /**
     * Checks if a metadata field is an array/collection field.
     *
     * @param fieldName The metadata field name (case-insensitive)
     * @return true if field is an array field, false otherwise
     */
    private boolean isArrayMetadataField(String fieldName) {
        return switch (fieldName.toLowerCase()) {
            case "authors", "categories", "moods", "tags" -> true;
            default -> false;
        };
    }

    /**
     * Builds a predicate for array metadata fields (moods, tags, authors, categories).
     * Checks if the collection exists and has elements.
     *
     * @param rule The rule containing the operator (HAS/MISSING)
     * @param fieldName The array field name (lowercase)
     * @param cb CriteriaBuilder for creating predicates
     * @param root Root entity for BookEntity
     * @return Predicate for collection existence check
     */
    private Predicate buildArrayMetadataFieldPredicate(Rule rule, String fieldName, CriteriaBuilder cb, Root<BookEntity> root) {
        RuleField arrayField = switch (fieldName) {
            case "authors" -> RuleField.AUTHORS;
            case "categories" -> RuleField.CATEGORIES;
            case "moods" -> RuleField.MOODS;
            case "tags" -> RuleField.TAGS;
            default -> null;
        };

        if (arrayField == null) {
            log.warn("Invalid array metadata field name: {}", fieldName);
            return cb.conjunction();
        }

        // Create a subquery to check if the collection has any elements
        Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
        Root<BookEntity> subRoot = subquery.from(BookEntity.class);

        Join<Object, Object> metadataJoin = subRoot.join("metadata", JoinType.INNER);
        joinArrayField(arrayField, metadataJoin);

        subquery.select(cb.literal(1L)).where(cb.equal(subRoot.get("id"), root.get("id")));

        boolean hasOperator = rule.getOperator() == org.booklore.model.dto.RuleOperator.HAS;
        return hasOperator ? cb.exists(subquery) : cb.not(cb.exists(subquery));
    }

    /**
     * Builds a predicate for the INCOMPLETE_SERIES field rule.
     * Checks if a book belongs to a series that appears incomplete.
     *
     * @param rule The rule containing the boolean value (true = incomplete, false = complete/not in series)
     * @param cb CriteriaBuilder for creating predicates
     * @param root Root entity for BookEntity
     * @param progressJoin Join to UserBookProgressEntity
     * @return Predicate for incomplete series check
     */
    private Predicate buildIncompleteSeriesPredicate(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        boolean wantIncomplete = Boolean.parseBoolean(rule.getValue().toString());
        
        // Books with series name but null series number are excluded from both groups
        Expression<String> seriesName = root.get("metadata").get("seriesName");
        Expression<Double> seriesNumber = root.get("metadata").get("seriesNumber");
        
        Predicate hasValidSeries = cb.and(
            cb.isNotNull(seriesName),
            cb.notEqual(cb.trim(seriesName), ""),
            cb.isNotNull(seriesNumber)
        );

        Predicate result;
        if (wantIncomplete) {
            // Return books with valid series that are incomplete
            result = cb.and(hasValidSeries, isSeriesIncompleteSubquery(cb, root, seriesName));
        } else {
            // Return books that are either:
            // 1. Not in a series (seriesName is null or empty), or
            // 2. In a complete series
            Predicate notInSeries = cb.or(
                cb.isNull(seriesName),
                cb.equal(cb.trim(seriesName), "")
            );
            
            result = cb.or(notInSeries, cb.and(hasValidSeries, cb.not(isSeriesIncompleteSubquery(cb, root, seriesName))));
        }

        // Handle NOT_EQUALS operator
        if (rule.getOperator() == org.booklore.model.dto.RuleOperator.NOT_EQUALS) {
            return cb.not(result);
        }

        return result;
    }

    /**
     * Builds a predicate for the FILE_TYPE field rule.
     * Uses a subquery to check the primary book file's type.
     *
     * @param rule The rule containing the file type value and operator
     * @param cb CriteriaBuilder for creating predicates
     * @param root Root entity for BookEntity
     * @param progressJoin Join to UserBookProgressEntity (unused)
     * @return Predicate for file type check
     */
    private Predicate buildFileTypePredicate(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        // Create a subquery to check if a matching book file exists
        Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
        Root<org.booklore.model.entity.BookFileEntity> fileRoot = subquery.from(org.booklore.model.entity.BookFileEntity.class);
        
        // Get the file extension from the fileName
        Expression<String> fileExtension = cb.function("SUBSTRING_INDEX", String.class,
                fileRoot.get("fileName"), cb.literal("."), cb.literal(-1));
        
        // Build predicate based on operator
        Predicate valuePredicate = switch (rule.getOperator()) {
            case EQUALS -> {
                if (rule.getValue() instanceof List) {
                    List<?> values = (List<?>) rule.getValue();
                    List<Predicate> predicates = values.stream()
                            .map(v -> cb.equal(cb.lower(fileExtension), v.toString().toLowerCase()))
                            .collect(Collectors.toList());
                    yield cb.or(predicates.toArray(new Predicate[0]));
                } else {
                    yield cb.equal(cb.lower(fileExtension), rule.getValue().toString().toLowerCase());
                }
            }
            case NOT_EQUALS -> {
                if (rule.getValue() instanceof List) {
                    List<?> values = (List<?>) rule.getValue();
                    List<Predicate> predicates = values.stream()
                            .map(v -> cb.notEqual(cb.lower(fileExtension), v.toString().toLowerCase()))
                            .collect(Collectors.toList());
                    yield cb.and(predicates.toArray(new Predicate[0]));
                } else {
                    yield cb.notEqual(cb.lower(fileExtension), rule.getValue().toString().toLowerCase());
                }
            }
            case INCLUDES_ANY -> {
                List<?> values = (List<?>) rule.getValue();
                List<Predicate> predicates = values.stream()
                        .map(v -> cb.equal(cb.lower(fileExtension), v.toString().toLowerCase()))
                        .collect(Collectors.toList());
                yield cb.or(predicates.toArray(new Predicate[0]));
            }
            case EXCLUDES_ALL -> {
                List<?> values = (List<?>) rule.getValue();
                List<Predicate> predicates = values.stream()
                        .map(v -> cb.notEqual(cb.lower(fileExtension), v.toString().toLowerCase()))
                        .collect(Collectors.toList());
                yield cb.and(predicates.toArray(new Predicate[0]));
            }
            case IS_EMPTY -> cb.isNull(fileExtension);
            case IS_NOT_EMPTY -> cb.isNotNull(fileExtension);
            default -> cb.conjunction();
        };
        
        subquery.select(cb.literal(1L))
                .where(cb.and(
                        cb.equal(fileRoot.get("book").get("id"), root.get("id")),
                        cb.isTrue(fileRoot.get("isBookFormat")),
                        valuePredicate
                ));
        
        return cb.exists(subquery);
    }

    /**
     * Builds a predicate for the FILE_SIZE field rule.
     * Uses a subquery to check the primary book file's size.
     *
     * @param rule The rule containing the file size value and operator
     * @param cb CriteriaBuilder for creating predicates
     * @param root Root entity for BookEntity
     * @param progressJoin Join to UserBookProgressEntity (unused)
     * @return Predicate for file size check
     */
    private Predicate buildFileSizePredicate(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        // Create a subquery to check if a matching book file exists
        Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
        Root<org.booklore.model.entity.BookFileEntity> fileRoot = subquery.from(org.booklore.model.entity.BookFileEntity.class);
        
        Expression<Long> fileSizeKb = fileRoot.get("fileSizeKb");
        
        // Build predicate based on operator
        Predicate valuePredicate = switch (rule.getOperator()) {
            case EQUALS -> cb.equal(fileSizeKb, Long.parseLong(rule.getValue().toString()));
            case NOT_EQUALS -> cb.notEqual(fileSizeKb, Long.parseLong(rule.getValue().toString()));
            case GREATER_THAN -> cb.greaterThan(fileSizeKb, Long.parseLong(rule.getValue().toString()));
            case GREATER_THAN_EQUAL_TO -> cb.greaterThanOrEqualTo(fileSizeKb, Long.parseLong(rule.getValue().toString()));
            case LESS_THAN -> cb.lessThan(fileSizeKb, Long.parseLong(rule.getValue().toString()));
            case LESS_THAN_EQUAL_TO -> cb.lessThanOrEqualTo(fileSizeKb, Long.parseLong(rule.getValue().toString()));
            case IN_BETWEEN -> {
                long start = Long.parseLong(rule.getValueStart().toString());
                long end = Long.parseLong(rule.getValueEnd().toString());
                yield cb.and(
                        cb.greaterThanOrEqualTo(fileSizeKb, start),
                        cb.lessThanOrEqualTo(fileSizeKb, end)
                );
            }
            case IS_EMPTY -> cb.isNull(fileSizeKb);
            case IS_NOT_EMPTY -> cb.isNotNull(fileSizeKb);
            default -> cb.conjunction();
        };
        
        subquery.select(cb.literal(1L))
                .where(cb.and(
                        cb.equal(fileRoot.get("book").get("id"), root.get("id")),
                        cb.isTrue(fileRoot.get("isBookFormat")),
                        valuePredicate
                ));
        
        return cb.exists(subquery);
    }

    /**
     * Subquery-based method for checking if a series is incomplete.
     * Checks if a book's series appears to be incomplete based on series number gaps.
     * Uses the approximation algorithm: (max - min + 1) != count
     * 
     * Performance: This query has a 5-second timeout with warning logging.
     *
     * @param cb CriteriaBuilder for creating predicates
     * @param root Root entity for BookEntity
     * @param seriesName Expression for the series name to check
     * @return Predicate that is true if series appears incomplete
     */
    private Predicate isSeriesIncompleteSubquery(CriteriaBuilder cb, Root<BookEntity> root, Expression<String> seriesName) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Subquery to count books in the series
            Subquery<Long> countSubquery = cb.createQuery().subquery(Long.class);
            Root<BookEntity> countRoot = countSubquery.from(BookEntity.class);
            countSubquery.select(cb.count(countRoot.get("id")))
                .where(
                    cb.and(
                        cb.equal(cb.lower(cb.trim(countRoot.get("metadata").get("seriesName"))), 
                                cb.lower(cb.trim(seriesName))),
                        cb.isNotNull(countRoot.get("metadata").get("seriesNumber"))
                    )
                );

            // Subquery to find minimum series number
            Subquery<Double> minSubquery = cb.createQuery().subquery(Double.class);
            Root<BookEntity> minRoot = minSubquery.from(BookEntity.class);
            Expression<Double> minSeriesNumber = minRoot.get("metadata").get("seriesNumber");
            minSubquery.select(cb.least(minSeriesNumber))
                .where(
                    cb.and(
                        cb.equal(cb.lower(cb.trim(minRoot.get("metadata").get("seriesName"))), 
                                cb.lower(cb.trim(seriesName))),
                        cb.isNotNull(minRoot.get("metadata").get("seriesNumber"))
                    )
                );

            // Subquery to find maximum series number
            Subquery<Double> maxSubquery = cb.createQuery().subquery(Double.class);
            Root<BookEntity> maxRoot = maxSubquery.from(BookEntity.class);
            Expression<Double> maxSeriesNumber = maxRoot.get("metadata").get("seriesNumber");
            maxSubquery.select(cb.greatest(maxSeriesNumber))
                .where(
                    cb.and(
                        cb.equal(cb.lower(cb.trim(maxRoot.get("metadata").get("seriesName"))), 
                                cb.lower(cb.trim(seriesName))),
                        cb.isNotNull(maxRoot.get("metadata").get("seriesNumber"))
                    )
                );

            // Algorithm: series is incomplete if (max - min + 1) != count
            // This works for fractional series numbers (1.0, 1.5, 2.0) as well
            Expression<Double> expectedCount = cb.sum(cb.diff(maxSubquery, minSubquery), cb.literal(1.0));
            Expression<Long> actualCount = countSubquery;
            
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > 5000) {
                log.warn("INCOMPLETE_SERIES query took {}ms (exceeds 5s timeout threshold)", elapsed);
                throw new RuntimeException("Incomplete series query timed out after " + elapsed + "ms");
            }
            
            if (elapsed > 1000) {
                log.info("INCOMPLETE_SERIES query took {}ms", elapsed);
            }

            return cb.notEqual(actualCount.as(Double.class), expectedCount);
            
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Error evaluating incomplete series predicate after {}ms: {}", elapsed, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Builds a predicate for the SERIES_STATUS field rule.
     * Series status can be:
     * - 'reading': Any book in the series has readStatus of READ or READING
     * - 'completed': seriesNumber equals seriesTotal
     * - 'ongoing': Has a series name (but not reading or completed)
     * - '' (empty string): Not in a series
     *
     * @param rule The rule containing the status value to match
     * @param cb CriteriaBuilder for creating predicates
     * @param root Root entity for BookEntity
     * @param progressJoin Join to UserBookProgressEntity
     * @return Predicate for series status check
     */
    private Predicate buildSeriesStatusPredicate(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        if (rule.getValue() == null) {
            log.warn("SERIES_STATUS rule missing status value");
            return cb.conjunction();
        }

        String statusValue = rule.getValue().toString().toLowerCase();
        Expression<String> seriesName = root.get("metadata").get("seriesName");
        Expression<Double> seriesNumber = root.get("metadata").get("seriesNumber");
        Expression<Double> seriesTotal = root.get("metadata").get("seriesTotal");

        Predicate result = switch (statusValue) {
            case "reading" -> {
                // Book must be in a series
                Predicate inSeries = cb.and(
                    cb.isNotNull(seriesName),
                    cb.notEqual(cb.trim(seriesName), "")
                );

                // Check if any book in this series is READ or READING
                Subquery<Long> readingSubquery = cb.createQuery().subquery(Long.class);
                Root<BookEntity> subRoot = readingSubquery.from(BookEntity.class);
                Join<BookEntity, UserBookProgressEntity> subProgressJoin = subRoot.join("userBookProgress", JoinType.LEFT);

                Predicate userMatch = cb.or(
                    cb.isNull(subProgressJoin.get("user").get("id")),
                    cb.equal(subProgressJoin.get("user").get("id"), progressJoin.get("user").get("id"))
                );

                Predicate sameSeriesNormalized = cb.equal(
                    cb.lower(cb.trim(subRoot.get("metadata").get("seriesName"))),
                    cb.lower(cb.trim(seriesName))
                );

                Predicate isReading = cb.or(
                    cb.equal(subProgressJoin.get("readStatus"), "READ"),
                    cb.equal(subProgressJoin.get("readStatus"), "READING")
                );

                readingSubquery.select(cb.literal(1L))
                    .where(cb.and(userMatch, sameSeriesNormalized, isReading));

                yield cb.and(inSeries, cb.exists(readingSubquery));
            }
            case "completed" -> {
                // seriesNumber must equal seriesTotal (both not null)
                yield cb.and(
                    cb.isNotNull(seriesNumber),
                    cb.isNotNull(seriesTotal),
                    cb.equal(seriesNumber, seriesTotal)
                );
            }
            case "ongoing" -> {
                // Has series name but not reading or completed
                Predicate inSeries = cb.and(
                    cb.isNotNull(seriesName),
                    cb.notEqual(cb.trim(seriesName), "")
                );

                // Not completed
                Predicate notCompleted = cb.or(
                    cb.isNull(seriesNumber),
                    cb.isNull(seriesTotal),
                    cb.notEqual(seriesNumber, seriesTotal)
                );

                // Not reading (no book in series is READ/READING)
                Subquery<Long> readingSubquery = cb.createQuery().subquery(Long.class);
                Root<BookEntity> subRoot = readingSubquery.from(BookEntity.class);
                Join<BookEntity, UserBookProgressEntity> subProgressJoin = subRoot.join("userBookProgress", JoinType.LEFT);

                Predicate userMatch = cb.or(
                    cb.isNull(subProgressJoin.get("user").get("id")),
                    cb.equal(subProgressJoin.get("user").get("id"), progressJoin.get("user").get("id"))
                );

                Predicate sameSeriesNormalized = cb.equal(
                    cb.lower(cb.trim(subRoot.get("metadata").get("seriesName"))),
                    cb.lower(cb.trim(seriesName))
                );

                Predicate isReading = cb.or(
                    cb.equal(subProgressJoin.get("readStatus"), "READ"),
                    cb.equal(subProgressJoin.get("readStatus"), "READING")
                );

                readingSubquery.select(cb.literal(1L))
                    .where(cb.and(userMatch, sameSeriesNormalized, isReading));

                Predicate notReading = cb.not(cb.exists(readingSubquery));

                yield cb.and(inSeries, notCompleted, notReading);
            }
            case "" -> {
                // Not in a series (empty or null series name)
                yield cb.or(
                    cb.isNull(seriesName),
                    cb.equal(cb.trim(seriesName), "")
                );
            }
            default -> {
                log.warn("Invalid SERIES_STATUS value: {}, expected 'reading', 'completed', 'ongoing', or empty string", statusValue);
                yield cb.conjunction();
            }
        };

        // Handle NOT_EQUALS operator
        if (rule.getOperator() == org.booklore.model.dto.RuleOperator.NOT_EQUALS) {
            return cb.not(result);
        }

        return result;
    }

    private Predicate buildEquals(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        List<String> ruleList = toStringList(rule.getValue());

        if (isArrayField(rule.getField())) {
            return buildArrayFieldPredicate(rule.getField(), ruleList, cb, root, false);
        }

        Expression<?> field = getFieldExpression(rule.getField(), cb, root, progressJoin);
        if (field == null) return cb.conjunction();

        Object value = normalizeValue(rule.getValue(), rule.getField());

        if (value instanceof LocalDateTime) {
            return cb.equal(field, value);
        } else if (rule.getField() == RuleField.READ_STATUS) {
            if ("UNSET".equals(value.toString())) {
                return cb.isNull(field);
            }
            return cb.equal(field, value.toString());
        } else if (value instanceof Number) {
            return cb.equal(field, value);
        }
        return cb.equal(cb.lower(field.as(String.class)), value.toString().toLowerCase());
    }

    private Predicate buildNotEquals(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return cb.not(buildEquals(rule, cb, root, progressJoin));
    }

    private Predicate buildContains(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        String ruleVal = rule.getValue().toString().toLowerCase();
        return buildStringPredicate(rule.getField(), root, progressJoin, cb,
            nameField -> cb.like(cb.lower(nameField), "%" + escapeLike(ruleVal) + "%"));
    }

    private Predicate buildStartsWith(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        String ruleVal = rule.getValue().toString().toLowerCase();
        return buildStringPredicate(rule.getField(), root, progressJoin, cb,
            nameField -> cb.like(cb.lower(nameField), escapeLike(ruleVal) + "%"));
    }

    private Predicate buildEndsWith(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        String ruleVal = rule.getValue().toString().toLowerCase();
        return buildStringPredicate(rule.getField(), root, progressJoin, cb,
            nameField -> cb.like(cb.lower(nameField), "%" + escapeLike(ruleVal)));
    }

    private Predicate buildStringPredicate(RuleField field, Root<BookEntity> root,
                                          Join<BookEntity, UserBookProgressEntity> progressJoin,
                                          CriteriaBuilder cb,
                                          java.util.function.Function<Expression<String>, Predicate> predicateBuilder) {
        if (isArrayField(field)) {
            Join<?, ?> arrayJoin = createArrayFieldJoin(field, root);
            Expression<String> nameField = getArrayFieldNameExpression(field, arrayJoin);
            return predicateBuilder.apply(nameField);
        }

        Expression<?> fieldExpr = getFieldExpression(field, cb, root, progressJoin);
        if (fieldExpr == null) return cb.conjunction();

        return predicateBuilder.apply(fieldExpr.as(String.class));
    }

    private Predicate buildGreaterThan(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return buildComparisonPredicate(rule, cb, root, progressJoin,
            (field, dateValue) -> cb.greaterThan(field.as(LocalDateTime.class), dateValue),
            (field, numValue) -> cb.gt((Expression<? extends Number>) field, numValue));
    }

    private Predicate buildGreaterThanEqual(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return buildComparisonPredicate(rule, cb, root, progressJoin,
            (field, dateValue) -> cb.greaterThanOrEqualTo(field.as(LocalDateTime.class), dateValue),
            (field, numValue) -> cb.ge((Expression<? extends Number>) field, numValue));
    }

    private Predicate buildLessThan(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return buildComparisonPredicate(rule, cb, root, progressJoin,
            (field, dateValue) -> cb.lessThan(field.as(LocalDateTime.class), dateValue),
            (field, numValue) -> cb.lt((Expression<? extends Number>) field, numValue));
    }

    private Predicate buildLessThanEqual(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return buildComparisonPredicate(rule, cb, root, progressJoin,
            (field, dateValue) -> cb.lessThanOrEqualTo(field.as(LocalDateTime.class), dateValue),
            (field, numValue) -> cb.le((Expression<? extends Number>) field, numValue));
    }

    private Predicate buildComparisonPredicate(Rule rule, CriteriaBuilder cb, Root<BookEntity> root,
                                              Join<BookEntity, UserBookProgressEntity> progressJoin,
                                              BiFunction<Expression<?>, LocalDateTime, Predicate> dateComparator,
                                              BiFunction<Expression<?>, Number, Predicate> numberComparator) {
        Expression<?> field = getFieldExpression(rule.getField(), cb, root, progressJoin);
        if (field == null) return cb.conjunction();

        Object value = normalizeValue(rule.getValue(), rule.getField());

        if (value instanceof LocalDateTime) {
            return dateComparator.apply(field, (LocalDateTime) value);
        }
        return numberComparator.apply(field, ((Number) value));
    }

    private Predicate buildInBetween(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        Expression<?> field = getFieldExpression(rule.getField(), cb, root, progressJoin);
        if (field == null) return cb.conjunction();

        Object start = normalizeValue(rule.getValueStart(), rule.getField());
        Object end = normalizeValue(rule.getValueEnd(), rule.getField());

        if (start == null || end == null) return cb.conjunction();

        if (start instanceof LocalDateTime && end instanceof LocalDateTime) {
            return cb.between(field.as(LocalDateTime.class), (LocalDateTime) start, (LocalDateTime) end);
        }

        if (!(start instanceof Number) || !(end instanceof Number)) {
            return cb.conjunction();
        }

        // Cast to Double expression for proper type inference in between()
        Expression<Double> doubleField = field.as(Double.class);
        return cb.between(doubleField, ((Number) start).doubleValue(), ((Number) end).doubleValue());
    }

    private Predicate buildIsEmpty(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        if (isArrayField(rule.getField())) {
            Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
            Root<BookEntity> subRoot = subquery.from(BookEntity.class);

            if (rule.getField() == RuleField.SHELF) {
                subRoot.join("shelves", JoinType.INNER);
            } else {
                Join<Object, Object> metadataJoin = subRoot.join("metadata", JoinType.INNER);
                joinArrayField(rule.getField(), metadataJoin);
            }

            subquery.select(cb.literal(1L)).where(cb.equal(subRoot.get("id"), root.get("id")));

            return cb.not(cb.exists(subquery));
        }

        Expression<?> field = getFieldExpression(rule.getField(), cb, root, progressJoin);
        if (field == null) return cb.conjunction();

        return cb.or(cb.isNull(field), cb.equal(cb.trim(field.as(String.class)), ""));
    }

    private Predicate buildIncludesAny(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        List<String> ruleList = toStringList(rule.getValue());

        if (isArrayField(rule.getField())) {
            return buildArrayFieldPredicate(rule.getField(), ruleList, cb, root, false);
        }

        return buildFieldInPredicate(rule.getField(), field -> field, ruleList, cb, progressJoin);
    }

    private Predicate buildExcludesAll(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        List<String> ruleList = toStringList(rule.getValue());

        if (isArrayField(rule.getField())) {
            return cb.not(buildArrayFieldPredicate(rule.getField(), ruleList, cb, root, false));
        }

        return cb.not(buildFieldInPredicate(rule.getField(), field -> field, ruleList, cb, progressJoin));
    }

    private Predicate buildIncludesAll(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        List<String> ruleList = toStringList(rule.getValue());

        if (isArrayField(rule.getField())) {
            return buildArrayFieldPredicate(rule.getField(), ruleList, cb, root, true);
        }

        return buildFieldInPredicate(rule.getField(), field -> field, ruleList, cb, progressJoin);
    }

    private Predicate buildFieldInPredicate(RuleField ruleField,
                                           java.util.function.Function<Expression<?>, Expression<?>> fieldTransformer,
                                           List<String> ruleList,
                                           CriteriaBuilder cb,
                                           Join<BookEntity, UserBookProgressEntity> progressJoin) {
        Expression<?> field = fieldTransformer.apply(getFieldExpression(ruleField, cb, null, progressJoin));
        if (field == null) return cb.conjunction();

        if (ruleField == RuleField.READ_STATUS) {
            boolean hasUnset = ruleList.stream().anyMatch("UNSET"::equals);
            List<String> nonUnsetValues = ruleList.stream()
                    .filter(v -> !"UNSET".equals(v))
                    .collect(Collectors.toList());

            if (hasUnset && !nonUnsetValues.isEmpty()) {
                return cb.or(
                    cb.isNull(field),
                    field.as(String.class).in(nonUnsetValues)
                );
            } else if (hasUnset) {
                return cb.isNull(field);
            } else {
                return field.as(String.class).in(nonUnsetValues);
            }
        }

        List<String> lowerList = ruleList.stream().map(String::toLowerCase).collect(Collectors.toList());
        return cb.lower(field.as(String.class)).in(lowerList);
    }

    private Expression<?> getFieldExpression(RuleField field, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return switch (field) {
            case LIBRARY -> root.get("library").get("id");
            case SHELF -> null; // Shelf is handled specially as a join field
            case READ_STATUS -> progressJoin.get("readStatus");
            case DATE_FINISHED -> progressJoin.get("dateFinished");
            case LAST_READ_TIME -> progressJoin.get("lastReadTime");
            case PERSONAL_RATING -> progressJoin.get("personalRating");
            case FILE_SIZE -> null; // Handled specially in buildRulePredicate
            case METADATA_SCORE -> root.get("metadataMatchScore");
            case TITLE -> root.get("metadata").get("title");
            case SUBTITLE -> root.get("metadata").get("subtitle");
            case PUBLISHER -> root.get("metadata").get("publisher");
            case PUBLISHED_DATE -> root.get("metadata").get("publishedDate");
            case PAGE_COUNT -> root.get("metadata").get("pageCount");
            case LANGUAGE -> root.get("metadata").get("language");
            case SERIES_NAME -> root.get("metadata").get("seriesName");
            case SERIES_NUMBER -> root.get("metadata").get("seriesNumber");
            case SERIES_TOTAL -> root.get("metadata").get("seriesTotal");
            case ISBN13 -> root.get("metadata").get("isbn13");
            case ISBN10 -> root.get("metadata").get("isbn10");
            case ASIN -> root.get("metadata").get("asin");
            case GOODREADS_ID -> root.get("metadata").get("goodreadsId");
            case COMICVINE_ID -> root.get("metadata").get("comicvineId");
            case HARDCOVER_ID -> root.get("metadata").get("hardcoverId");
            case HARDCOVER_BOOK_ID -> root.get("metadata").get("hardcoverBookId");
            case GOOGLE_ID -> root.get("metadata").get("googleId");
            case LUBIMYCZYTAC_ID -> root.get("metadata").get("lubimyczytacId");
            case RANOBEDB_ID -> root.get("metadata").get("ranobedbId");
            case AMAZON_RATING -> root.get("metadata").get("amazonRating");
            case AMAZON_REVIEW_COUNT -> root.get("metadata").get("amazonReviewCount");
            case GOODREADS_RATING -> root.get("metadata").get("goodreadsRating");
            case GOODREADS_REVIEW_COUNT -> root.get("metadata").get("goodreadsReviewCount");
            case HARDCOVER_RATING -> root.get("metadata").get("hardcoverRating");
            case HARDCOVER_REVIEW_COUNT -> root.get("metadata").get("hardcoverReviewCount");
            case RANOBEDB_RATING -> root.get("metadata").get("ranobedbRating");
            case LUBIMYCZYTAC_RATING -> root.get("metadata").get("lubimyczytacRating");
            case AGE_RATING -> root.get("metadata").get("ageRating");
            case CONTENT_RATING -> root.get("metadata").get("contentRating");
            case FILE_TYPE -> null; // Handled specially in buildRulePredicate
            case ADDED_ON -> cb.function("DATEDIFF", Integer.class,
                    cb.currentDate(),
                    cb.function("DATE", LocalDate.class, root.get("addedOn"))).as(Double.class);
            case METADATA, INCOMPLETE_SERIES, SERIES_STATUS -> null; // Handled specially in buildRulePredicate
            default -> null;
        };
    }

    private boolean isArrayField(RuleField field) {
        return field == RuleField.AUTHORS || field == RuleField.CATEGORIES ||
               field == RuleField.MOODS || field == RuleField.TAGS ||
               field == RuleField.GENRE || field == RuleField.SHELF;
    }

    private Join<?, ?> createArrayFieldJoin(RuleField field, Root<BookEntity> root) {
        if (field == RuleField.SHELF) {
            return root.join("shelves", JoinType.INNER);
        }
        Join<Object, Object> metadataJoin = root.join("metadata", JoinType.INNER);
        return joinArrayField(field, metadataJoin);
    }

    private Expression<String> getArrayFieldNameExpression(RuleField field, Join<?, ?> arrayJoin) {
        if (field == RuleField.SHELF) {
            return arrayJoin.get("id").as(String.class);
        }
        return arrayJoin.get("name");
    }

    private Join<?, ?> joinArrayField(RuleField field, Join<Object, Object> metadataJoin) {
        return switch (field) {
            case AUTHORS -> metadataJoin.join("authors", JoinType.INNER);
            case CATEGORIES -> metadataJoin.join("categories", JoinType.INNER);
            case MOODS -> metadataJoin.join("moods", JoinType.INNER);
            case TAGS -> metadataJoin.join("tags", JoinType.INNER);
            case GENRE -> metadataJoin.join("categories", JoinType.INNER);
            default -> throw new IllegalArgumentException("Not an array field: " + field);
        };
    }

    private Predicate buildArrayFieldPredicate(RuleField field, List<String> values, CriteriaBuilder cb, Root<BookEntity> root, boolean includesAll) {
        if (values.isEmpty()) {
            return cb.conjunction();
        }
        if (includesAll) {
            List<Predicate> predicates = values.stream()
                    .map(value -> {
                        Join<?, ?> arrayJoin = createArrayFieldJoin(field, root);
                        Expression<String> nameField = getArrayFieldNameExpression(field, arrayJoin);
                        return cb.equal(cb.lower(nameField), value.toLowerCase());
                    })
                    .toList();

            return cb.and(predicates.toArray(new Predicate[0]));
        } else {
            Join<?, ?> arrayJoin = createArrayFieldJoin(field, root);
            Expression<String> nameField = getArrayFieldNameExpression(field, arrayJoin);

            List<String> lowerValues = values.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());

            return cb.lower(nameField).in(lowerValues);
        }
    }

    private Object normalizeValue(Object value, RuleField field) {
        if (value == null) return null;

        if (field == RuleField.PUBLISHED_DATE) {
            return parseDate(value);
        }

        if (field == RuleField.DATE_FINISHED || field == RuleField.LAST_READ_TIME) {
            LocalDateTime parsed = parseDate(value);
            if (parsed != null) {
                return parsed.atZone(ZoneId.systemDefault()).toInstant();
            }
            return null;
        }

        if (field == RuleField.READ_STATUS) {
            return value.toString();
        }

        if (value instanceof Number) {
            return value;
        }

        return value.toString().toLowerCase();
    }

    private LocalDateTime parseDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime) return (LocalDateTime) value;

        try {
            return LocalDateTime.parse(value.toString(), DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDate.parse(value.toString()).atStartOfDay();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private List<String> toStringList(Object value) {
        if (value == null) return Collections.emptyList();
        if (value instanceof List) {
            return ((Collection<?>) value).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        return Collections.singletonList(value.toString());
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                   .replace("%", "\\%")
                   .replace("_", "\\_");
    }
}
