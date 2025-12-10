package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.model.enums.FetchedMetadataProposalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "metadata_fetch_proposals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataFetchProposalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proposal_id")
    private Long proposalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private MetadataFetchJobEntity job;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FetchedMetadataProposalStatus status;

    @Lob
    @Column(name = "metadata_json", columnDefinition = "JSON")
    private String metadataJson;
}