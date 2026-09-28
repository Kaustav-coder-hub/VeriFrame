package com.mediaprovenance.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaVersionRepository extends JpaRepository<MediaVersion, UUID> {
    Optional<MediaVersion> findBySha256Hash(String sha256Hash);
    List<MediaVersion> findByMediaAssetIdOrderByCreatedAtAsc(UUID mediaAssetId);
}
