package com.ngs.analytics.projects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description
    ) {
    }

    public record ProjectResponse(
            String id,
            String name,
            String description,
            Instant createdAt
    ) {
    }

    public record CreateSampleRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String notes,
            String fastaReferenceName
    ) {
    }

    public record SampleResponse(
            String id,
            String projectId,
            String name,
            String notes,
            String fastaReferenceName,
            String fastaStoragePath,
            Instant createdAt
    ) {
    }

    public record FileAssetResponse(
            String id,
            String originalFilename,
            String fileType,
            long sizeBytes,
            Instant uploadedAt
    ) {
    }

    public record AnalysisResponse(
            String id,
            String sampleId,
            String fileAssetId,
            String mateFileAssetId,
            String status,
            String engine,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt
    ) {
    }
}
