package com.adityachandel.booklore.service.opds;

import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.CategoryEntity;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.ReadStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.*;
import java.util.stream.Collectors;

public class MagicShelfSpecificationBuilder {

    public static Specification<BookEntity> build(String filterJson, Set<Long> allowedLibraryIds, Long userId) {
        Specification<BookEntity> base = (root, query, cb) -> {
            query.distinct(true);
            return cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted")));
        };

        Specification<BookEntity> access = (root, query, cb) -> {
            if (allowedLibraryIds == null || allowedLibraryIds.isEmpty()) return cb.conjunction();
            CriteriaBuilder.In<Long> in = cb.in(root.get("library").get("id"));
            allowedLibraryIds.forEach(in::value);
            return in;
        };

        Specification<BookEntity> rules = parseRules(filterJson, userId);
        return base.and(access).and(rules);
    }

    @SuppressWarnings("unchecked")
    private static Specification<BookEntity> parseRules(String filterJson, Long userId) {
        if (filterJson == null || filterJson.isBlank()) return (root, query, cb) -> cb.conjunction();
        try {
            Map<String, Object> group = new ObjectMapper().readValue(filterJson, Map.class);
            return groupSpec(group, userId);
        } catch (Exception e) {
            return (root, query, cb) -> cb.conjunction();
        }
    }

    @SuppressWarnings("unchecked")
    private static Specification<BookEntity> groupSpec(Map<String, Object> group, Long userId) {
        String join = Objects.toString(group.get("join"), "and");
        List<Object> rules = (List<Object>) group.getOrDefault("rules", List.of());
        List<Specification<BookEntity>> specs = new ArrayList<>();
        for (Object r : rules) {
            if (r instanceof Map<?,?> m) {
                String type = Objects.toString(m.get("type"), "rule");
                if ("group".equals(type)) specs.add(groupSpec((Map<String, Object>) m, userId));
                else specs.add(ruleSpec((Map<String, Object>) m, userId));
            }
        }
        Specification<BookEntity> combined = (root, query, cb) -> cb.conjunction();
        for (Specification<BookEntity> s : specs) {
            combined = ("and".equalsIgnoreCase(join)) ? combined.and(s) : combined.or(s);
        }
        return combined;
    }

    private static Specification<BookEntity> ruleSpec(Map<String, Object> rule, Long userId) {
        String field = Objects.toString(rule.get("field"), "");
        String operator = Objects.toString(rule.get("operator"), "contains");
        Object value = rule.get("value");
        Object valueStart = rule.get("valueStart");
        Object valueEnd = rule.get("valueEnd");

        return switch (field) {
            case "library" -> (root, query, cb) -> cb.equal(root.get("library").get("id"), toLong(value));
            case "fileType" -> (root, query, cb) -> cb.equal(root.get("bookType"), mapFileType(value));
            case "title" -> likeOp(pathStr("title"), operator, value);
            case "subtitle" -> likeOp(pathStr("subtitle"), operator, value);
            case "seriesName" -> likeOp(pathStr("seriesName"), operator, value);
            case "publisher" -> likeOp(pathStr("publisher"), operator, value);
            case "language" -> likeOp(pathStr("language"), operator, value);
            case "authors" -> arrayOp(true, operator, value);
            case "categories" -> arrayOp(false, operator, value);
            case "pageCount" -> numberOp(pathInt("pageCount"), operator, value, valueStart, valueEnd);
            case "readStatus" -> readStatusOp(userId, operator, value);
            default -> (root, query, cb) -> cb.conjunction();
        };
    }

    private static Specification<BookEntity> likeOp(java.util.function.Function<Root<BookEntity>, Expression<String>> pathFn,
                                                    String operator, Object value) {
        if (value == null) return (root, query, cb) -> cb.conjunction();
        String term = value.toString().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> {
            Expression<String> exp = cb.lower(pathFn.apply(root));
            return switch (operator) {
                case "equals" -> cb.equal(exp, term);
                case "starts_with" -> cb.like(exp, term + "%");
                case "ends_with" -> cb.like(exp, "%" + term);
                case "does_not_contain" -> cb.notLike(exp, "%" + term + "%");
                default -> cb.like(exp, "%" + term + "%");
            };
        };
    }

