package com.ngs.analytics.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QcCheckFactRepository extends JpaRepository<QcCheckFact, UUID> {
    List<QcCheckFact> findByAnalysisId(UUID analysisId);
    void deleteByAnalysisId(UUID analysisId);
}
