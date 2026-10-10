package com.ngs.analytics.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "qc_check_facts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_qc_check_analysis_key", columnNames = {"analysis_id", "check_key"})
})
public class QcCheckFact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Column(name = "check_key", nullable = false, length = 100)
    private String checkKey;

    @Column(nullable = false, length = 10)
    private String status;

    private Double numericValue;

    @Column(length = 1000)
    private String message;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public Analysis getAnalysis() { return analysis; }
    public void setAnalysis(Analysis analysis) { this.analysis = analysis; }
    public String getCheckKey() { return checkKey; }
    public void setCheckKey(String checkKey) { this.checkKey = checkKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getNumericValue() { return numericValue; }
    public void setNumericValue(Double numericValue) { this.numericValue = numericValue; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getCreatedAt() { return createdAt; }
}
