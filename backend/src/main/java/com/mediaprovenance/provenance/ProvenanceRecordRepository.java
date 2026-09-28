package com.mediaprovenance.provenance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProvenanceRecordRepository extends JpaRepository<ProvenanceRecord, UUID> {
    Optional<ProvenanceRecord> findByMediaVersionId(UUID mediaVersionId);
    List<ProvenanceRecord> findByChainStatus(String chainStatus);
    List<ProvenanceRecord> findByChainStatusIn(List<String> chainStatuses);
}
