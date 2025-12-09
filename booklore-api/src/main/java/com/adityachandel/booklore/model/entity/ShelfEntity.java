package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.convertor.SortConverter;
import com.adityachandel.booklore.model.dto.Sort;
import com.adityachandel.booklore.model.enums.IconType;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_type", nullable = false)
    @Builder.Default
    private IconType iconType = IconType.PRIME_NG;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "book_shelf_mapping",
            joinColumns = {@JoinColumn(name = "shelf_id")},
            inverseJoinColumns = {@JoinColumn(name = "book_id")}
    )
    @Builder.Default
    private Set<BookEntity> bookEntities = new HashSet<>();
}