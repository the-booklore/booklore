package com.adityachandel.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "book_custom_field_value",
        uniqueConstraints = {
                @UniqueConstraint(name = "unique_book_custom_field", columnNames = {"book_id", "custom_field_id"})
        }
)
public class BookCustomFieldValueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_field_id", nullable = false)
    private LibraryCustomFieldEntity customField;

    @Column(name = "value_string", columnDefinition = "TEXT")
    private String valueString;

    @Column(name = "value_number")
    private Double valueNumber;

    @Column(name = "value_date")
    private LocalDate valueDate;
}
