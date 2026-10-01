package com.mediaprovenance.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiResultRepository extends JpaRepository<AiResult, UUID> {
    Optional<AiResult> findByMediaVersionId(UUID mediaVersionId);
}
