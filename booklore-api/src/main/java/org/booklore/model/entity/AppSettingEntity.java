package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "app_settings")
@Data
public class AppSettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "val", nullable = false, columnDefinition = "TEXT")
    private String val;
}
