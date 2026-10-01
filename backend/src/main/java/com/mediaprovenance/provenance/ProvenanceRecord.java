package com.mediaprovenance.provenance;

import com.mediaprovenance.media.MediaVersion;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provenance_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvenanceRecord {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_version_id", nullable = false, unique = true)
    private MediaVersion mediaVersion;

    @Column(name = "previous_hash", length = 66)
    private String previousHash;

    @Column(name = "operation", nullable = false, length = 100)
    private String operation;

    @Column(name = "cloudinary_asset_id", nullable = false)
    private String cloudinaryAssetId;

    @Column(name = "chain_status", nullable = false, length = 50)
    private String chainStatus; // PENDING | CONFIRMED | FAILED

    @Column(name = "tx_hash")
    private String txHash;

    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "explorer_url", length = 2048)
    private String explorerUrl;

    @Column(name = "error_message", length = 16000)
    private String errorMessage;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
