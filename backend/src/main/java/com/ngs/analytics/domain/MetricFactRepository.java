package com.ngs.analytics.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MetricFactRepository extends JpaRepository<MetricFact, UUID> {
    List<MetricFact> findByAnalysisId(UUID analysisId);
    List<MetricFact> findByAnalysisIdAndMetricKeyIn(UUID analysisId, Collection<String> metricKeys);
    @Query("""
            select fact from MetricFact fact
            join fetch fact.analysis analysis
            join fetch analysis.sample sample
            join fetch sample.project project
            join fetch analysis.fileAsset file
            where project.owner.id = :ownerId
              and fact.metricKey in :metricKeys
              and (:projectId is null or project.id = :projectId)
              and (:filterSamples = false or sample.id in :sampleIds)
              and (:filterStatuses = false or analysis.status in :statuses)
              and (:fromDate is null or analysis.createdAt >= :fromDate)
              and (:toDate is null or analysis.createdAt <= :toDate)
            order by analysis.createdAt desc
            """)
    List<MetricFact> queryOwned(
            @Param("ownerId") UUID ownerId,
            @Param("metricKeys") Collection<String> metricKeys,
            @Param("projectId") UUID projectId,
            @Param("filterSamples") boolean filterSamples,
            @Param("sampleIds") Collection<UUID> sampleIds,
            @Param("filterStatuses") boolean filterStatuses,
            @Param("statuses") Collection<AnalysisStatus> statuses,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable
    );
    @Query("""
            select count(fact) from MetricFact fact
            join fact.analysis analysis
            join analysis.sample sample
            join sample.project project
            where project.owner.id = :ownerId
              and fact.metricKey in :metricKeys
              and (:projectId is null or project.id = :projectId)
              and (:filterSamples = false or sample.id in :sampleIds)
              and (:filterStatuses = false or analysis.status in :statuses)
              and (:fromDate is null or analysis.createdAt >= :fromDate)
              and (:toDate is null or analysis.createdAt <= :toDate)
            """)
    long countOwned(
            @Param("ownerId") UUID ownerId,
            @Param("metricKeys") Collection<String> metricKeys,
            @Param("projectId") UUID projectId,
            @Param("filterSamples") boolean filterSamples,
            @Param("sampleIds") Collection<UUID> sampleIds,
            @Param("filterStatuses") boolean filterStatuses,
            @Param("statuses") Collection<AnalysisStatus> statuses,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate
    );
    boolean existsByAnalysisId(UUID analysisId);
    void deleteByAnalysisId(UUID analysisId);
}
