package com.mediaprovenance.media;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaVersion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_asset_id", nullable = false)
    private MediaAsset mediaAsset;

    @Column(name = "version_type", nullable = false, length = 50)
    private String versionType; // ORIGINAL | TRANSFORMED

    @Column(name = "operation", nullable = false, length = 100)
    private String operation; // NONE | CROP | BG_REMOVAL | RESIZE

    @Column(name = "parent_version_id")
    private UUID parentVersionId;

    @Column(name = "sha256_hash", nullable = false, unique = true, length = 66)
    private String sha256Hash; // 0x + 64 hex chars

    @Column(name = "cloudinary_public_id", nullable = false)
    private String cloudinaryPublicId;

    @Column(name = "cloudinary_url", nullable = false, length = 2048)
    private String cloudinaryUrl;

    @Column(name = "optimized_url", nullable = false, length = 2048)
    private String optimizedUrl;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "bytes", nullable = false)
    private long bytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
