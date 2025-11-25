package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.convertor.SortConverter;
import com.adityachandel.booklore.model.dto.Sort;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "shelf")
public class ShelfEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @Column(name = "name", nullable = false)
    private String name;

    @Convert(converter = SortConverter.class)
    private Sort sort;

    private String icon;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "book_shelf_mapping",
            joinColumns = {@JoinColumn(name = "shelf_id")},
            inverseJoinColumns = {@JoinColumn(name = "book_id")}
    )
    @Builder.Default
    private Set<BookEntity> bookEntities = new HashSet<>();
}