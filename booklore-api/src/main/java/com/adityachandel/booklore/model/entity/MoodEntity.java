package com.adityachandel.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mood")
public class MoodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @ManyToMany(mappedBy = "moods", fetch = FetchType.LAZY)
    private Set<BookMetadataEntity> bookMetadataEntityList = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MoodEntity that)) return false;
        return name != null && name.equalsIgnoreCase(that.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.toLowerCase().hashCode() : 0;
    }
}

