package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.GroupRule;
import com.adityachandel.booklore.model.dto.Rule;
import com.adityachandel.booklore.model.dto.RuleField;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

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

        return group.getJoin() == com.adityachandel.booklore.model.dto.JoinType.AND
                ? cb.and(predicates.toArray(new Predicate[0]))
                : cb.or(predicates.toArray(new Predicate[0]));
    }

    private Predicate buildRulePredicate(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        if (rule.getField() == null || rule.getOperator() == null) return null;

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
        };
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
            (field, numValue) -> cb.gt(field.as(Number.class), numValue));
    }

    private Predicate buildGreaterThanEqual(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return buildComparisonPredicate(rule, cb, root, progressJoin,
            (field, dateValue) -> cb.greaterThanOrEqualTo(field.as(LocalDateTime.class), dateValue),
            (field, numValue) -> cb.ge(field.as(Number.class), numValue));
    }

    private Predicate buildLessThan(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return buildComparisonPredicate(rule, cb, root, progressJoin,
            (field, dateValue) -> cb.lessThan(field.as(LocalDateTime.class), dateValue),
            (field, numValue) -> cb.lt(field.as(Number.class), numValue));
    }

    private Predicate buildLessThanEqual(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return buildComparisonPredicate(rule, cb, root, progressJoin,
            (field, dateValue) -> cb.lessThanOrEqualTo(field.as(LocalDateTime.class), dateValue),
            (field, numValue) -> cb.le(field.as(Number.class), numValue));
    }

    private Predicate buildComparisonPredicate(Rule rule, CriteriaBuilder cb, Root<BookEntity> root,
                                              Join<BookEntity, UserBookProgressEntity> progressJoin,
                                              BiFunction<Expression<?>, LocalDateTime, Predicate> dateComparator,
                                              BiFunction<Expression<?>, Double, Predicate> numberComparator) {
        Expression<?> field = getFieldExpression(rule.getField(), cb, root, progressJoin);
        if (field == null) return cb.conjunction();

        Object value = normalizeValue(rule.getValue(), rule.getField());

        if (value instanceof LocalDateTime) {
            return dateComparator.apply(field, (LocalDateTime) value);
        }
        return numberComparator.apply(field, ((Number) value).doubleValue());
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

        return cb.between(field.as(Double.class), ((Number) start).doubleValue(), ((Number) end).doubleValue());
    }

    private Predicate buildIsEmpty(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        if (isArrayField(rule.getField())) {
            Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
            Root<BookEntity> subRoot = subquery.from(BookEntity.class);

            Join<Object, Object> metadataJoin = subRoot.join("metadata", JoinType.INNER);
            joinArrayField(rule.getField(), metadataJoin);

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
            case READ_STATUS -> progressJoin.get("readStatus");
            case DATE_FINISHED -> progressJoin.get("dateFinished");
            case LAST_READ_TIME -> progressJoin.get("lastReadTime");
            case PERSONAL_RATING -> progressJoin.get("personalRating");
            case FILE_SIZE -> root.get("fileSizeKb");
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
            case AMAZON_RATING -> root.get("metadata").get("amazonRating");
            case AMAZON_REVIEW_COUNT -> root.get("metadata").get("amazonReviewCount");
            case GOODREADS_RATING -> root.get("metadata").get("goodreadsRating");
            case GOODREADS_REVIEW_COUNT -> root.get("metadata").get("goodreadsReviewCount");
            case HARDCOVER_RATING -> root.get("metadata").get("hardcoverRating");
            case HARDCOVER_REVIEW_COUNT -> root.get("metadata").get("hardcoverReviewCount");
            case FILE_TYPE -> cb.function("SUBSTRING_INDEX", String.class,
                    root.get("fileName"), cb.literal("."), cb.literal(-1));
            default -> null;
        };
    }

    private boolean isArrayField(RuleField field) {
        return field == RuleField.AUTHORS || field == RuleField.CATEGORIES ||
               field == RuleField.MOODS || field == RuleField.TAGS;
    }

    private Join<?, ?> createArrayFieldJoin(RuleField field, Root<BookEntity> root) {
        Join<Object, Object> metadataJoin = root.join("metadata", JoinType.INNER);
        return joinArrayField(field, metadataJoin);
    }

    private Expression<String> getArrayFieldNameExpression(RuleField field, Join<?, ?> arrayJoin) {
        return arrayJoin.get("name");
    }

    private Join<?, ?> joinArrayField(RuleField field, Join<Object, Object> metadataJoin) {
        return switch (field) {
            case AUTHORS -> metadataJoin.join("authors", JoinType.INNER);
            case CATEGORIES -> metadataJoin.join("categories", JoinType.INNER);
            case MOODS -> metadataJoin.join("moods", JoinType.INNER);
            case TAGS -> metadataJoin.join("tags", JoinType.INNER);
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