    private static Specification<BookEntity> numberOp(java.util.function.Function<Root<BookEntity>, Expression<Integer>> pathFn,
                                                      String operator, Object value, Object start, Object end) {
        return (root, query, cb) -> {
            Expression<Integer> exp = pathFn.apply(root);
            return switch (operator) {
                case "equals" -> cb.equal(exp, toInt(value));
                case "greater_than" -> cb.gt(exp, toInt(value));
                case "greater_than_equal_to" -> cb.ge(exp, toInt(value));
                case "less_than" -> cb.lt(exp, toInt(value));
                case "less_than_equal_to" -> cb.le(exp, toInt(value));
                case "in_between" -> cb.between(exp, toInt(start), toInt(end));
                default -> cb.conjunction();
            };
        };
    }

    private static Specification<BookEntity> arrayOp(boolean authors, String operator, Object value) {
        List<String> vals = toStringList(value).stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toList());
        if (vals.isEmpty()) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> {
            Join<BookEntity, BookMetadataEntity> meta = root.join("metadata", JoinType.LEFT);
            Expression<String> name;
            if (authors) name = cb.lower(meta.join("authors", JoinType.LEFT).get("name"));
            else name = cb.lower(meta.join("categories", JoinType.LEFT).get("name"));
            List<Predicate> preds = vals.stream().map(v -> cb.equal(name, v)).collect(Collectors.toList());
            return switch (operator) {
                case "includes_all" -> preds.stream().reduce(cb::and).orElse(cb.conjunction());
                case "excludes_all" -> cb.not(preds.stream().reduce(cb::or).orElse(cb.disjunction()));
                default -> preds.stream().reduce(cb::or).orElse(cb.disjunction());
            };
        };
    }

    private static Specification<BookEntity> readStatusOp(Long userId, String operator, Object value) {
        if (userId == null || value == null) return (root, query, cb) -> cb.conjunction();
        List<ReadStatus> statuses = toStringList(value).stream()
                .map(MagicShelfSpecificationBuilder::toStatus)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (statuses.isEmpty()) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<UserBookProgressEntity> p = sq.from(UserBookProgressEntity.class);
            sq.select(cb.literal(1L));
            Predicate byUser = cb.equal(p.get("user").get("id"), userId);
            Predicate byBook = cb.equal(p.get("book").get("id"), root.get("id"));
            Predicate byStatus = p.get("readStatus").in(statuses);
            sq.where(cb.and(byUser, byBook, byStatus));
            Predicate exists = cb.exists(sq);
            return switch (operator) {
                case "excludes_all", "not_equals" -> cb.not(exists);
                default -> exists;
            };
        };
    }

    private static Long toLong(Object v) { try { return v == null ? null : Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; } }
    private static Integer toInt(Object v) { try { return v == null ? null : Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; } }

    private static BookFileType mapFileType(Object v) {
        if (v == null) return null;
        return switch (String.valueOf(v).toLowerCase(Locale.ROOT)) {
            case "pdf" -> BookFileType.PDF;
            case "epub" -> BookFileType.EPUB;
            case "cbr", "cbz", "cb7", "cbx" -> BookFileType.CBX;
            default -> null;
        };
    }

    private static java.util.function.Function<Root<BookEntity>, Expression<String>> pathStr(String metaField) {
        return root -> root.join("metadata", JoinType.LEFT).get(metaField);
    }
    private static java.util.function.Function<Root<BookEntity>, Expression<Integer>> pathInt(String metaField) {
        return root -> root.join("metadata", JoinType.LEFT).get(metaField);
    }

    private static List<String> toStringList(Object value) {
        if (value == null) return Collections.emptyList();
        if (value instanceof Collection<?> col) return col.stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.toList());
        if (value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            List<String> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                Object elem = java.lang.reflect.Array.get(value, i);
                if (elem != null) out.add(elem.toString());
            }
            return out;
        }
        return List.of(value.toString());
    }

    private static ReadStatus toStatus(String v) {
        if (v == null) return null;
        String norm = v.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        try { return ReadStatus.valueOf(norm); } catch (Exception e) { return null; }
    }
}

