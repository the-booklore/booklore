package org.booklore.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "comic_metadata")
public class ComicMetadataEntity {

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", insertable = false, updatable = false)
    @JsonIgnore
    private BookMetadataEntity bookMetadata;

    @Column(name = "issue_number")
    private String issueNumber;

    @Column(name = "volume_name")
    private String volumeName;

    @Column(name = "volume_number")
    private Integer volumeNumber;

    @Column(name = "story_arc")
    private String storyArc;

    @Column(name = "story_arc_number")
    private Integer storyArcNumber;

    @Column(name = "alternate_series")
    private String alternateSeries;

    @Column(name = "alternate_issue")
    private String alternateIssue;

    @Column(name = "penciller")
    private String penciller;

    @Column(name = "inker")
    private String inker;

    @Column(name = "colorist")
    private String colorist;

    @Column(name = "letterer")
    private String letterer;

    @Column(name = "cover_artist")
    private String coverArtist;

    @Column(name = "editor")
    private String editor;

    @Column(name = "imprint")
    private String imprint;

    @Column(name = "format", length = 50)
    private String format;

    @Column(name = "black_and_white")
    @Builder.Default
    private Boolean blackAndWhite = Boolean.FALSE;

    @Column(name = "manga")
    @Builder.Default
    private Boolean manga = Boolean.FALSE;

    @Column(name = "reading_direction", length = 10)
    @Builder.Default
    private String readingDirection = "ltr";

    @Column(name = "characters", columnDefinition = "TEXT")
    private String characters;

    @Column(name = "teams", columnDefinition = "TEXT")
    private String teams;

    @Column(name = "locations", columnDefinition = "TEXT")
    private String locations;

    @Column(name = "web_link")
    private String webLink;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Locked fields
    @Column(name = "issue_number_locked")
    @Builder.Default
    private Boolean issueNumberLocked = Boolean.FALSE;

    @Column(name = "volume_name_locked")
    @Builder.Default
    private Boolean volumeNameLocked = Boolean.FALSE;

    @Column(name = "volume_number_locked")
    @Builder.Default
    private Boolean volumeNumberLocked = Boolean.FALSE;

    @Column(name = "story_arc_locked")
    @Builder.Default
    private Boolean storyArcLocked = Boolean.FALSE;

    @Column(name = "penciller_locked")
    @Builder.Default
    private Boolean pencillerLocked = Boolean.FALSE;

    @Column(name = "inker_locked")
    @Builder.Default
    private Boolean inkerLocked = Boolean.FALSE;

    @Column(name = "colorist_locked")
    @Builder.Default
    private Boolean coloristLocked = Boolean.FALSE;

    @Column(name = "letterer_locked")
    @Builder.Default
    private Boolean lettererLocked = Boolean.FALSE;

    @Column(name = "cover_artist_locked")
    @Builder.Default
    private Boolean coverArtistLocked = Boolean.FALSE;

    @Column(name = "editor_locked")
    @Builder.Default
    private Boolean editorLocked = Boolean.FALSE;

    @Column(name = "characters_locked")
    @Builder.Default
    private Boolean charactersLocked = Boolean.FALSE;

    @Column(name = "teams_locked")
    @Builder.Default
    private Boolean teamsLocked = Boolean.FALSE;

    @Column(name = "locations_locked")
    @Builder.Default
    private Boolean locationsLocked = Boolean.FALSE;

    public void applyLockToAllFields(boolean lock) {
        this.issueNumberLocked = lock;
        this.volumeNameLocked = lock;
        this.volumeNumberLocked = lock;
        this.storyArcLocked = lock;
        this.pencillerLocked = lock;
        this.inkerLocked = lock;
        this.coloristLocked = lock;
        this.lettererLocked = lock;
        this.coverArtistLocked = lock;
        this.editorLocked = lock;
        this.charactersLocked = lock;
        this.teamsLocked = lock;
        this.locationsLocked = lock;
    }

    public boolean areAllFieldsLocked() {
        return Boolean.TRUE.equals(this.issueNumberLocked)
                && Boolean.TRUE.equals(this.volumeNameLocked)
                && Boolean.TRUE.equals(this.volumeNumberLocked)
                && Boolean.TRUE.equals(this.storyArcLocked)
                && Boolean.TRUE.equals(this.pencillerLocked)
                && Boolean.TRUE.equals(this.inkerLocked)
                && Boolean.TRUE.equals(this.coloristLocked)
                && Boolean.TRUE.equals(this.lettererLocked)
                && Boolean.TRUE.equals(this.coverArtistLocked)
                && Boolean.TRUE.equals(this.editorLocked)
                && Boolean.TRUE.equals(this.charactersLocked)
                && Boolean.TRUE.equals(this.teamsLocked)
                && Boolean.TRUE.equals(this.locationsLocked);
    }
}
