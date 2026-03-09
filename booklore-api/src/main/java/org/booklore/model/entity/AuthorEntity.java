package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.Objects;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "author")
public class AuthorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "asin", length = 20)
    private String asin;

    @Column(name = "name_locked", nullable = false)
    private boolean nameLocked;

    @Column(name = "description_locked", nullable = false)
    private boolean descriptionLocked;

    @Column(name = "asin_locked", nullable = false)
    private boolean asinLocked;

    @Column(name = "photo_locked", nullable = false)
    private boolean photoLocked;

    @ManyToMany(mappedBy = "authors", fetch = FetchType.LAZY)
    private List<BookMetadataEntity> bookMetadataEntityList;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthorEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
