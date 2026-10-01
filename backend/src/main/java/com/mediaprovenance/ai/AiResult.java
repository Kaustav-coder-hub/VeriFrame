package com.mediaprovenance.ai;

import com.mediaprovenance.media.MediaVersion;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiResult {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_version_id", nullable = false)
    private MediaVersion mediaVersion;

    @Column(name = "provider", nullable = false, length = 100)
    private String provider;

    @Column(name = "tags_json", length = 16000)
    private String tagsJson;

    @Column(name = "raw_json", length = 16000)
    private String rawJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
