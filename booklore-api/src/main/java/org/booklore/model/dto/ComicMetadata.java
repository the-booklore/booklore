package org.booklore.model.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComicMetadata {
    private String issueNumber;
    private String volumeName;
    private Integer volumeNumber;
    private String storyArc;
    private Integer storyArcNumber;
    private String alternateSeries;
    private String alternateIssue;
    private String penciller;
    private String inker;
    private String colorist;
    private String letterer;
    private String coverArtist;
    private String editor;
    private String imprint;
    private String format;
    private Boolean blackAndWhite;
    private Boolean manga;
    private String readingDirection;
    private String characters;
    private String teams;
    private String locations;
    private String webLink;
    private String notes;

    private Boolean issueNumberLocked;
    private Boolean volumeNameLocked;
    private Boolean volumeNumberLocked;
    private Boolean storyArcLocked;
    private Boolean pencillerLocked;
    private Boolean inkerLocked;
    private Boolean coloristLocked;
    private Boolean lettererLocked;
    private Boolean coverArtistLocked;
    private Boolean editorLocked;
    private Boolean charactersLocked;
    private Boolean teamsLocked;
    private Boolean locationsLocked;
}
