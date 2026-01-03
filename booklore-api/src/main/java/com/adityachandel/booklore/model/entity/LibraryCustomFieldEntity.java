package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.model.enums.CustomFieldType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "library_custom_field",
        uniqueConstraints = {
                @UniqueConstraint(name = "unique_library_custom_field_name", columnNames = {"library_id", "name"})
        }
)
public class LibraryCustomFieldEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private LibraryEntity library;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false)
    private CustomFieldType fieldType;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;
}
