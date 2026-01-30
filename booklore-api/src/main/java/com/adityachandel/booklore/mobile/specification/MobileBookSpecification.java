package com.adityachandel.booklore.mobile.specification;

import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.ReadStatus;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MobileBookSpecification {

    private MobileBookSpecification() {
    }

    public static Specification<BookEntity> inLibraries(Collection<Long> libraryIds) {
        return (root, query, cb) -> {
            if (libraryIds == null || libraryIds.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("library").get("id").in(libraryIds);
        };
    }

    public static Specification<BookEntity> inLibrary(Long libraryId) {
        return (root, query, cb) -> {
            if (libraryId == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("library").get("id"), libraryId);
        };
    }

    public static Specification<BookEntity> inShelf(Long shelfId) {
        return (root, query, cb) -> {
            if (shelfId == null) {
                return cb.conjunction();
            }
            Join<BookEntity, ShelfEntity> shelvesJoin = root.join("shelves", JoinType.INNER);
            return cb.equal(shelvesJoin.get("id"), shelfId);
        };
    }

    public static Specification<BookEntity> withReadStatus(ReadStatus status, Long userId) {
        return (root, query, cb) -> {
            if (status == null || userId == null) {
                return cb.conjunction();
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<UserBookProgressEntity> progressRoot = subquery.from(UserBookProgressEntity.class);
            subquery.select(progressRoot.get("book").get("id"))
                    .where(
                            cb.equal(progressRoot.get("user").get("id"), userId),
                            cb.equal(progressRoot.get("readStatus"), status)
                    );
            return root.get("id").in(subquery);
        };
    }

    public static Specification<BookEntity> inProgress(Long userId) {
        return (root, query, cb) -> {
            if (userId == null) {
                return cb.conjunction();
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<UserBookProgressEntity> progressRoot = subquery.from(UserBookProgressEntity.class);
            subquery.select(progressRoot.get("book").get("id"))
                    .where(
                            cb.equal(progressRoot.get("user").get("id"), userId),
                            progressRoot.get("readStatus").in(ReadStatus.READING, ReadStatus.RE_READING)
                    );
            return root.get("id").in(subquery);
        };
    }

    public static Specification<BookEntity> addedWithinDays(int days) {
        return (root, query, cb) -> {
            Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
            return cb.greaterThanOrEqualTo(root.get("addedOn"), cutoff);
        };
    }

    public static Specification<BookEntity> searchText(String searchQuery) {
        return (root, query, cb) -> {
            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                return cb.conjunction();
            }
            String pattern = "%" + searchQuery.toLowerCase().trim() + "%";

            Join<BookEntity, BookMetadataEntity> metadataJoin = root.join("metadata", JoinType.LEFT);
            Join<BookMetadataEntity, AuthorEntity> authorsJoin = metadataJoin.join("authors", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(metadataJoin.get("title")), pattern));
            predicates.add(cb.like(cb.lower(metadataJoin.get("seriesName")), pattern));
            predicates.add(cb.like(cb.lower(authorsJoin.get("name")), pattern));

            query.distinct(true);

            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<BookEntity> notDeleted() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("deleted")),
                cb.equal(root.get("deleted"), false)
        );
    }

    public static Specification<BookEntity> hasDigitalFile() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("isPhysical")),
                cb.equal(root.get("isPhysical"), false)
        );
    }

    @SafeVarargs
    public static Specification<BookEntity> combine(Specification<BookEntity>... specs) {
        Specification<BookEntity> result = (root, query, cb) -> cb.conjunction();
        for (Specification<BookEntity> spec : specs) {
            if (spec != null) {
                result = result.and(spec);
            }
        }
        return result;
    }
}
