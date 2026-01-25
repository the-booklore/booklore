package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.convertor.FormatPriorityConverter;
import com.adityachandel.booklore.convertor.SortConverter;
import com.adityachandel.booklore.model.dto.Sort;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.IconType;
import com.adityachandel.booklore.model.enums.LibraryOrganizationMode;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "library")
public class LibraryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Convert(converter = SortConverter.class)
    private Sort sort;

    @OneToMany(mappedBy = "library", orphanRemoval = true)
    private List<BookEntity> bookEntities;

    @OneToMany(mappedBy = "library", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<LibraryPathEntity> libraryPaths;

    @ManyToMany(mappedBy = "libraries")
    private List<BookLoreUserEntity> users;

    private boolean watch;

    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_type", nullable = false)
    @Builder.Default
    private IconType iconType = IconType.PRIME_NG;

    @Column(name = "file_naming_pattern")
    private String fileNamingPattern;

    @Convert(converter = FormatPriorityConverter.class)
    @Column(name = "format_priority")
    @Builder.Default
    private List<BookFileType> formatPriority = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_mode")
    @Builder.Default
    private LibraryOrganizationMode organizationMode = LibraryOrganizationMode.AUTO_DETECT;

    @PrePersist
    public void ensureIconType() {
        if (this.iconType == null) {
            this.iconType = IconType.PRIME_NG;
        }
    }
}
