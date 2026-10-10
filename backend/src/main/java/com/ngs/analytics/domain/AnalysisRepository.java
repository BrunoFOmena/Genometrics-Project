package com.ngs.analytics.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {
    List<Analysis> findBySampleIdOrderByCreatedAtDesc(UUID sampleId);
    List<Analysis> findBySampleProjectOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    List<Analysis> findByStatus(AnalysisStatus status);
    Optional<Analysis> findTopBySampleIdAndFileAssetFileTypeInAndStatusOrderByFinishedAtDesc(
            UUID sampleId, List<FileType> types, AnalysisStatus status);
}
